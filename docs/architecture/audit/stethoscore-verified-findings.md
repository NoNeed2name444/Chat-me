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

## Still to verify

The remaining tags of `stethoscore-unverified-findings.md`: next batches follow in this file.
