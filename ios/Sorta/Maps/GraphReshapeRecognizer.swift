import UIKit
import UIKit.UIGestureRecognizerSubclass

/// Two fingers on the 3D map that reshape an idea (S06c): spread on the
/// chosen idea, it splits; one on the chosen idea and one on another,
/// pushed together, they join. GraphReshapeTouch judges both, as Android's
/// MapReshape does. The map's own pinches wait for this to fail
/// (GraphSCNView.Coordinator's `shouldBeRequiredToFailBy`), so while one
/// waits the camera does not zoom; two fingers anywhere else fail it at
/// once and the camera has them as before. One finger never starts it, so
/// a tap, a turn and hold-to-move are as they were.
final class GraphReshapeRecognizer: UIGestureRecognizer {
    /// What two fingers coming down at these points may become (the
    /// coordinator's: on the chosen idea only, never while a body is held).
    var starts: (CGPoint, CGPoint) -> GraphReshapeTouch.Start = { _, _ in GraphReshapeTouch.Start.none }
    /// What the fingers began as, once two are down; read when it is
    /// recognised.
    private(set) var start: GraphReshapeTouch.Start = .none
    private var decided: Bool = false
    private var from: Double = 0
    private var fingers: [UITouch] = []
    /// Where a lone finger went down: moved past the slop, it is a turn
    /// or a drag, never this.
    private var lone: CGPoint?

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
        super.touchesBegan(touches, with: event)
        fingers.append(contentsOf: touches)
        guard state == .possible else { return }
        if fingers.count == 1 {
            lone = fingers[0].location(in: view)
            return
        }
        guard fingers.count == 2, !decided else {
            state = .failed
            return
        }
        decided = true
        let a: CGPoint = fingers[0].location(in: view)
        let b: CGPoint = fingers[1].location(in: view)
        start = starts(a, b)
        from = Self.apart(a, b)
        if start == GraphReshapeTouch.Start.none { state = .failed }
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent) {
        super.touchesMoved(touches, with: event)
        guard state == .possible else { return }
        guard decided else {
            if let lone, let finger = fingers.first,
               Self.apart(finger.location(in: view), lone) > GraphTouchRules.slop {
                state = .failed
            }
            return
        }
        guard fingers.count == 2 else { return }
        let now: Double = Self.apart(fingers[0].location(in: view), fingers[1].location(in: view))
        switch GraphReshapeTouch.judge(start, from: from, now: now) {
        case .done: state = .recognized
        case .failed: state = .failed
        case .waiting: break
        }
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent) {
        super.touchesEnded(touches, with: event)
        fingers.removeAll { touches.contains($0) }
        if state == .possible { state = .failed }
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent) {
        super.touchesCancelled(touches, with: event)
        fingers.removeAll { touches.contains($0) }
        if state == .possible { state = .failed }
    }

    override func reset() {
        super.reset()
        start = .none
        decided = false
        from = 0
        fingers = []
        lone = nil
    }

    private static func apart(_ a: CGPoint, _ b: CGPoint) -> Double {
        let dx: Double = Double(a.x - b.x)
        let dy: Double = Double(a.y - b.y)
        return (dx * dx + dy * dy).squareRoot()
    }
}

/// UI tests only: `-SortaDropPreview <title>` rings that idea on the 3D map
/// as the one a held idea would be let go on, for the drop target's render
/// (a test cannot hold a drag still while it takes a picture).
enum GraphReshapePreview {
    static let drop: String? = {
        #if DEBUG
        let args: [String] = ProcessInfo.processInfo.arguments
        guard let at = args.firstIndex(of: "-SortaDropPreview"), at + 1 < args.count else { return nil }
        return args[at + 1]
        #else
        return nil
        #endif
    }()
}
