import XCTest
final class KeychainUITests: XCTestCase {
    func testDeviceLinkPersistsAndLoadsProfiles() {
        let app = XCUIApplication()
        app.launchArguments = ["--device-link-fixture", "--reset-device-link-fixture"]
        app.launch()
        XCTAssertTrue(app.buttons["profile.pick.adult"].waitForExistence(timeout: 15), app.debugDescription)
        app.terminate()
        app.launchArguments = ["--device-link-fixture"]
        app.launch()
        XCTAssertTrue(app.buttons["profile.pick.adult"].waitForExistence(timeout: 10), app.debugDescription)
        XCTAssertFalse(app.staticTexts["Sign in with your phone"].exists)
    }
    func testInstalledAppSecureStorage() {
        let app = XCUIApplication()
        app.launchArguments = ["--keychain-smoke"]
        app.launch()
        let result = app.staticTexts["keychain.result"]
        XCTAssertTrue(result.waitForExistence(timeout: 10))
        let finished = XCTNSPredicateExpectation(predicate: NSPredicate(format: "label BEGINSWITH 'PASS:' OR label BEGINSWITH 'FAIL:'"), object: result)
        XCTAssertEqual(XCTWaiter.wait(for: [finished], timeout: 10), .completed)
        XCTAssertTrue(result.label.hasPrefix("PASS:"), result.label)
    }
}
