import XCTest

final class BrowseUITests: XCTestCase {
    let app = XCUIApplication()
    let remote = XCUIRemote.shared
    override func setUp() {
        continueAfterFailure = false
        app.launchArguments = ["--browse-ui-fixture", "--search-query=Fixture"]
        app.launch()
        XCTAssertTrue(app.buttons["profile.pick.adult"].waitForExistence(timeout: 10))
        select("profile.pick.adult")
        XCTAssertTrue(app.buttons["home.featured"].waitForExistence(timeout: 10), app.debugDescription)
    }
    private func select(_ id: String, limit: Int = 40, control: XCUIElement? = nil, activate: Bool = true) {
        let target = control ?? app.buttons[id]
        if !target.exists { _ = target.waitForExistence(timeout: 2) }
        for _ in 0..<limit {
            if !target.exists { remote.press(.down); Thread.sleep(forTimeInterval: 0.2); continue }
            if target.hasFocus || target.descendants(matching: .any).matching(NSPredicate(format: "hasFocus == true")).firstMatch.exists || app.cells.containing(.any, identifier: id).matching(NSPredicate(format: "hasFocus == true")).firstMatch.exists { Thread.sleep(forTimeInterval: 0.2); if activate { remote.press(.select) }; return }
            let current = app.descendants(matching: .any).matching(NSPredicate(format: "hasFocus == true")).firstMatch
            if current.exists {
                let from = current.frame, to = target.frame
                print("select(\(id)): focus \(current.elementType.rawValue) '\(current.identifier)' '\(current.label)' \(from), target \(to)")
                if to.minY > from.maxY { remote.press(.down) }
                else if to.maxY < from.minY { remote.press(.up) }
                else { remote.press(to.midX < from.midX ? .left : .right) }
            } else { remote.press(.down) }
            Thread.sleep(forTimeInterval: 0.2)
        }
        XCTFail("Could not focus \(id)\n\(app.debugDescription)")
    }
    private func screenshot(_ name: String) {
        let shot = XCTAttachment(screenshot: app.screenshot()); shot.name = name; shot.lifetime = .keepAlways; add(shot)
    }
    func testHomeHasContinueWatchingAndSourceControls() {
        XCTAssertTrue(app.buttons["home.source"].exists)
        XCTAssertTrue(app.buttons["home.search"].exists)
        XCTAssertTrue(app.buttons["home.resume.fixture|https://fixture.invalid/title/1|2"].exists)
        screenshot("TV Home and glass navigation")
    }
    func testSearchPaginatesAndDeduplicates() {
        select("home.search")
        XCTAssertTrue(app.staticTexts["search.count"].waitForExistence(timeout: 10))
        XCTAssertEqual(app.staticTexts["search.count"].label, "2 titles")
        select("search.more")
        let count = app.staticTexts["search.count"]
        let ready = XCTNSPredicateExpectation(predicate: NSPredicate(format: "label == '3 titles'"), object: count)
        XCTAssertEqual(XCTWaiter.wait(for: [ready], timeout: 8), .completed)
        XCTAssertFalse(app.buttons["search.more"].exists)
        screenshot("TV Search results")
        remote.press(.menu)
        XCTAssertTrue(app.buttons["home.profile"].waitForExistence(timeout: 5))
    }
    func testEpisodesAndOnlyPlayableSourceChoices() {
        select("home.featured")
        XCTAssertTrue(app.buttons["detail.episode.1"].waitForExistence(timeout: 10))
        select("detail.episodes.more")
        XCTAssertTrue(app.buttons["detail.episode.2"].waitForExistence(timeout: 8))
        XCTAssertFalse(app.buttons["detail.episode.3"].exists)
        screenshot("TV Detail episode grid")
        select("detail.episode.0")
        XCTAssertTrue(app.buttons["detail.source.0"].waitForExistence(timeout: 8))
        XCTAssertTrue(app.buttons["detail.source.1"].exists)
        XCTAssertFalse(app.buttons["detail.source.2"].exists)
        XCTAssertFalse(app.staticTexts["Embed"].exists)
        screenshot("TV Quality source chooser")
        remote.press(.menu)
        XCTAssertTrue(app.buttons["detail.episode.0"].waitForExistence(timeout: 5))
    }
    func testClosedSupportThreadAndNotificationAction() {
        select("home.profile")
        select("profile.route.notifications")
        select("notifications.item.notice")
        XCTAssertTrue(app.staticTexts["Marked as read"].waitForExistence(timeout: 8))
        screenshot("TV Notifications")
        remote.press(.menu)
        select("profile.route.support")
        XCTAssertTrue(app.buttons["support.ticket.closed-ticket"].waitForExistence(timeout: 8))
        select("support.ticket.closed-ticket")
        XCTAssertTrue(app.staticTexts["This request is closed"].waitForExistence(timeout: 8))
        XCTAssertFalse(app.textFields["support.reply"].exists)
        screenshot("TV Closed support conversation")
    }
    func testSearchShowsSourceFailureAndEmptyResults() {
        for (term, text) in [("Error", "This source is temporarily unavailable."), ("Missing", "No matching titles")] {
            app.launchArguments = ["--browse-ui-fixture", "--search-query=\(term)"]
            app.launch()
            XCTAssertTrue(app.buttons["profile.pick.adult"].waitForExistence(timeout: 10))
            select("profile.pick.adult")
            XCTAssertTrue(app.buttons["home.featured"].waitForExistence(timeout: 10))
            select("home.search")
            XCTAssertTrue(app.staticTexts[text].waitForExistence(timeout: 10), app.debugDescription)
            XCTAssertFalse(app.buttons["search.more"].exists)
            screenshot("TV Search \(term)")
        }
    }
    func testProfileEditorProtectsDefaultProfile() {
        select("home.profile")
        select("profile.manage")
        select("profile.edit.adult")
        XCTAssertTrue(app.textFields["profile.editor.name"].waitForExistence(timeout: 8))
        XCTAssertEqual(app.textFields["profile.editor.name"].value as? String, "Azamov")
        XCTAssertFalse(app.buttons["profile.editor.delete"].exists)
        screenshot("TV Profile editor")
        remote.press(.menu)
        XCTAssertTrue(app.buttons["profile.switch"].waitForExistence(timeout: 5))
    }

    func testPlaybackPreferencesStayWithTheirProfile() {
        select("home.profile")
        select("profile.settings")
        let saver = app.switches["settings.dataSaver"]
        XCTAssertTrue(saver.waitForExistence(timeout: 5), app.debugDescription)
        let original = saver.value as? String
        select("settings.dataSaver", control: saver)
        let changed = saver.value as? String
        XCTAssertNotEqual(changed, original)
        screenshot("TV Appearance and playback settings")
        remote.press(.menu)
        select("profile.settings")
        XCTAssertTrue(saver.waitForExistence(timeout: 5))
        XCTAssertEqual(saver.value as? String, changed)
        remote.press(.menu)
        select("profile.manage")
        select("profile.switch")
        select("profile.pick.kids")
        XCTAssertTrue(app.buttons["home.profile"].waitForExistence(timeout: 10))
        select("home.profile")
        select("profile.settings")
        XCTAssertTrue(saver.waitForExistence(timeout: 5))
        XCTAssertEqual(saver.value as? String, original)
    }

    func testSourceSelectionUpdatesHomeAndStaysWithProfile() {
        select("home.profile")
        select("profile.route.sources")
        XCTAssertTrue(app.buttons["sources.alternate"].waitForExistence(timeout: 8), app.debugDescription)
        XCTAssertFalse(app.buttons["sources.phone-only"].exists)
        select("sources.alternate")
        XCTAssertTrue(app.staticTexts["Home source updated"].waitForExistence(timeout: 5))
        remote.press(.menu); remote.press(.menu)
        XCTAssertTrue(app.buttons["home.source"].waitForExistence(timeout: 10))
        let expected = XCTNSPredicateExpectation(predicate: NSPredicate(format: "label CONTAINS 'Alternate source'"), object: app.buttons["home.source"].staticTexts.firstMatch)
        XCTAssertEqual(XCTWaiter.wait(for: [expected], timeout: 10), .completed, app.debugDescription)
        select("home.profile"); select("profile.manage"); select("profile.switch"); select("profile.pick.kids")
        XCTAssertTrue(app.buttons["home.source"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons["home.source"].staticTexts["Demo source"].exists, app.debugDescription)
    }

    func testUnavailableSourceDoesNotReplaceWorkingHome() {
        select("home.profile")
        select("profile.route.sources")
        XCTAssertTrue(app.buttons["sources.unavailable"].waitForExistence(timeout: 8))
        select("sources.unavailable")
        XCTAssertTrue(app.staticTexts["This source is temporarily unavailable."].waitForExistence(timeout: 8), app.debugDescription)
        XCTAssertFalse(app.staticTexts["Home source updated"].exists)
        remote.press(.menu); remote.press(.menu)
        XCTAssertTrue(app.buttons["home.source"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons["home.source"].staticTexts["Demo source"].exists)
    }

    func testBannerRotatesAndPausesWhileFocused() {
        let position = app.staticTexts["home.banner.position"]
        XCTAssertTrue(position.waitForExistence(timeout: 5))
        let initial = position.label
        let moved = XCTNSPredicateExpectation(predicate: NSPredicate(format: "label != %@", initial), object: position)
        XCTAssertEqual(XCTWaiter.wait(for: [moved], timeout: 12), .completed)
        select("home.featured", activate: false)
        let focused = position.label
        let changed = XCTNSPredicateExpectation(predicate: NSPredicate(format: "label != %@", focused), object: position)
        changed.isInverted = true
        XCTAssertEqual(XCTWaiter.wait(for: [changed], timeout: 9), .completed)
        screenshot("Banner pauses under Siri Remote focus")
    }
    func testHomeSourceControlOpensFullCatalog() {
        select("home.source")
        XCTAssertTrue(app.textFields["sources.search"].waitForExistence(timeout: 8))
        XCTAssertTrue(app.buttons["sources.category"].exists)
        XCTAssertTrue(app.buttons["sources.language"].exists)
        screenshot("Apple TV Sources catalog")
    }

    func testSourceSearchAndClearFilters() {
        app.launchArguments = ["--browse-ui-fixture", "--source-query=Alternate"]
        app.launch(); XCTAssertTrue(app.buttons["profile.pick.adult"].waitForExistence(timeout: 8)); select("profile.pick.adult")
        XCTAssertTrue(app.buttons["home.source"].waitForExistence(timeout: 8)); select("home.source")
        XCTAssertTrue(app.buttons["sources.alternate"].waitForExistence(timeout: 8))
        XCTAssertFalse(app.buttons["sources.fixture"].exists)
        XCTAssertEqual(app.staticTexts["sources.count"].label, "1 of 3 sources")
        select("sources.clear")
        XCTAssertTrue(app.buttons["sources.fixture"].waitForExistence(timeout: 5))
        XCTAssertEqual(app.staticTexts["sources.count"].label, "3 of 3 sources")
    }

    private func launchLongCatalog() {
        app.launchArguments = ["--browse-ui-fixture", "--season-scroll-fixture"]
        app.launch()
        XCTAssertTrue(app.buttons["profile.pick.adult"].waitForExistence(timeout: 10))
        select("profile.pick.adult")
        XCTAssertTrue(app.buttons["home.featured"].waitForExistence(timeout: 10))
    }
    func testMultipleSeasonsCanBeSelectedWithoutMixingEpisodes() {
        launchLongCatalog(); select("home.featured")
        XCTAssertTrue(app.buttons["detail.season.1"].waitForExistence(timeout: 8), app.debugDescription)
        select("detail.season.4")
        XCTAssertTrue(app.buttons["detail.episode.36"].waitForExistence(timeout: 5), app.debugDescription)
        XCTAssertEqual(app.buttons["detail.episode.36"].label, "S4E1 · Chapter 1")
        XCTAssertFalse(app.buttons["detail.episode.0"].exists)
        screenshot("Season four episode grid")
        select("detail.episode.36")
        XCTAssertTrue(app.buttons["detail.source.0"].waitForExistence(timeout: 8))
        screenshot("Season four episode source selection")
    }
    func testLongShelvesMoveVerticallyAndReturnToHorizontalPosition() {
        launchLongCatalog()
        select("media.fixture|https://fixture.invalid/title/100", activate: false)
        for _ in 0..<9 { remote.press(.right) }
        let remembered = app.descendants(matching: .any).matching(NSPredicate(format: "hasFocus == true")).firstMatch.identifier
        XCTAssertTrue(remembered.hasPrefix("media."), app.debugDescription)
        remote.press(.down); Thread.sleep(forTimeInterval: 0.5)
        let next = app.descendants(matching: .any).matching(NSPredicate(format: "hasFocus == true")).firstMatch
        XCTAssertTrue(next.identifier.contains("/title/2"), app.debugDescription)
        remote.press(.up); Thread.sleep(forTimeInterval: 0.5)
        XCTAssertEqual(app.descendants(matching: .any).matching(NSPredicate(format: "hasFocus == true")).firstMatch.identifier, remembered)
        for _ in 0..<6 { remote.press(.down); Thread.sleep(forTimeInterval: 0.25) }
        let last = app.descendants(matching: .any).matching(NSPredicate(format: "hasFocus == true")).firstMatch
        XCTAssertTrue(last.identifier.contains("/title/7"), app.debugDescription)
        XCTAssertTrue(last.frame.intersects(app.frame))
        for _ in 0..<6 { remote.press(.up); Thread.sleep(forTimeInterval: 0.25) }
        XCTAssertEqual(app.descendants(matching: .any).matching(NSPredicate(format: "hasFocus == true")).firstMatch.identifier, remembered, "A shelf must keep its focused title after leaving the viewport")
        screenshot("Long horizontal shelves after vertical navigation")
    }

    func testAppearanceControlsRemainReadableWhenFocused() {
        select("home.profile"); select("profile.settings")
        let glass = app.switches["settings.liquidGlass"]
        XCTAssertTrue(glass.waitForExistence(timeout: 8))
        select("settings.liquidGlass", control: glass, activate: false)
        screenshot("Readable Liquid Glass setting under focus")
    }

    func testAccountRetryPreservesTypedDraftAndNotificationsRetrySamePage() {
        app.launchArguments = ["--browse-ui-fixture", "--retry-fixture", "--account-draft=New account name"]
        app.launch(); XCTAssertTrue(app.buttons["profile.pick.adult"].waitForExistence(timeout: 10)); select("profile.pick.adult")
        XCTAssertTrue(app.buttons["home.profile"].waitForExistence(timeout: 10)); select("home.profile")
        select("profile.route.account")
        XCTAssertTrue(app.textFields["account.name"].waitForExistence(timeout: 8))
        select("Save name")
        XCTAssertTrue(app.staticTexts["Name save failed once"].waitForExistence(timeout: 8))
        select("Try again")
        XCTAssertTrue(app.staticTexts["Saved"].waitForExistence(timeout: 8))
        XCTAssertEqual(app.textFields["account.name"].value as? String, "New account name")
        remote.press(.menu)
        select("profile.route.notifications")
        XCTAssertTrue(app.buttons["notifications.item.notice-1"].waitForExistence(timeout: 8))
        select("Load more")
        XCTAssertTrue(app.staticTexts["Notification page failed once"].waitForExistence(timeout: 8))
        select("Retry")
        XCTAssertTrue(app.buttons["notifications.item.notice-2"].waitForExistence(timeout: 8))
        XCTAssertFalse(app.buttons["notifications.item.notice-3"].exists)
    }

    func testResumeLoadsHistoryEpisodeOnLaterPageAndSelectsItsSeason() {
        app.launchArguments = ["--browse-ui-fixture", "--resume-pages"]
        app.launch(); XCTAssertTrue(app.buttons["profile.pick.adult"].waitForExistence(timeout: 10)); select("profile.pick.adult")
        XCTAssertTrue(app.buttons["home.resume.fixture|https://fixture.invalid/title/1|3"].waitForExistence(timeout: 10))
        select("home.resume.fixture|https://fixture.invalid/title/1|3")
        XCTAssertTrue(app.buttons["detail.episode.2"].waitForExistence(timeout: 10), app.debugDescription)
        XCTAssertTrue(app.buttons["detail.episode.2"].label.contains("S2E1 · Resumed"))
        XCTAssertFalse(app.buttons["detail.episode.0"].exists)
    }

    func testDuplicateSearchPageStillReachesLaterTitles() {
        app.launchArguments = ["--browse-ui-fixture", "--search-query=Fixture", "--duplicate-pages"]
        app.launch(); select("profile.pick.adult"); select("home.search")
        XCTAssertTrue(app.staticTexts["2 titles"].waitForExistence(timeout: 8))
        select("search.more")
        XCTAssertTrue(app.buttons["search.more"].waitForExistence(timeout: 8), "An overlapping page must not hide subsequent pages")
        select("search.more")
        XCTAssertTrue(app.staticTexts["3 titles"].waitForExistence(timeout: 8))
        XCTAssertFalse(app.buttons["search.more"].exists)
    }
    func testSupportPaginationRetriesFailedPage() {
        app.launchArguments = ["--browse-ui-fixture", "--support-retry-fixture"]
        app.launch(); select("profile.pick.adult"); select("home.profile"); select("profile.route.support")
        XCTAssertTrue(app.buttons["support.ticket.ticket-1"].waitForExistence(timeout: 8))
        select("support.loadMore")
        XCTAssertTrue(app.staticTexts["Support page failed once"].waitForExistence(timeout: 8))
        select("Try again")
        XCTAssertTrue(app.buttons["support.ticket.ticket-2"].waitForExistence(timeout: 8))
        XCTAssertFalse(app.buttons["support.loadMore"].exists)
    }

    func testFriendsRetriesSearchInsteadOfRefreshingLists() {
        app.launchArguments = ["--browse-ui-fixture", "--friends-query=alex", "--friends-retry-fixture"]
        app.launch(); select("profile.pick.adult"); select("home.profile"); select("profile.route.friends")
        select("friends.search.submit")
        XCTAssertTrue(app.staticTexts["Friend search failed once"].waitForExistence(timeout: 8))
        select("Retry")
        XCTAssertTrue(app.staticTexts["Alex found"].waitForExistence(timeout: 8))
    }
    func testProfileEditorNativeRowsRemainReadable() {
        select("home.profile"); select("profile.manage"); select("profile.edit.locked")
        let kids = app.switches["profile.editor.kids"]
        select("profile.editor.kids", control: kids, activate: false)
        XCTAssertTrue(kids.hasFocus || app.cells.containing(.any, identifier: "profile.editor.kids").matching(NSPredicate(format: "hasFocus == true")).firstMatch.exists)
        screenshot("Editor Kids row focused")
        let pin = app.descendants(matching: .any).matching(identifier: "profile.editor.pinMode").firstMatch
        select("profile.editor.pinMode", control: pin, activate: false)
        screenshot("Editor PIN row focused")
    }

}
