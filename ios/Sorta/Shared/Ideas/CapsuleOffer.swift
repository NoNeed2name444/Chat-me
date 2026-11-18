import Foundation

/// When Sorta offers a capsule (S06d A4, plugins-plan idea 11): quietly,
/// once a month at most. Pure; Android's CapsuleOffer is the same. The
/// offer is due when there are ideas and `interval` has passed since the
/// latest of the first launch with ideas, the last offer and the last
/// capsule saved. Shown once, it is marked offered whatever the person
/// does, so it never comes twice in a month.
enum CapsuleOffer {
    /// Thirty days.
    static let interval: TimeInterval = 30 * 24 * 60 * 60
    /// The three times, in UserDefaults as seconds since 1970 (Android's
    /// settings keep the same keys, in milliseconds).
    static let firstSeenKey = "sorta.capsule.firstSeen"
    static let offeredKey = "sorta.capsule.lastOffered"
    static let savedKey = "sorta.capsule.lastSaved"

    /// `firstSeen` is nil until a launch has seen ideas.
    static func due(now: Date, ideas: Int, firstSeen: Date?, offered: Date?, saved: Date?) -> Bool {
        guard ideas > 0, let firstSeen else { return false }
        let since: Date = [firstSeen, offered, saved].compactMap { $0 }.max() ?? firstSeen
        return now.timeIntervalSince(since) >= interval
    }

    /// A time kept under `key`, or nil.
    static func time(_ key: String, in defaults: UserDefaults = .standard) -> Date? {
        guard defaults.object(forKey: key) != nil else { return nil }
        return Date(timeIntervalSince1970: defaults.double(forKey: key))
    }

    static func setTime(_ date: Date, _ key: String, in defaults: UserDefaults = .standard) {
        defaults.set(date.timeIntervalSince1970, forKey: key)
    }
}
