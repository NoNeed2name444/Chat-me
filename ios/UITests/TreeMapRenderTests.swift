import XCTest

/// S11: the 3D Tree rendered for real, on the approved concept's sample (the
/// same parts as tree-fixture.json's "sample" and Android's TreeMapRenderTest),
/// each view kept as a named screenshot: whole (the first view, the first
/// visit's card up and the trunk above it), orbit (a drag of about 40 degrees
/// round the trunk) and cardiology (flown in to Cardiology by a tap on its
/// name). At each the names drawn never overlap and the big branches are
/// named; the app says where it put each name (`-SortaTreeProbe`). CI runs it
/// by day and by night on the iPhone and the iPad (tools/run_ui_tests.sh, the
/// ideas set). On the iPad the map is the pane beside the Sort screen.
final class TreeMapRenderTests: XCTestCase {
    override func setUp() {
        super.setUp()
        continueAfterFailure = false
        Retaps.reset()
    }

    func testTreeRenders() throws {
        let app = launchIdeas(IdeasStoreName(), ["-SortaImportFile", try TreeSample.base64(), "-SortaFirstRun", "-SortaTreeProbe"])
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

        app.chooseLook("look-tree")
        let chip = app.descendants(matching: .any)[MapText.lookChip].firstMatch
        let named = NSPredicate(format: "label == %@", "Theme, Tree")
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: named, object: chip)], timeout: 10),
                       .completed, "the chip names the Tree: \(chip.label)")
        let map = app.descendants(matching: .any)["tree-map"].firstMatch
        XCTAssertTrue(map.waitForExistence(timeout: 10), "the Tree")
        XCTAssertTrue(map.label.hasPrefix("Tree map of ideas, showing All ideas"), "the tree read whole: \(map.label)")
        let card = app.descendants(matching: .any)["tree-first-visit"].firstMatch
        XCTAssertTrue(card.waitForExistence(timeout: 10), "the first visit's card")
        sleep(2)
        // each still is kept before its checks, so a failed check still leaves the picture
        keepShot("tree-whole")
        let whole = names(app, "whole", want: ["All ideas", "Examples", "Cardiology"])
        let trunk = try XCTUnwrap(whole.names.first { $0.0 == "All ideas" }, "the trunk named")
        XCTAssertLessThan(map.frame.minY + trunk.1.maxY, card.frame.minY,
                          "the trunk's name \(trunk.1) (map at \(map.frame.minY)) runs under the card \(card.frame)")

        // a drag of about 40 degrees round the trunk (3.2 radians a view's width)
        let from = map.coordinate(withNormalizedOffset: CGVector(dx: 0.70, dy: 0.4))
        let to = map.coordinate(withNormalizedOffset: CGVector(dx: 0.482, dy: 0.4))
        from.press(forDuration: 0.1, thenDragTo: to)
        sleep(2)
        keepShot("tree-orbit")
        let turned = names(app, "orbit", want: ["All ideas", "Cardiology"]).yaw - whole.yaw
        XCTAssertTrue((0.45...1.0).contains(turned), "the drag turned the tree \(turned) radians, not about 40 degrees")

        // a tap on Cardiology's name flies in to it
        let cardiology = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", "Cardiology, folder")).firstMatch
        XCTAssertTrue(cardiology.waitForExistence(timeout: 10), "Cardiology, named")
        cardiology.tap()
        let back = app.descendants(matching: .any)["tree-back"].firstMatch
        XCTAssertTrue(back.waitForExistence(timeout: 10), "flown in, Back")
        XCTAssertEqual(back.label, "Back to All ideas")
        XCTAssertGreaterThanOrEqual(back.frame.width, 43.5, "Back's target: \(back.frame)")
        let inside = NSPredicate(format: "label BEGINSWITH %@", "Tree map of ideas, showing Cardiology")
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: inside, object: map)], timeout: 10),
                       .completed, "Cardiology is the whole tree: \(map.label)")
        let gone = NSPredicate(format: "exists == false")
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: gone, object: card)], timeout: 10),
                       .completed, "the first visit's card is put away")
        sleep(2)
        keepShot("tree-cardiology")
        let flown = names(app, "cardiology", want: ["Cardiology", "Heart failure"])
        let parts = flown.names.filter { TreeSample.under("Cardiology", $0.0) }
        XCTAssertGreaterThanOrEqual(parts.count, 6, "few of Cardiology's parts named: \(flown.names.map(\.0))")
        // each name a VoiceOver element a touch target each way at least
        let heart = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", "Heart failure, folder")).firstMatch
        XCTAssertTrue(heart.waitForExistence(timeout: 10), "Heart failure, named")
        XCTAssertGreaterThanOrEqual(heart.frame.width, 43.5, "Heart failure's target: \(heart.frame)")
        XCTAssertGreaterThanOrEqual(heart.frame.height, 43.5, "Heart failure's target: \(heart.frame)")

        back.tap()
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: gone, object: back)], timeout: 10),
                       .completed, "back to the whole tree")
    }

    /// What the app placed: the camera's turn and each name's box, in the map.
    struct Placed {
        var yaw: Double = 0
        var names: [(String, CGRect)] = []
    }

    /// The names drawn now (waiting up to 10 s for `want` among them): none
    /// overlapping, `want` all named.
    private func names(_ app: XCUIApplication, _ what: String, want: [String]) -> Placed {
        let probe = app.descendants(matching: .any)["tree-probe"].firstMatch
        XCTAssertTrue(probe.waitForExistence(timeout: 10), "\(what): the probe")
        var placed = Placed()
        let deadline = Date().addingTimeInterval(10)
        repeat {
            placed = Self.read(probe.label)
            if Set(want).isSubset(of: Set(placed.names.map(\.0))) { break }
            _ = XCTWaiter.wait(for: [XCTestExpectation(description: "names")], timeout: 0.5)
        } while Date() < deadline
        let words = placed.names.map(\.0)
        XCTAssertTrue(Set(want).isSubset(of: Set(words)),
                      "\(what): \(want) not all named: " + placed.names.map { "\($0.0) \($0.1)" }.joined(separator: ", "))
        for a in placed.names.indices {
            for b in placed.names.indices where b > a {
                let p = placed.names[a], q = placed.names[b]
                XCTAssertFalse(p.1.intersection(q.1).width > 0.01 && p.1.intersection(q.1).height > 0.01,
                               "\(what): \(p.0) \(p.1) overlaps \(q.0) \(q.1)")
            }
        }
        return placed
    }

    /// The probe's label: "width height yaw focus cardTop", then "name<TAB>left top right bottom" a line each.
    static func read(_ label: String) -> Placed {
        var out = Placed()
        let lines = label.components(separatedBy: "\n")
        guard let head = lines.first else { return out }
        let first = head.split(separator: " ")
        if first.count >= 3 { out.yaw = Double(first[2]) ?? 0 }
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

/// The approved concept's sample, as tree-fixture.json's "sample" and
/// Android's TreeMapRenderTest hold it: each part's parent and title. A part
/// with parts is a folder, the rest ideas, made a minute apart in its order.
enum TreeSample {
    static let parent: [Int] = [
        -1, 0, 1, 2, 3, 4, 5, 5, 4, 8, 8, 4, 11, 12, 4, 14, 14, 3, 17, 17, 3, 20, 20, 20,
        2, 24, 24, 26, 26, 24, 29, 29, 2, 32, 32, 32, 35, 2, 37, 37, 37, 2, 41, 41, 2, 44, 44, 1,
        47, 48, 48, 48, 51, 51, 51, 51, 47, 56, 56, 47, 59, 59, 59, 1, 63, 63, 63, 1, 67, 68, 67, 70,
        67, 1, 73, 0, 75, 76, 77, 78, 77, 80, 77, 82, 76, 84, 84, 84, 76, 88, 88, 88, 76, 92, 92, 92,
        92, 75, 97, 97, 75, 100, 101, 100, 75, 104, 104, 104, 75, 108, 108, 108, 75, 112, 112, 75, 115, 115, 75, 118,
        118, 75, 121, 121, 75, 124, 124, 124, 75, 128, 75, 130, 130, 130, 75, 134, 134, 75, 137, 137, 0, 140, 140, 140,
        0, 144, 144, 144, 144]
    static let title: [String] = [
        "All ideas", "Examples", "Inguinal", "Anatomy",
        "Canal boundaries", "Anterior", "External oblique all through", "Internal oblique laterally",
        "Posterior", "Fascia transversalis all through", "Internal oblique medially", "Roof",
        "Conjoined muscles", "Internal oblique and transversus", "Floor", "Inguinal ligament",
        "Lacunar ligament", "LA/PM mnemonic", "Anterior wall Laterally", "Posterior wall Medially",
        "Spermatic cord coverings", "Internal spermatic fascia", "Cremasteric muscle and fascia", "External spermatic fascia",
        "Inguinal canal", "4 cm, running obliquely", "In males", "Spermatic cord",
        "Ilio-inguinal nerve", "In females", "Round ligament of the uterus", "Ilio-inguinal nerve",
        "Indirect inguinal hernia", "70% of all hernias", "The defect is the stretched deep inguinal ring", "The sac lies inside the cord",
        "May reach the scrotum", "Direct inguinal hernia", "Through the posterior wall of the canal", "Adult or elderly",
        "Reduces backwards", "Inferior epigastric vessels", "Sac lateral = indirect inguinal hernia", "Sac medial = direct inguinal hernia",
        "Internal ring test", "Positive (swelling controlled) = oblique", "Negative = direct", "Femoral",
        "Femoral hernia", "More common in women", "Neck below and lateral to the pubic tubercle", "Femoral ring",
        "Inguinal ligament in front", "Pectineal ligament behind", "Femoral vein laterally", "Lacunar ligament medially",
        "Femoral canal", "Medial to the femoral vein", "1.25 cm long, cone shaped", "Saphena varix",
        "Disappears completely lying flat", "Fluid thrill on cough", "Venous hum", "Groin hernia",
        "Three to know", "The anatomy first", "The only treatment is surgery", "Hernia repair",
        "TAPP", "Transabdominal preperitoneal", "TEP", "Totally extraperitoneal",
        "Robotic-assisted repair", "Expansile cough impulse", "A hernia until shown otherwise", "Cardiology",
        "Heart failure", "Types", "HFrEF", "Ejection fraction 40% or less",
        "HFpEF", "Ejection fraction 50% or more", "Mildly reduced", "41 to 49%",
        "Causes", "Ischaemic heart disease", "Hypertension", "Valve disease",
        "Symptoms and signs", "Raised JVP", "Ankle swelling", "Basal crackles",
        "Treatment", "Loop diuretics for congestion", "Beta-blocker", "SGLT2 inhibitor",
        "Start low and titrate", "BNP", "Raised in heart failure", "A normal level makes it unlikely",
        "Loop diuretics", "Furosemide", "For congestion", "Watch hypokalaemia",
        "ACE inhibitors", "Prognostic in heart failure", "Cough", "Check hypokalaemia the other way",
        "Hypokalaemia", "Flat T waves", "U waves", "Risk with loop diuretics",
        "Acute coronary syndrome", "Unstable angina", "Troponin tells them apart", "STEMI",
        "ST elevation", "Primary PCI", "NSTEMI", "Raised troponin",
        "Without ST elevation", "Troponin", "Rises in 3 hours", "Peaks at 24",
        "Atrial fibrillation", "Irregularly irregular", "Rate or rhythm control", "CHA2DS2-VASc for anticoagulation",
        "CHA2DS2-VASc", "Stroke risk", "Murmurs", "Aortic stenosis",
        "Mitral regurgitation", "Heart failure as the end point", "Heart sounds", "S1 and S2",
        "Listen at the apex for S3", "Syncope", "Cardiac or not?", "Exertional syncope needs an echo",
        "Richter's hernia", "Only part of the bowel wall is trapped", "Can strangulate without obstructing", "Most often through the femoral ring",
        "Pericarditis", "Sharp chest pain", "Better sitting forward", "Saddle-shaped ST elevation",
        "PR depression"]

    static func id(_ i: Int) -> String { String(format: "00000000-0000-4000-8000-%012X", 0x7000 + i) }

    /// Whether a part titled `name` is under (or is) the part titled `top`.
    static func under(_ top: String, _ name: String) -> Bool {
        guard let t = title.firstIndex(of: top) else { return false }
        return title.indices.contains { i in
            guard title[i] == name else { return false }
            var q = i
            while q > 0 { if q == t { return true }; q = parent[q] }
            return q == t
        }
    }

    /// The sample as a Stethoscore backup, as base64, for `-SortaImportFile`.
    static func base64() throws -> String {
        var kids = [Int](repeating: 0, count: parent.count)
        for i in 1..<parent.count { kids[parent[i]] += 1 }
        let start = ISO8601DateFormatter().date(from: "2026-09-01T10:00:00Z")!
        func when(_ minutes: Int) -> String { ISO8601DateFormatter().string(from: start.addingTimeInterval(Double(minutes) * 60)) }
        var folders: [[String: Any]] = [], notes: [[String: Any]] = []
        for i in 1..<parent.count {
            if kids[i] > 0 {
                var f: [String: Any] = ["id": id(i), "name": title[i], "createdAt": when(i)]
                if parent[i] != 0 { f["parentId"] = id(parent[i]) }
                folders.append(f)
            } else {
                var n: [String: Any] = ["id": id(i), "title": title[i], "body": "", "kind": "idea", "links": [String](),
                                        "tags": [String](), "createdAt": when(i), "updatedAt": when(i)]
                if parent[i] != 0 { n["folderId"] = id(parent[i]) }
                notes.append(n)
            }
        }
        let data = try JSONSerialization.data(withJSONObject: ["folders": folders, "notes": notes], options: [.sortedKeys])
        return try MadeUpExport.base64(notes: String(decoding: data, as: UTF8.self), count: notes.count)
    }
}
