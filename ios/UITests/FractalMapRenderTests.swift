import XCTest

/// S12: the 3D Fractal rendered for real, on the approved concept's sample
/// (TreeSample, the same parts as tools/fractal_fixture.json and Android's
/// FractalMapRenderTest), each view kept as a named screenshot: whole (the
/// first view, the first visit's card up), orbit (a drag of about 40
/// degrees), orbit120 (about 120 degrees round) and cardiology (flown in to
/// Cardiology by a tap on its name). At each the names drawn never overlap
/// and the big hubs are named (the app says where it put each name,
/// `-SortaFractalProbe`); what is drawn, glows and all, stays in the room the
/// framing fit it to, and flown in it is centred there with the first visit's
/// card gone; a filter changed while flown in keeps the hub and Back; the
/// map is Performance's night sky by day too, with
/// lit dots on it. CI runs it by day and by night on the iPhone and the iPad
/// (tools/run_ui_tests.sh, the ideas set). On the iPad the map is the pane
/// beside the Sort screen.
final class FractalMapRenderTests: XCTestCase {
    override func setUp() {
        super.setUp()
        continueAfterFailure = false
        Retaps.reset()
    }

    func testFractalRenders() throws {
        let app = launchIdeas(IdeasStoreName(), ["-SortaImportFile", try TreeSample.base64(), "-SortaFirstRun", "-SortaFractalProbe"])
        app.more.tap()
        app.buttons[IdeasText.importFromStethoscore].firstMatch.tap()
        let added = app.alerts[IdeasText.importedTitle]
        XCTAssertTrue(added.waitForExistence(timeout: 20), "the import added the sample")
        added.buttons[IdeasText.done].tap()
        XCTAssertTrue(app.folderRow("Examples").waitForExistence(timeout: 10), "the sample's Examples folder")
        app.keyboards.buttons["Done"].firstMatch.tapIfThere()
        let window = app.windows.firstMatch.frame
        if window.width >= 700 {
            XCTAssertTrue(app.descendants(matching: .any)[MapText.lookChip].waitForExistence(timeout: 10),
                          "two panes: the map beside the Sort screen")
        } else {
            app.switchMode(to: "mode-map")
        }

        app.chooseLook("look-fractal")
        let chip = app.descendants(matching: .any)[MapText.lookChip].firstMatch
        let named = NSPredicate(format: "label == %@", "Theme, Fractal")
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: named, object: chip)], timeout: 10),
                       .completed, "the chip names the Fractal: \(chip.label)")
        let map = app.descendants(matching: .any)["fractal-map"].firstMatch
        XCTAssertTrue(map.waitForExistence(timeout: 10), "the Fractal")
        XCTAssertTrue(map.label.hasPrefix("Fractal map of ideas, showing All ideas"), "the map read whole: \(map.label)")
        let card = app.descendants(matching: .any)["fractal-first-visit"].firstMatch
        XCTAssertTrue(card.waitForExistence(timeout: 10), "the first visit's card")
        sleep(2)
        // each still is kept before its checks, so a failed check still leaves the picture
        keepShot("fractal-whole")
        sky(map, "whole")
        let whole = names(app, "whole", want: ["All ideas", "Examples", "Cardiology"])
        XCTAssertGreaterThan(whole.dots, 20, "whole: dots drawn")
        let hub = try XCTUnwrap(whole.names.first { $0.0 == "All ideas" }, "All ideas named")
        XCTAssertLessThan(map.frame.minY + hub.1.maxY, card.frame.minY,
                          "All ideas' name \(hub.1) (map at \(map.frame.minY)) runs under the card \(card.frame)")
        framed(whole, "whole", centred: false)

        // a drag of about 40 degrees round (3.2 radians a view's width)
        turn(map, by: 0.218)
        keepShot("fractal-orbit")
        sky(map, "orbit")
        let orbit = names(app, "orbit", want: ["All ideas"])
        let turned = orbit.yaw - whole.yaw
        XCTAssertTrue((0.45...1.0).contains(turned), "the drag turned the map \(turned) radians, not about 40 degrees")

        // about 80 degrees more: 120 round from the first view. Cardiology's parts stand round All ideas' dot
        // there, so a name of it would read as theirs, and none is wanted (the review of S12's stills, item 9)
        turn(map, by: 0.218)
        turn(map, by: 0.218)
        keepShot("fractal-orbit120")
        sky(map, "orbit120")
        let far = names(app, "orbit120", want: [])
        XCTAssertTrue((1.6...2.6).contains(far.yaw - whole.yaw), "the drags turned the map \(far.yaw - whole.yaw) radians, not about 120 degrees")

        // Recentre, then a tap on Cardiology's name flies in to it
        app.buttons["Recentre"].firstMatch.tap()
        let cardiology = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", "Cardiology, folder")).firstMatch
        XCTAssertTrue(cardiology.waitForExistence(timeout: 10), "Cardiology, named")
        cardiology.tap()
        let back = app.descendants(matching: .any)["fractal-back"].firstMatch
        XCTAssertTrue(back.waitForExistence(timeout: 10), "flown in, Back")
        XCTAssertEqual(back.label, "Back to All ideas")
        XCTAssertGreaterThanOrEqual(back.frame.width, 43.5, "Back's target: \(back.frame)")
        XCTAssertGreaterThanOrEqual(back.frame.height, 43.5, "Back's target: \(back.frame)")
        let inside = NSPredicate(format: "label BEGINSWITH %@", "Fractal map of ideas, showing Cardiology")
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: inside, object: map)], timeout: 10),
                       .completed, "flown in to Cardiology: \(map.label)")
        sleep(2)
        keepShot("fractal-cardiology")
        sky(map, "cardiology")
        let flown = names(app, "cardiology", want: ["Cardiology", "Heart failure"])
        let parts = flown.names.filter { TreeSample.under("Cardiology", $0.0) }
        XCTAssertGreaterThanOrEqual(parts.count, 6, "few of Cardiology's parts named: \(flown.names.map(\.0))")
        // the tap that flew in put the first visit's card away: the fly-in is framed without it (the review, item 1)
        XCTAssertEqual(flown.cardTop, flown.height, accuracy: 0.5, "flown in, the card still framed: its top at \(flown.cardTop) of \(flown.height)")
        framed(flown, "cardiology", centred: true)
        // each name a VoiceOver element a touch target each way at least
        let heart = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", "Heart failure, folder")).firstMatch
        XCTAssertTrue(heart.waitForExistence(timeout: 10), "Heart failure, named")
        XCTAssertGreaterThanOrEqual(heart.frame.width, 43.5, "Heart failure's target: \(heart.frame)")
        XCTAssertGreaterThanOrEqual(heart.frame.height, 43.5, "Heart failure's target: \(heart.frame)")

        // a filter changed while flown in keeps the hub and Back (the review, item 3)
        filter(app, "Linked ideas")
        XCTAssertTrue(back.waitForExistence(timeout: 5), "a filter changed while flown in: Back")
        XCTAssertTrue(map.label.hasPrefix("Fractal map of ideas, showing Cardiology"), "a filter changed while flown in: \(map.label)")
        filter(app, "Everything")
        XCTAssertTrue(back.waitForExistence(timeout: 5), "the filter back to Everything: Back")
        XCTAssertTrue(map.label.hasPrefix("Fractal map of ideas, showing Cardiology"), "the filter back to Everything: \(map.label)")

        back.tap()
        let gone = NSPredicate(format: "exists == false")
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: gone, object: back)], timeout: 10),
                       .completed, "back to the whole map")
    }

    /// Chooses `what` in the map's Filter menu.
    private func filter(_ app: XCUIApplication, _ what: String) {
        let button = app.buttons["Filter"].firstMatch
        XCTAssertTrue(button.waitForExistence(timeout: 5), "the Filter")
        button.tap()
        let item = app.buttons[what].firstMatch
        XCTAssertTrue(item.waitForExistence(timeout: 5), "the Filter's \(what)")
        item.tap()
        sleep(2)
    }

    /// Every dot drawn, its glow and all, inside the room the framing fit it to (a point for rounding), and with
    /// `centred` in that room's middle, as FractalFit puts the box of what it frames (the review, items 1 and 2).
    private func framed(_ placed: Placed, _ what: String, centred: Bool) {
        let room = CGRect(x: placed.room.left, y: placed.room.top, width: placed.width - placed.room.left - placed.room.right,
                          height: placed.height - placed.room.top - placed.room.bottom)
        let drawn = placed.box
        XCTAssertGreaterThan(drawn.width, 0, "\(what): the dots' box")
        XCTAssertTrue(room.insetBy(dx: -1, dy: -1).contains(drawn), "\(what): the dots \(drawn) leave the room \(room)")
        guard centred else { return }
        let slackX = max(4, room.width * 0.03), slackY = max(4, room.height * 0.03)
        XCTAssertEqual(drawn.midX, room.midX, accuracy: slackX, "\(what): the dots \(drawn) off the room's middle \(room)")
        XCTAssertEqual(drawn.midY, room.midY, accuracy: slackY, "\(what): the dots \(drawn) off the room's middle \(room)")
    }

    /// A drag across `share` of the map's width, from its right, at 40% down.
    private func turn(_ map: XCUIElement, by share: CGFloat) {
        let from = map.coordinate(withNormalizedOffset: CGVector(dx: 0.70, dy: 0.4))
        let to = map.coordinate(withNormalizedOffset: CGVector(dx: 0.70 - share, dy: 0.4))
        from.press(forDuration: 0.1, thenDragTo: to)
        sleep(2)
    }

    /// The map is Performance's night sky (#05070C) by day too, over more than
    /// a quarter of it, with lit dots on it (more than a 400th of it bright).
    /// Counted over the map's own picture, so a tablet on its side counts the same.
    private func sky(_ map: XCUIElement, _ what: String) {
        guard let cg = map.screenshot().image.cgImage else { return XCTFail("\(what): no picture of the map") }
        let w = cg.width, h = cg.height
        var pixels = [UInt8](repeating: 0, count: w * h * 4)
        let drawn = pixels.withUnsafeMutableBytes { raw -> Bool in
            guard let ctx = CGContext(data: raw.baseAddress, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4,
                                      space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)
            else { return false }
            ctx.draw(cg, in: CGRect(x: 0, y: 0, width: w, height: h))
            return true
        }
        XCTAssertTrue(drawn, "\(what): the picture read")
        var skyCount = 0, lit = 0, all = 0
        var y = 0
        while y < h {
            var x = 0
            while x < w {
                let o = (y * w + x) * 4
                let r = Int(pixels[o]), g = Int(pixels[o + 1]), b = Int(pixels[o + 2])
                // within 3 a channel: the picture's colour space may be turned to the context's on the way
                if abs(r - 0x05) <= 3 && abs(g - 0x07) <= 3 && abs(b - 0x0C) <= 3 { skyCount += 1 }
                if max(r, g, b) >= 96 { lit += 1 }
                all += 1
                x += 2
            }
            y += 2
        }
        XCTAssertGreaterThan(all, 0, "\(what): the map on screen \(map.frame)")
        XCTAssertGreaterThan(skyCount * 4, all, "\(what): the night sky over \(skyCount) of \(all) of the map")
        XCTAssertGreaterThan(lit * 400, all, "\(what): lit dots over \(lit) of \(all) of the map")
    }

    /// What the app placed: the view's size, the camera's turn, the card's top, the dots drawn and their box with
    /// their glows, the room the framing fit them to, and each name's box, in the map.
    struct Placed {
        var width: CGFloat = 0, height: CGFloat = 0
        var yaw: Double = 0
        var cardTop: CGFloat = 0
        var dots = 0
        var box: CGRect = .zero
        var room: (top: CGFloat, left: CGFloat, bottom: CGFloat, right: CGFloat) = (0, 0, 0, 0)
        var names: [(String, CGRect)] = []
    }

    /// The names drawn now (waiting up to 10 s for `want` among them): none
    /// overlapping, `want` all named.
    private func names(_ app: XCUIApplication, _ what: String, want: [String]) -> Placed {
        let probe = app.descendants(matching: .any)["fractal-probe"].firstMatch
        XCTAssertTrue(probe.waitForExistence(timeout: 10), "\(what): the probe")
        var placed = Placed()
        let deadline = Date().addingTimeInterval(10)
        repeat {
            placed = Self.read(probe.label)
            if Set(want).isSubset(of: Set(placed.names.map(\.0))) { break }
            _ = XCTWaiter.wait(for: [XCTestExpectation(description: "names")], timeout: 0.5)
        } while Date() < deadline
        XCTAssertTrue(Set(want).isSubset(of: Set(placed.names.map(\.0))),
                      "\(what): \(want) not all named: " + placed.names.map { "\($0.0) \($0.1)" }.joined(separator: ", "))
        XCTAssertGreaterThanOrEqual(placed.names.count, 6, "\(what): few names: \(placed.names.map(\.0))")
        for a in placed.names.indices {
            for b in placed.names.indices where b > a {
                let p = placed.names[a], q = placed.names[b]
                XCTAssertFalse(p.1.intersection(q.1).width > 0.01 && p.1.intersection(q.1).height > 0.01,
                               "\(what): \(p.0) \(p.1) overlaps \(q.0) \(q.1)")
            }
        }
        return placed
    }

    /// The probe's label: "width height yaw focus cardTop dots left top right bottom roomTop roomLeft roomBottom
    /// roomRight", then "name<TAB>left top right bottom" a line each.
    static func read(_ label: String) -> Placed {
        var out = Placed()
        let lines = label.components(separatedBy: "\n")
        guard let head = lines.first else { return out }
        let first = head.split(separator: " ")
        let n = first.map { Double($0) ?? 0 }
        if first.count >= 3 { out.width = CGFloat(n[0]); out.height = CGFloat(n[1]); out.yaw = n[2] }
        if first.count >= 6 { out.cardTop = CGFloat(n[4]); out.dots = Int(first[5]) ?? 0 }
        if first.count >= 10 { out.box = CGRect(x: CGFloat(n[6]), y: CGFloat(n[7]), width: CGFloat(n[8] - n[6]), height: CGFloat(n[9] - n[7])) }
        if first.count >= 14 { out.room = (CGFloat(n[10]), CGFloat(n[11]), CGFloat(n[12]), CGFloat(n[13])) }
        for line in lines.dropFirst() {
            let parts = line.components(separatedBy: "\t")
            guard parts.count == 2 else { continue }
            let n = parts[1].split(separator: " ").compactMap { Double($0) }
            guard n.count == 4 else { continue }
            out.names.append((parts[0], CGRect(x: n[0], y: n[1], width: n[2] - n[0], height: n[3] - n[1])))
        }
        return out
    }
}
