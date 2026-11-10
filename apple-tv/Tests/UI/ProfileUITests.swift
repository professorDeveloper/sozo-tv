import XCTest

final class ProfileUITests: XCTestCase {
    let app = XCUIApplication()
    let remote = XCUIRemote.shared
    override func setUp() {
        continueAfterFailure = false
        app.launchArguments = ["--profile-ui-fixture"]
        app.launch()
        XCTAssertTrue(app.buttons["profile.pick.adult"].waitForExistence(timeout: 10), app.debugDescription)
    }
    func focus(_ identifier: String, direction: XCUIRemote.Button = .right, limit: Int = 12) {
        let item = app.buttons[identifier]
        if !item.exists { _ = item.waitForExistence(timeout: 2) }
        for _ in 0..<limit {
            if !item.exists { remote.press(direction); continue }
            if item.hasFocus || app.cells.containing(.button, identifier: identifier).matching(NSPredicate(format: "hasFocus == true")).firstMatch.exists { Thread.sleep(forTimeInterval: 0.2); return }
            let current = app.descendants(matching: .any).matching(NSPredicate(format: "hasFocus == true")).firstMatch
            if current.exists {
                let from = current.frame, to = item.frame
                if to.maxY < from.minY { remote.press(.up) }
                else if to.minY > from.maxY { remote.press(.down) }
                else if to.minX > from.maxX { remote.press(.right) }
                else if to.maxX < from.minX { remote.press(.left) }
                else { remote.press(to.midX < from.midX ? .left : .right) }
            } else { remote.press(direction) }
            Thread.sleep(forTimeInterval: 0.2)
        }
        XCTAssertTrue(item.hasFocus, "Could not focus \(identifier)\n\(app.debugDescription)")
    }
    func select(_ identifier: String, direction: XCUIRemote.Button = .right, limit: Int = 12) {
        focus(identifier, direction: direction, limit: limit)
        remote.press(.select)
    }
    func testAdultHubAndLibrary() {
        select("profile.pick.adult")
        select("home.profile")
        XCTAssertTrue(app.buttons["profile.manage"].waitForExistence(timeout: 10))
        select("profile.route.library", direction: .down)
        XCTAssertTrue(app.staticTexts["This collection is empty"].waitForExistence(timeout: 5) || app.segmentedControls.count > 0, app.debugDescription)
        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.name = "Apple TV profile library"; screenshot.lifetime = .keepAlways
        add(screenshot)
    }
    func testProfileDestinationsLoad() {
        select("profile.pick.adult")
        select("home.profile")
        for (route, title) in [("history", "Watch history"), ("activity", "Activity"), ("account", "My account"), ("notifications", "Notifications"), ("friends", "Friends"), ("connections", "Connections"), ("sources", "Sources"), ("support", "Help & support"), ("about", "About")] {
            select("profile.route.\(route)", direction: .down, limit: 24)
            XCTAssertTrue(app.navigationBars[title].waitForExistence(timeout: 5), app.debugDescription)
            XCTAssertFalse(app.buttons["Retry"].exists, app.debugDescription)
            remote.press(.menu)
        }
    }
    func testSettingsAndProfileSwitch() {
        select("profile.pick.adult")
        select("home.profile")
        focus("profile.route.library", direction: .down)
        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.name = "Apple TV Liquid profile hub"; screenshot.lifetime = .keepAlways; add(screenshot)
        select("profile.settings", direction: .down, limit: 24)
        XCTAssertTrue(app.switches["settings.liquidGlass"].waitForExistence(timeout: 5) || app.buttons["settings.liquidGlass"].exists, app.debugDescription)
        remote.press(.menu)
        select("profile.manage", direction: .up, limit: 24)
        select("profile.switch", direction: .down)
        select("profile.pick.kids")
        XCTAssertTrue(app.buttons["home.profile"].waitForExistence(timeout: 10))
        select("home.profile")
        XCTAssertTrue(app.staticTexts["Kids profile"].waitForExistence(timeout: 5))
    }
    func testKidsCannotManageProfiles() {
        select("profile.pick.kids")
        select("home.profile")
        XCTAssertTrue(app.buttons["profile.manage"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.buttons["profile.route.friends"].exists)
        select("profile.manage", direction: .up)
        XCTAssertTrue(app.staticTexts["Switch to an adult profile to manage profiles."].waitForExistence(timeout: 5))
        XCTAssertFalse(app.buttons["New profile"].exists)
    }
    func assertFocused(_ identifier: String) {
        let item = app.buttons[identifier]
        XCTAssertTrue(item.exists || item.waitForExistence(timeout: 5))
        if item.hasFocus {
            Thread.sleep(forTimeInterval: 0.2) // Let the focus animation settle before the next remote event.
            return
        }
        let ready = XCTNSPredicateExpectation(predicate: NSPredicate(format: "hasFocus == true"), object: item)
        XCTAssertEqual(XCTWaiter.wait(for: [ready], timeout: 3), .completed, "\(identifier)\n\(app.debugDescription)")
        Thread.sleep(forTimeInterval: 0.2)
    }
    func testPINRemoteFocusAndBack() {
        select("profile.pick.locked")
        assertFocused("profile.pin.digit.1")
        remote.press(.left); assertFocused("profile.pin.digit.1")
        remote.press(.right); assertFocused("profile.pin.digit.2")
        remote.press(.down); assertFocused("profile.pin.digit.5")
        remote.press(.left); assertFocused("profile.pin.digit.4")
        remote.press(.down); assertFocused("profile.pin.digit.7")
        remote.press(.down); assertFocused("profile.pin.delete")
        remote.press(.right); assertFocused("profile.pin.digit.0")
        remote.press(.select)
        remote.press(.left); assertFocused("profile.pin.delete")
        remote.press(.select)
        XCTAssertEqual(app.staticTexts["profile.pin.count"].label, "0 digits entered")
        assertFocused("profile.pin.delete")
        remote.press(.right); assertFocused("profile.pin.digit.0")
        remote.press(.down); assertFocused("profile.pin.submit")
        remote.press(.select)
        XCTAssertFalse(app.buttons["home.profile"].exists)
        // Submit is aligned with zero so vertical navigation has one clear target.
        remote.press(.up); assertFocused("profile.pin.digit.0")
        remote.press(.right); assertFocused("profile.pin.cancel")
        let picture = XCTAttachment(screenshot: app.screenshot())
        picture.name = "PIN remote keypad"; picture.lifetime = .keepAlways; add(picture)
        remote.press(.menu)
        assertFocused("profile.pick.locked")
    }
    func testLockedProfileRequiresPIN() {
        select("profile.pick.locked")
        XCTAssertTrue(app.staticTexts["Enter your PIN to unlock this profile"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.buttons["home.profile"].exists)
        for _ in 0..<4 { select("profile.pin.digit.1") }
        focus("profile.pin.digit.0")
        // Zero is the documented vertical route to the centered confirmation button.
        remote.press(.down); assertFocused("profile.pin.submit"); remote.press(.select)
        XCTAssertTrue(app.staticTexts["Incorrect PIN. Please try again."].waitForExistence(timeout: 5))
        assertFocused("profile.pin.digit.1")
        XCTAssertFalse(app.buttons["home.profile"].exists)
        for digit in [1, 2, 3, 4] { select("profile.pin.digit.\(digit)") }
        remote.press(.playPause)
        XCTAssertTrue(app.buttons["home.profile"].waitForExistence(timeout: 10))
    }
}
