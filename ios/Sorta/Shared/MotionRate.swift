import QuartzCore

/// The rate everything that moves asks for (the owner, 15 Nov: "since most
/// phones now are 120hz make it 120 fps not 60"): 120 a second, and never
/// under 80 while it moves, on a ProMotion screen. An iPhone goes past 60
/// only with CADisableMinimumFrameDurationOnPhone in Info.plist (project.yml);
/// a 60 Hz screen keeps its 60. Each display link that moves something asks
/// for `moving`, and one with nothing left to move lowers itself
/// (PopOutMotion's idle rate) or stops. The 3D map's SceneKit view asks for
/// the screen's own rate while anything on it moves (GraphPace), and the
/// Performance map's Metal view while it draws, pausing once still
/// (GraphPerfMapView). tools/check_high_refresh.py checks each of them.
enum MotionRate {
    /// A display link's range while it moves.
    static let moving = CAFrameRateRange(minimum: 80, maximum: 120, preferred: 120)
    /// A link that only follows what is drawn (the map's rings) while that
    /// draws at the floor, or under Reduce Motion: the screen's ordinary
    /// 60, never faster.
    static let calm = CAFrameRateRange(minimum: 60, maximum: 60, preferred: 60)

    /// The range for a link that follows what another view draws (the
    /// map's rings): `moving` while that view is fast, else `calm` (the
    /// review, 19 Nov: GraphPace.ringsFast, slow at rest, under a sheet
    /// and with Reduce Motion on).
    static func range(fast: Bool) -> CAFrameRateRange {
        fast ? moving : calm
    }
}
