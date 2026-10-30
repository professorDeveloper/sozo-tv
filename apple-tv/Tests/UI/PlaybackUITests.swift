import XCTest

final class PlaybackUITests: XCTestCase {
    let app = XCUIApplication()
    let clip = "/tmp/sozo-tvos-playback-smoke.mp4"
    private func launch(autoPlay: Bool = true) throws {
        try XCTSkipUnless(FileManager.default.fileExists(atPath: clip), "Generate the local test clip using scripts/check-playback.sh")
        continueAfterFailure = false
        app.launchArguments = ["--player-smoke"] + (autoPlay ? [] : ["--autoplay-off"])
        app.launchEnvironment["SOZO_PLAYBACK_FILE"] = clip
        app.launch()
    }
    private func expect(_ prefix: String) {
        let state = app.staticTexts["playback.probe"]
        XCTAssertTrue(state.waitForExistence(timeout: 10))
        let expected = XCTNSPredicateExpectation(predicate: NSPredicate(format: "label BEGINSWITH %@", prefix), object: state)
        XCTAssertEqual(XCTWaiter.wait(for: [expected], timeout: 15), .completed, app.debugDescription)
    }
    func testNativePlaybackResumesAndRemotePauses() throws {
        try launch()
        expect("Playing")
        let seconds = Int(app.staticTexts["playback.probe"].label.components(separatedBy: " • ").last?.dropLast() ?? "") ?? 0
        XCTAssertGreaterThanOrEqual(seconds, 10)
        XCUIRemote.shared.press(.playPause)
        expect("Paused")
        XCUIRemote.shared.press(.playPause)
        expect("Playing")
        let shot = XCTAttachment(screenshot: app.screenshot()); shot.name = "Native AVKit resumed playback"; shot.lifetime = .keepAlways; add(shot)
    }
    func testAutoPlayOffWaitsForRemote() throws {
        try launch(autoPlay: false)
        expect("Paused")
        XCUIRemote.shared.press(.playPause)
        expect("Playing")
    }
    func testUnreadableVideoShowsRecoveryAction() throws {
        continueAfterFailure = false
        let broken = FileManager.default.temporaryDirectory.appendingPathComponent("sozo-unreadable-player-fixture.mp4")
        try Data("not a video".utf8).write(to: broken)
        defer { try? FileManager.default.removeItem(at: broken) }
        app.launchArguments = ["--player-smoke"]
        app.launchEnvironment["SOZO_PLAYBACK_FILE"] = broken.path
        app.launch()
        XCTAssertTrue(app.alerts["Playback unavailable"].waitForExistence(timeout: 15), app.debugDescription)
        XCTAssertTrue(app.buttons["Choose another source"].exists)
    }

    func testRemoteSelectShowsNativeTimelineAndPlaybackControls() throws {
        try launch(); expect("Playing")
        XCUIRemote.shared.press(.select)
        XCTAssertTrue(app.otherElements["Media Controls"].waitForExistence(timeout: 6), app.debugDescription)
        XCTAssertTrue(app.cells["AVAudibleSettings"].exists, app.debugDescription)
        XCTAssertTrue(app.cells.matching(NSPredicate(format: "label CONTAINS 'Playback options'")).firstMatch.exists, app.debugDescription)
        let shot = XCTAttachment(screenshot: app.screenshot()); shot.name = "Native player controls and timeline"; shot.lifetime = .keepAlways; add(shot)
    }

    private func openPlayerMenu(_ title: String) {
        XCTAssertTrue(app.otherElements["player.surface"].waitForExistence(timeout: 10), app.debugDescription)
        if !app.otherElements["Media Controls"].isHittable { XCUIRemote.shared.press(.select) }
        XCTAssertTrue(app.otherElements["Media Controls"].waitForExistence(timeout: 6))
        let menu = app.cells.matching(NSPredicate(format: "label CONTAINS %@", title)).firstMatch
        XCTAssertTrue(menu.exists, app.debugDescription)
        XCUIRemote.shared.press(.up)
        for _ in 0..<8 {
            if menu.hasFocus { XCUIRemote.shared.press(.select); return }
            let current = app.cells.matching(NSPredicate(format: "hasFocus == true")).firstMatch
            XCUIRemote.shared.press(current.exists && current.frame.midX < menu.frame.midX ? .right : .left)
        }
        XCTFail("Could not focus \(title): \(app.debugDescription)")
    }
    private func choosePlayerAction(_ title: String) {
        let matches = app.cells.matching(NSPredicate(format: "label BEGINSWITH %@", title))
        XCTAssertTrue(matches.firstMatch.waitForExistence(timeout: 4), app.debugDescription)
        for _ in 0..<10 {
            if matches.matching(NSPredicate(format: "hasFocus == true")).firstMatch.exists { XCUIRemote.shared.press(.select); return }
            let current = app.cells.matching(NSPredicate(format: "hasFocus == true")).firstMatch
            XCUIRemote.shared.press(current.exists && current.frame.midY > matches.firstMatch.frame.midY ? .up : .down)
        }
        XCTFail("Could not focus \(title): \(app.debugDescription)")
    }
    func testNextEpisodeAndSourceChangeMenuReturnToCaller() throws {
        for (action, route) in [("Source & quality", "Source picker requested"), ("Next episode", "Next episode requested")] {
            try launch(); expect("Playing")
            openPlayerMenu("Playback options"); choosePlayerAction(action)
            XCTAssertTrue(app.staticTexts[route].waitForExistence(timeout: 6), app.debugDescription)
        }
    }
    func testExternalSubtitleTrackRendersAndCanBeDisabled() throws {
        let file = FileManager.default.temporaryDirectory.appendingPathComponent("sozo-sidecar-smoke.vtt")
        try Data("WEBVTT\n\n00:00:00.000 --> 00:01:00.000\nFixture caption\n".utf8).write(to: file)
        defer { try? FileManager.default.removeItem(at: file) }
        app.launchEnvironment["SOZO_SUBTITLE_FILE"] = file.path
        try launch(); expect("Playing")
        openPlayerMenu("External subtitles"); choosePlayerAction("Fixture English")
        XCTAssertTrue(app.staticTexts["player.sidecar.text"].waitForExistence(timeout: 8), app.debugDescription)
        XCTAssertEqual(app.staticTexts["player.sidecar.text"].label, "Fixture caption")
        XCTAssertLessThan(app.staticTexts["player.sidecar.text"].frame.maxY, 810, "Captions must clear the native transport controls")
        let shot = XCTAttachment(screenshot: app.screenshot()); shot.name = "External subtitles on native player"; shot.lifetime = .keepAlways; add(shot)
        openPlayerMenu("External subtitles"); choosePlayerAction("External subtitles off")
        let hidden = XCTNSPredicateExpectation(predicate: NSPredicate(format: "exists == false"), object: app.staticTexts["player.sidecar.text"])
        XCTAssertEqual(XCTWaiter.wait(for: [hidden], timeout: 5), .completed)
    }

    private func focusAndSelectButton(_ title: String) {
        let target = app.buttons[title]
        XCTAssertTrue(target.waitForExistence(timeout: 10), app.debugDescription)
        for _ in 0..<25 {
            if target.hasFocus { XCUIRemote.shared.press(.select); return }
            let current = app.buttons.matching(NSPredicate(format: "hasFocus == true")).firstMatch
            if current.exists && target.frame.maxY < current.frame.minY { XCUIRemote.shared.press(.up) }
            else if current.exists && target.frame.minY > current.frame.maxY { XCUIRemote.shared.press(.down) }
            else { XCUIRemote.shared.press(current.exists && target.frame.midX < current.frame.midX ? .left : .right) }
        }
        XCTFail("Cannot focus \(title): \(app.debugDescription)")
    }
    func testDetailPlayerChangesSourceAndAdvancesToNextEpisode() throws {
        continueAfterFailure = false
        app.launchArguments = ["--browse-ui-fixture", "--native-detail-playback"]
        app.launch()
        focusAndSelectButton("profile.pick.adult")
        focusAndSelectButton("home.featured")
        focusAndSelectButton("detail.episode.0")
        openPlayerMenu("External subtitles"); choosePlayerAction("English")
        XCTAssertTrue(app.staticTexts["player.sidecar.text"].waitForExistence(timeout: 8), app.debugDescription)
        XCTAssertEqual(app.staticTexts["player.sidecar.text"].label, "Local server subtitle")
        openPlayerMenu("Playback options"); choosePlayerAction("Forward 10 seconds")
        let position = app.staticTexts["player.position"]
        let advanced = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in (Int(position.label) ?? -1) >= 10 }, object: position)
        XCTAssertEqual(XCTWaiter.wait(for: [advanced], timeout: 8), .completed)
        let checkpoint = Int(position.label) ?? 0
        openPlayerMenu("Playback options"); choosePlayerAction("Source & quality")
        XCTAssertTrue(app.buttons["detail.source.0"].waitForExistence(timeout: 10), app.debugDescription)
        focusAndSelectButton("detail.source.0")
        let retained = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in (Int(position.label) ?? -1) >= checkpoint - 2 }, object: position)
        XCTAssertEqual(XCTWaiter.wait(for: [retained], timeout: 8), .completed, "Source changes must retain current playback time")
        openPlayerMenu("Playback options"); choosePlayerAction("Next episode")
        openPlayerMenu("Playback options")
        XCTAssertTrue(app.staticTexts.matching(NSPredicate(format: "label CONTAINS 'Episode 2 · The crossing'")).firstMatch.waitForExistence(timeout: 10), app.debugDescription)
    }

    func testNextEpisodeSkipsOverlappingEpisodePage() throws {
        continueAfterFailure = false
        app.launchArguments = ["--browse-ui-fixture", "--native-detail-playback", "--next-page-overlap"]
        app.launch(); focusAndSelectButton("profile.pick.adult"); focusAndSelectButton("home.featured"); focusAndSelectButton("detail.episode.1")
        openPlayerMenu("Playback options"); choosePlayerAction("Next episode")
        openPlayerMenu("Playback options")
        XCTAssertTrue(app.staticTexts.matching(NSPredicate(format: "label CONTAINS 'Episode 3 · Homeward'")).firstMatch.waitForExistence(timeout: 8), app.debugDescription)
    }

    func testBackgroundReturnWaitsForExplicitRemotePlay() throws {
        try launch(); expect("Playing")
        XCUIRemote.shared.press(.home)
        let background = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in self.app.state != .runningForeground }, object: app)
        XCTAssertEqual(XCTWaiter.wait(for: [background], timeout: 8), .completed)
        app.activate()
        expect("Paused")
        XCUIRemote.shared.press(.playPause)
        expect("Playing")
    }

}
