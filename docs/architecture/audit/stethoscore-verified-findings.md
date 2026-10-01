# Stethoscore app audit: the findings verified against the code

Started 1 October 2026 from `stethoscore-unverified-findings.md` (127 carried-over claims). Each finding was read against red-pen-ios `personal` at 84e8b36 before a verdict; nothing below is a guess. Verdicts: CONFIRMED (the code does what the finding says), REFUTED (it does not; what it does instead), UNKNOWN (cannot be settled without a device). Severity: P0 data loss, crash or security; P1 wrong behaviour the student sees; P2 cosmetic or rare. Paths are under `ios/RedPen/` unless noted.

## Fixed

| # | tag | finding | fixed in | how |
|---|---|---|---|---|
| 2 | launch | A library file that exists but cannot be read (a launch before the first unlock after a restart) was treated as an empty library with `readWhole` true, so the next save overwrote the real file | db7fbf3 | `Store.load` marks a present-but-unreadable file; `write` holds that file back; the personal-build seed and sync hold off; `reloadIfUnread` reads it again when the app comes to the front with nothing changed in memory |
| 16 | data | A failed library write was dropped with `try?`, the dirty flag already cleared, and `flushed()` returned as if the bytes were on disk, so sync advanced its cursor and a finished cloud job was forgotten on the server | db7fbf3 | `WriteJob.run` reports which files did not land, they stay dirty, `flushed()` returns false, the sync run stops before any bookmark moves (plain message), the cloud job's server copy is kept; `StoreFiles` suite in Swift tests |

## Batch 1: [launch] and [data], 28 findings (26 confirmed, 1 refuted, 1 unknown)

| # | tag | finding (short) | verdict | evidence | sev | fix sketch |
|---|---|---|---|---|---|---|
| 1 | launch | Whole library (base64 pictures) decoded synchronously on the main thread in App.init | CONFIRMED | `RedPenApp.swift:44` → `Store()`; `Persistence/Store.swift:117-119` (`load()` in init, class is `@MainActor`), `:143-146`; `Models/StudySet.swift:60` `images: [String]` | P1 | Load on a background task and publish into `library` when done, splash until then (or decode images lazily) |
| 2 | launch | Unreadable-but-present library file treated as empty | CONFIRMED | `Store.swift:143` (no else path); `SourceFiles.swift:356`, `SyncEngine.swift:337` trust `readWhole`; `Store.swift:300-303` next write overwrites | P0 | Fixed, above |
| 3 | launch | Files opened before the library shows go to the inbox after 2.5 s over sign-in, terms or exam; decks lose their preview | CONFIRMED | `Shared/ImportRouter.swift:50` `graceSeconds = 2.5`, `:95-104`; `AppIntentsRouting.swift:213` applied on the root `Group` (`RedPenApp.swift:241`), not gated by `ready` (`:196`) | P1 | Keep files in `waiting` while the library is not showing; start the timer once ready |
| 4 | launch | Spotlight marks sets indexed before the index call finishes; a cancelled run loses adds and deletes | CONFIRMED | `Shared/AppIntents.swift:214-225`, `:258` | P2 | Save `indexed` after `send` completes and only when not cancelled; merge pending changes into the next run |
| 5 | launch | Privacy manifest reason 7D9E.1 but free disk sent in automatic reports | CONFIRMED | `ios/AppResources/PrivacyInfo.xcprivacy:201-204`; `Diagnostics.swift:26-28`; `DiagnosticsPlatform.swift:63-70`, `:181-190`; `DiagnosticsQueue.swift:197` | P2 | Zero `freeDiskGB` unless the report is the user's own |
| 6 | launch | The minute sync loop checks a stale `scenePhase` | CONFIRMED | `RedPenApp.swift:159-164` `.task` without an id | P2 | `.task(id: phase)` or read the application state inside the loop |
| 7 | launch | Reminder and "ready" notification taps go nowhere | CONFIRMED | `Shared/BackgroundWork.swift:116-121`, `:83-91`; `CloudJobCollector.swift:308-315`; `LearnNotifications.swift:135-151` `default: break` | P2 | Put a kind in `userInfo` and route to Due today or the set |
| 8 | launch | App lock may ask for Face ID again straight after cancel | CONFIRMED (static) | `Platform/AppLock.swift:81-89`, `:129-131` | P2 | Track a declined state; re-prompt only if not declined |
| 9 | launch | Run marker never clears on termination; a switcher quit reported as a crash | UNKNOWN | `Diagnostics.swift:156-162`, `:220-228`; `DiagnosticsQueue.swift:142-143`; no `willTerminate` handler; needs a device test | - | Observe `willTerminateNotification` and flush anyway |
| 10 | launch | Crash reports cannot tell Playgrounds from Xcode or one package drop from the next | CONFIRMED (personal build) | `DiagnosticsPlatform.swift:197-205`, `:159`; `tools/make_swiftpm.py` fixes version "1.0"/"1" | P2 | Append `-playgrounds` to the flavour; set the bundle version from the commit |
| 11 | launch | make_swiftpm.py's assertion can never fail; patches unchecked | CONFIRMED | `tools/make_swiftpm.py:190` `or True`; `:154-158` unchecked replaces | P2 | Drop `or True`; assert each anchor matched once |
| 12 | launch | One-up: Face ID lock (local network, ProMotion) via package capabilities, gated at runtime | CONFIRMED as described | `make_swiftpm.py:218-222`; `AppLock.swift:2,21,52` `!SWIFT_PACKAGE` | - | `.faceID(purposeString:)` capability; gate on `canEvaluatePolicy` |
| 13 | launch | One-up: Playgrounds CI builds but never launches | CONFIRMED for swiftpm-check.yml; swiftpm-launch.yml on ci/launch does launch | - | Done on ci/launch |
| 14 | data | Pictures on imported Anki basic and cloze cards stored but never shown | CONFIRMED | `Shared/ApkgImport.swift:764-771`; `Features/Anki/AnkiCardFace.swift:76-82` only `.occlusion` | P1 | Show the plain image for `.qa` and `.cloze` when `imageIndex` is set |
| 15 | data | Restoring a backup while sync pulls can duplicate a set id | CONFIRMED | `Shared/LibraryBackupRunner.swift:216-234`; `Store.swift:589-593`; `StoreSync.swift:365-373` | P1 | Re-plan on the main actor after the await, or filter by ids now present |
| 16 | data | Failed library write silently dropped; sync cursor still advances | CONFIRMED | `Store.swift:302-303`, `:775-781`, `:271-276`; `SyncEngine.swift:365-368` | P0 | Fixed, above |
| 17 | data | Library file holds every picture as base64, so a rename rewrites it all | CONFIRMED (mechanism; the measured numbers UNKNOWN) | `StudySet.swift:58-60`; `BlobRefs.swift:17`; `Store.swift:25`, `:362-368`, `:286-290` (encoding is on the write queue, not main) | P1 | Store pictures as `blob:` refs on disk (BlobCache exists) |
| 18 | data | Each rating re-encodes the whole schedule on main; each sync re-encodes and hashes every deck's schedule | CONFIRMED | `Persistence/ReviewStore.swift:54-57`, `:134`; `SyncPush.swift:155-159`; `SyncDocuments.swift:176-190`; `SyncMerge.swift:114-129` | P2 | Debounce and encode off main; cache a per-deck stamp and skip unchanged decks |
| 19 | data | Combining textbooks mis-points figures; combining drops lecture sources | CONFIRMED | `Store.swift:514-515`, `:500`; `Shared/BookFigures.swift:71` | P1 | Rebase `image:N` references; append `m.sources` |
| 20 | data | The Mistakes practice set loses every picture | REFUTED | `Persistence/StoreStudy.swift:157-180` copies images and rebases indexes; both builders use it | - | - |
| 21 | data | Anki export drops pictures on basic and cloze cards and exports all cards as new | CONFIRMED | `Shared/ApkgExporter.swift:218-230`, `:263-264`, `:202`; `LibraryBackupRunner.swift:316-324` | P1 | Emit `<img>` for `.qa` and `.cloze`; pass the review records and write the schedule columns |
| 22 | data | A push-wins review conflict never merges the other device's ratings | CONFIRMED | `SyncEngine.swift:385-396`, `:531` (`keepCopy` ignores `.review`) | P1 | Merge the remote review records before `keepCopy` |
| 23 | data | A schedule too big for the server is silently never synced | CONFIRMED | `SyncPush.swift:37-41`; `SyncEngine.swift:300-306`; `SyncRules.swift:15` | P1 | `problems()` also checks the review document id |
| 24 | data | Backup restore throws away unreadable sets and the backup's own unread; the report says nothing lost | CONFIRMED | `Shared/LibraryBackup.swift:111-118`, `:212-275`; `LibraryBackupRunner.swift:32-55` | P1 | Count skipped sets, carry `unread` through, say so in the summary |
| 25 | data | Set-aside recovery copies unreachable; ReviewStore and NoteStore fail all-or-nothing | CONFIRMED | `Store.swift:225-238`; `ReviewStore.swift:44-50`; `NoteStore.swift:126-133`; `ios/project.yml:126` | P2 | `UIFileSharingEnabled` or a recovery-files row; decode records lossily |
| 26 | data | Improvement: derive card ids from the Anki note GUID | CONFIRMED (current behaviour) | `ApkgImport.swift:569`, `:649`, `:691` | - | Select `n.guid`; id = UUID v5 of guid and ord |
| 27 | data | Improvement: sync study progress and Ideas notes | CONFIRMED (not synced) | `Models/SyncDoc.swift:8-13`; `Store.swift:80-85` | - | `.study` and `.note` kinds, reuse `mergeStudy` and `NoteStore.restore` |
| 28 | data | Improvement: Recently deleted for sets | CONFIRMED (delete is immediate) | `Store.swift:347-360`; `LibrarySheets.swift:57` | - | A deleted list with a 30-day stamp; defer the recording's removal |


## Batch 4: [design] and [map], 22 findings (22 confirmed, 2 of them in part, 0 refuted)

Verified against personal db7fbf3. Contrast ratios computed with the WCAG formula from the RGB literals in the code.

| # | tag | finding (short) | verdict | evidence | sev | fix sketch |
|---|---|---|---|---|---|---|
| 106 | map | Idea Board recomputes every link, O(n squared), on every frame of pan, pinch or drag | CONFIRMED | `Features/Notes/IdeaBoardView.swift:100-104` inside the GeometryReader body; `Persistence/NoteStore.swift:325-338`, `:299-305` | P2 | Cache edges and lanes in `@State`, refreshed on `notes.notes` change; build `known` once |
| 107 | map | Signature rebuilt on each store change; whole scene rebuilt on each editor save | CONFIRMED in part | `Graph3DView.swift:724-754`, `:135`; the per-save rebuild is REFUTED: the id holds title, kind, folder and size level only (`NoteEditorView.swift:117`, `:309`) | P2 | A `graphVersion` bumped only on title, folder, link or level change |
| 108 | map | Tapping an aliased wiki link in Read mode creates a junk page | CONFIRMED | `NoteMarkdown.swift:155-168`, `:24-26`, `:170-173`; `NoteEditorView.swift:330-335`; `NoteStore.swift:349` splits correctly | P1 | Split on the bar in `wikiLinksAsMarkdown` as `wikiTitles` does |
| 109 | map | A still map keeps rendering at 60 fps; the map renders under the editor sheet | CONFIRMED | `Graph3DView.swift:1311-1312` `rendersContinuously = true` never cleared, `:1523`, `:1796-1798`, `:1823`, `:1782-1785`, `:1383`, `:151` | P1 | `rendersContinuously = lively`; `isPlaying` false while the sheet is up |
| 110 | map | Every rebuild tessellates an SCNText per body on the main thread; at most four names shown | CONFIRMED | `Graph3DView.swift:1156-1180`, `:1013`, `:1029`; `GraphUniverseScene.swift:101`; `GraphThemeScene.swift:180`; `GraphMotion.swift:1097-1100` | P2 | Lazy labels, made the first time a name is wanted |
| 111 | map | Touch, hover and tap wait for the render step because the sim holds its lock through SceneKit writes | CONFIRMED | `GraphMotion.swift:1234-1236`, `:1312-1405`, `:787-1043` (tilt and shaders are outside the lock) | P2 | Integrate into a snapshot under the lock, write the nodes after |
| 112 | map | A probe that fails once turns shaders off for the life of the process | CONFIRMED | `GraphLook.swift:317`, `:327-359`; same in `GraphCircuitLook.swift:787`, `GraphNodeShaders.swift:1197`, `GraphNeuronLook.swift:873`; probes run in the rebuild's detached task (`Graph3DView.swift:791-792`) | P2 | Cache only when the app is active at probe time |
| 113 | map | Not re-fitted when the view's size changes but stays upright or wide | CONFIRMED | `Graph3DView.swift:1683-1689`; only `inset(to:)` `:1659-1663` refits | P2 | Track the last size and refit when untouched |
| 114 | map | Superseded force layouts keep running | CONFIRMED | `Graph3DView.swift:786-795`, `:811-817`, `:842-847`; `ForceLayout3D.swift` has no cancellation check | P2 | Cancel the detached job with the task; check `Task.isCancelled` in the loop |
| 115 | map | One-up: link card sets to their source note | CONFIRMED (absent) | `NoteEditorView.swift:447-453`; `Models/StudySet.swift:35-70`; `NoteStore.swift:4-17` | P2 | `sourceNoteId` on StudySet |
| 116 | design | Paywall Terms and Privacy go to redpen.app | CONFIRMED | `Features/Paywall/PaywallView.swift:168-169`; the app's only host is the Worker (`Shared/AuthAPI.swift:22`), which has no terms or privacy route; whether redpen.app resolves is UNKNOWN | P1 | Brand URLs on the Stethoscore site, or serve both from the Worker |
| 117 | design | Default accent still pen red, not Midnight Enamel | CONFIRMED | `RedPenApp.swift:251`, `:285`; the Midnight Enamel colours exist only in `tools/make_icon.py:36-42` and `LaunchSplash.swift:25` | P2 | `Brand.accent` used at both tint sites |
| 118 | design | AccentColor.colorset empty, so accentColor uses fall back to blue and mix with red | CONFIRMED | `Assets.xcassets/AccentColor.colorset/Contents.json` has no colour; 49 `.accentColor` uses; `LibraryView.swift:206` tints the library with it | P2 | Add the colour to the colorset |
| 119 | design | White on amber (Cases) and lavender (Bedtime) buttons fails 3 to 1 | CONFIRMED | `Shared/Theme.swift:17`, `:464`, `:470`; `BedtimeReviewView.swift:39`, `:121`; `CaseChatView.swift:65-309`; amber 2.88, lavender 2.73 | P2 | Ink by tint luminance, or darken the two tints |
| 120 | design | Billing-retry check passes the group name where StoreKit wants the group id | CONFIRMED | `Shared/SubscriptionStore.swift:171`, `:155`, `:174`; `Shared/Entitlement.swift:15`; `RedPen.storekit:13`; masked by `isPro { true }` (`:30`) | P2, P1 once the gate is live | Pass the transaction's subscription group id |
| 121 | design | Server errors still say Vignette Cloud; the app shows them behind a raw HTTP 402 | CONFIRMED in part | `server/ai.js:62`, `:271`, `:1188-1205`; the raw prefix is REFUTED for 402 and 429 (`LLMCore.swift:43-44`), present for other codes (`:46`) | P2 | One brand constant in the server; drop the prefix |
| 122 | design | Paywall spins forever when the App Store returns no products | CONFIRMED | `PaywallView.swift:88-89`; `SubscriptionStore.swift:58-68` sets `trouble` only on a throw; reachable from `AccountView.swift:94` and `ModelSettingsView.swift:77` | P1 | Treat an empty result as trouble and show it |
| 123 | design | Stethoscope logo only in the splash; Brand.swift draws the CramDown mark | CONFIRMED | `LaunchSplash.swift:17-20`, `:49`; `Brand.swift:5-11`, `:44-80`, no callers | P2 | Replace the mark with the icon; show it on Account and Paywall |
| 124 | design | Import help names three retired brands | CONFIRMED | `LibraryImport.swift:39`; shown at `NewSetView.swift:567` | P2 | Name only the current brand |
| 125 | design | No shared radius tokens; like surfaces use 12, 14, 16, 20, 22 | CONFIRMED | 21 of 12, 20 of 20, 18 of 16, 14 of 14, 11 of 18, 9 of 28, 9 of 22, 13 others; no constant in Shared | P2 | A `Radius` enum with three values |
| 126 | design | One-up: exported PDF decks carry no Stethoscore mark | CONFIRMED (absent) | `DeckPDF.swift:155`; `DeckPDFPages.swift:173-183`; `PDFExporter.swift` | P2 | The brand name in the bar and a footer line |
| 127 | design | DeckPalette re-types the six mode tints by hand | CONFIRMED | `DeckPalette.swift:26-34` repeats `Theme.swift:14-19`; DeckPalette is Foundation only | P2 | Derive the SwiftUI tints from DeckPalette |

P1s in this batch: 108, 109, 116, 122 (and 120 once the Pro gate is live). No P0.

## Still to verify

Batches 2 ([library-ui], [study-a], [study-b]) and 3 ([audio], [ai-client], [server]) follow in this file.
