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

## Batch 2: [library-ui], [study-a], [study-b], 41 findings (40 confirmed, 1 refuted)

Verified against personal db7fbf3.

| # | tag | finding (short) | verdict | evidence | sev | fix sketch |
|---|---|---|---|---|---|---|
| 29 | library-ui | A lecture-generated quiz opens full screen with no way out | CONFIRMED | `Features/Library/NewSetView.swift:211-215`; `Features/MCQ/MCQQuizView.swift:186-199`; `MCQSummaryView.swift:194,221` | P1 | A Close button in the quiz toolbar while unsaved |
| 30 | library-ui | Delete account fails for this-device-only users with "Please sign in again" | CONFIRMED | `Persistence/AccountStore.swift:225-237`; `Models/Account.swift:50`; `server/worker.js:384-406`; `Shared/AuthAPI.swift:39,156` | P1 | Local-only: sign out and return success |
| 31 | library-ui | Contact us and Report a problem never delivered for local-only accounts; the message blames sign-in | CONFIRMED | `Shared/SupportSender.swift:53-65`; `Shared/LLM/LocalLLMService.swift:115-117` | P1 | Send without a bearer, or mint a device account first |
| 32 | library-ui | A search with question hits but no set names shows "No question sets yet" | CONFIRMED | `Features/Library/LibraryView.swift:581-591` | P2 | Not while searching |
| 33 | library-ui | "Add an audio file" adds an empty New lecture set per tap, even when cancelled | CONFIRMED | `LibraryCategory.swift:215-218`; `NarrateReviewView.swift:144-153,197-199` | P1 | Add the set only when the import produces it |
| 34 | library-ui | Personal build shows a Pro badge, "No subscription yet" and "Manage or cancel" | CONFIRMED | `SubscriptionStore.swift:30`; `AccountView.swift:173-179,267,293` | P2 | "Personal build, everything unlocked"; hide Manage |
| 35 | library-ui | "Progress" opens "Analytics"; "By subject" opens "Progress" | CONFIRMED | `SupportCenter.swift:54-55,83-84`; `AnalyticsView.swift:87`; `StatsView.swift:102` | P2 | Match the titles |
| 36 | library-ui | AI models page says Gemini 3.1 Pro | REFUTED | The page says Gemini 3.5 Flash with Gemma and Cloudflare fallback (`ModelSettingsView.swift:146-152`) | - | Nit: name Flash-Lite too |
| 37 | library-ui | Reminders stay on after permission is refused | CONFIRMED | `StudyReminderSettings.swift:51-59`; `LearnNotifications.swift:36-42` | P2 | Turn the toggle off when refused |
| 38 | library-ui | A link-device error lingers and reappears behind the sheet | CONFIRMED | `LinkDeviceView.swift:108-110,180`; `AccountView.swift:57-61,216-219` | P2 | Clear it on disappear |
| 39 | library-ui | Paywall spins forever with no products | CONFIRMED | same as 122 | P2 | Fixed in c07beb6 |
| 40 | library-ui | "20 from every set" builds 20 in total | CONFIRMED | `StudyCategory.swift:281` vs `:411-413` | P2 | "20 across all your sets" |
| 41 | library-ui | One-up: undoable delete | CONFIRMED as described | `Persistence/Store.swift:421-434`; `LibraryView.swift:744-756` | P2 | A recently-deleted list, purged after 30 days |
| 42 | study-a | Free on-device MCQ writing sends 45,000 characters into 4,096-token models | CONFIRMED | `Shared/MCQPrompt.swift:17`; `MCQGenerator.swift:134,172-174`; `GemmaGenerate.swift:35,54` | P1 | Cap at about 10,000 characters or window per batch |
| 43 | study-a | Practise-mistakes copies keep the ids; an accepted key fix reaches one copy | CONFIRMED | `MCQSummaryView.swift:103-116`; `AccuracyBadge.swift:262-271` | P1 | Apply the fix to every set it fits |
| 44 | study-a | A re-test counts as an extra question and erases the miss from the queues | CONFIRMED | `MCQQuizView.swift:148-155,846-859,1060`; `StudyCategory.swift:440-442`; `StoreInsight.swift:92,167`; `StoreStudy.swift:100-104` | P1 | Do not record re-test answers; leave them out of the score |
| 45 | study-a | Progress stops saving once a re-test is inserted | CONFIRMED | `MCQQuizView.swift:307,314-319` | P1 | Save against the set's own questions |
| 46 | study-a | A mock paper sitting lives only in view state, lost if iOS ends the app | CONFIRMED | `Features/Mock/MockSittingView.swift:12-32,498`; `MockPaperView.swift:50` | P0 (up to three hours of answers) | Persist the sitting on each answer; offer Resume |
| 47 | study-a | Mock ignores negative marking; exam-day advice wrong for NEET-PG | CONFIRMED | `Shared/Exam/MockPaper.swift:217-221`; `ExamCatalog.swift:165,487,503`; `ExamDayKitView.swift:41-42` | P1 | A wrong-answer penalty in the catalog, scored and worded |
| 48 | study-a | Guess-first shows the answer when the term appears twice in the stem | CONFIRMED | `Shared/Learn/Pretest.swift:224-235` | P1 | Blank every whole-word hit |
| 49 | study-a | Timed exam mode is forced-linear; an answer locks on Next | CONFIRMED | `MCQQuizView.swift:991,1020,1035-1043,629` | P2 | Let answers change until finish in exam mode |
| 50 | study-a | The attending hint is cached per id for ever | CONFIRMED | `ExamStore.swift:243`; `AttendingHint.swift:116-131`; `AccuracySchedule.swift:109-120` | P2 | Key by id and a hash of stem and answer |
| 51 | study-a | Cases asks "Did you get it right?" with no way to answer | CONFIRMED | `Features/QA/QACardsView.swift:75,135-160` | P2 | Reword, or add Got it and Missed it |
| 52 | study-a | The AI coverage check is saved per track, not per exam | CONFIRMED | `CoverageView.swift:29-35,105`; `CoverageChecker.swift:38,112`; `CoverageCloudCheck.swift:232-243` | P2 | Key the file by exam too |
| 53 | study-a | A quiz from a search hit shares the set id; Timed, Finish or Try again wipe its resume point | CONFIRMED | `Shared/LibrarySearch.swift:418-425`; `LibraryView.swift:382`; `MCQQuizView.swift:299,337,445,1078` | P1 | Guard each clear with keepsProgress |
| 54 | study-a | A twin due "tomorrow" is due exactly 24 hours later | CONFIRMED | `Shared/Exam/TwinQueue.swift:44-46`; `MCQQuizView.swift:886` | P2 | Start of the next calendar day |
| 55 | study-b | Imported Anki cards get their last rating set to the import time | CONFIRMED | `LibraryImport.swift:320-333`; `ReviewOptions.swift:329`; `FSRS.swift:142-143` | P1 | Rated at due minus interval |
| 56 | study-b | Classic (default) keeps cards in sub-day learning for days, outside the daily limit | CONFIRMED | `ReviewOptions.swift:49,89,115-129`; `AnkiScheduler.swift:18-23,49` | P1 | Graduate Good to at least a day after the 10-minute step |
| 57 | study-b | Talk to the patient never uses Apple's free model although the screen says so | CONFIRMED | `Features/Cases/CaseChatView.swift:60,70`; `LocalLLMService.swift:200-202` | P1 | `writerOrApple()` |
| 58 | study-b | Default OSCE generation sends 12,000 characters to the 4,096-token model, then says nothing reads like a station | CONFIRMED | `Shared/OsceGenerator.swift:21,64,99-104` | P1 | Cap at about 8,000; a context-size message |
| 59 | study-b | Cloze answers containing a colon are not blanked | CONFIRMED | `Features/Anki/AnkiCardFace.swift:150,176` | P1 | Allow single colons in the capture |
| 60 | study-b | Buttons read "back in in 10 min" | CONFIRMED | `AnkiCardFace.swift:381`; `ReviewPlan.swift:154` | P2 | Drop the extra "in" |
| 61 | study-b | The case debrief credits things never said | CONFIRMED | `Shared/CaseSimulator.swift:300-307,348-350` | P1 | Reset before applying the grade |
| 62 | study-b | A scroll swipe over the picture draws a thin cover | CONFIRMED | `OcclusionCoverEditor.swift:135,186-189`; `PictureFromPhotoView.swift:63,287`; `PhotoOcclusion.swift:109` | P1 | Both sides at least the minimum; a minimum drag distance |
| 63 | study-b | Multi-cloze Anki notes become one card with every blank hidden | CONFIRMED | `Shared/ApkgImport.swift:668-697`; `AnkiCardFace.swift:174-177` | P1 | One card per cloze number |
| 64 | study-b | OSCE tidy drops the closing "Wash hands" | CONFIRMED | `Shared/OsceStations.swift:35-40` | P2 | Drop only consecutive repeats |
| 65 | study-b | Reopening after finishing a non-last station lands on its last step | CONFIRMED | `OsceReviewView.swift:91-95,392-416`; `Models/OsceChecklist.swift:30-34` | P2 | Save the next station's first step |
| 66 | study-b | The clue-case hint is cached per case, not per clues shown | CONFIRMED | `Features/Reasoning/ClueCaseView.swift:173-179`; `ExamStore.swift:243` | P2 | Key by case and clues shown |
| 67 | study-b | The occlusion face decodes base64 on every body pass | CONFIRMED | `AnkiCardFace.swift:76-79` | P2 | Decode once per card |
| 68 | study-b | Cancelling Reasoning writing and restarting leaves the new job untrackable | CONFIRMED | `Shared/Reasoning/ReasoningStore.swift:155-197` | P1 | Guard the old task's tail by job id |
| 69 | study-b | Improvement: leech flagging and an on-device rewrite | CONFIRMED gap | `ApkgExporter.swift:195`; `ReviewPlan.swift:10` | P2 | Leeches from lapses, a tile, a rewrite |

## Batch 3: [audio], [ai-client], [server], 36 findings (36 confirmed, 2 with corrections)

Verified against personal db7fbf3. No secret was printed.

| # | tag | finding (short) | verdict | evidence | sev | fix sketch |
|---|---|---|---|---|---|---|
| 70 | audio | Leaving Narrate mid-transcription loses the transcript and deletes the cloud chunks | CONFIRMED | `Features/Narrate/NarrateReviewView.swift:52,147,160`; `Shared/CloudTranscriber.swift:72` | P1 | The importer saves into the store itself |
| 71 | audio | Commute rates Again when nothing was recognised | CONFIRMED | `Shared/Voice/VoiceListener.swift:108-120,156`; `CommuteSession.swift:166,192-200` | P1 | Silence is not rated |
| 72 | audio | The Playgrounds build has no audio background mode | CONFIRMED | `tools/make_swiftpm.py:108,218-222`; `ios/project.yml:104-107` | P1 | An extra plist with the audio mode in the package |
| 73 | audio | A crafted .docx, .pptx or .apkg crashes the app (Zip64 integer overflow) | CONFIRMED | `Shared/Zip.swift:205,209,248`; used by `OfficeIngest.swift`, `ApkgImport.swift`, `LibraryBackup.swift` | P0 | Bound the Zip64 values by the archive size; overflow-safe offsets |
| 74 | audio | Lupus-only terms sent as "terms from this lecture's slides" | CONFIRMED | `Shared/LectureTranscriber.swift:160-167`; `LectureImporter.swift:59-65`; `CloudTranscript.swift:52` | P2 | Drop the fixed list from the slide vocabulary |
| 75 | audio | Transcription language fixed to Egyptian Arabic | CONFIRMED | `CloudTranscript.swift:42-44`; `LectureImporter.swift:35` | P1 | A per-set language |
| 76 | audio | Interruptions unhandled: commute hangs, the player shows playing | CONFIRMED | only `NarrateVoice.swift:103-109` observes; `LecturePlayer.swift:82-142`; `VoiceSpeaker.swift:118-122` | P1 | Observe interruptions in the player, commute and speaker |
| 77 | audio | Headphone unplug and AirPods loss unhandled | CONFIRMED | no route-change observer anywhere | P2 | Pause on old device unavailable |
| 78 | audio | Opening a Narrate set stops music, which never resumes | CONFIRMED | `NarrateReviewView.swift:207`; `LecturePlayer.swift:65,127`; `NowPlaying.swift:17-18,57-64` | P1 | Activate on play; deactivate with notify-others |
| 79 | audio | The voice session prefers Bluetooth HFP | CONFIRMED | `VoiceAccess.swift:94-97`; `CommuteSession.swift:124` | P2 | A2DP out, phone mic in |
| 80 | audio | Replacing a recording deletes the old one before the copy | CONFIRMED | `Shared/LectureAudio.swift:71-72` | P0 | Copy beside it first, then swap |
| 81 | audio | Recordings sit in Documents, inside the iCloud backup | CONFIRMED | `LectureAudio.swift:16-17` vs `SourceFiles.swift:28`, `BlobCache.swift:33` | P2 | Exclude the folder from backup |
| 82 | audio | "N cards from this page" ignores which lecture | CONFIRMED | `Features/Sources/SourcePageReader.swift:66-72`; `SourceSearch.swift:106-131` | P2 | Resolve the citation to its source |
| 83 | audio | Improvement: read PowerPoint speaker notes | CONFIRMED (not done) | `Shared/PptxText.swift:13` | P2 | Read the notes slides |
| 84 | audio | Improvement: SpeechAnalyzer on iOS 26 | CONFIRMED (not done) | `LectureTranscriber.swift:119-127` | P2 | An iOS 26 path |
| 85 | ai-client | Free on-device MCQ and OSCE send 45,000 characters into a 4,096-token context | CONFIRMED | same as 42 and 58 | P1 | Cap and window |
| 86 | ai-client | `isPro` hard-coded true in every build | CONFIRMED | `Shared/SubscriptionStore.swift:30` | P1 | True only in the personal build |
| 87 | ai-client | Busy voters burn the day's allowance and the global ceiling | CONFIRMED | `server/accuracy.js:286,296-316`; `server/ai.js:1086-1094` | P1 | Charge after the vote, only when ballots came back |
| 88 | ai-client | Local and hosted writing lose everything on one failed batch | CONFIRMED | `Shared/LLM/LectureWriter.swift:71,188` | P1 | Count the failure and continue |
| 89 | ai-client | Anki, Cases and Textbook checks read the first part and say "Checked" | CONFIRMED | `LectureWriterSection.swift:462-468`; `AccuracyChecker.swift:24` | P2 | Check per slice, or say how much was checked |
| 90 | ai-client | MCQ generation reads only the first 40,000 to 45,000 characters | CONFIRMED | `MCQGenerator.swift:134`; `MedicalGenerate.swift:18,32,60-61` | P1 | Rotate windows per batch |
| 91 | ai-client | Generation-time verdicts thrown away; items checked again | CONFIRMED | `AccuracyChecker.swift:125-131`; `AccuracyStore.swift:215-229`; `AccuracySchedule.swift:45` | P2 | Record the verdicts |
| 92 | ai-client | Study Lens marks the wrong option | CONFIRMED | `Shared/LLM/LLMParsing.swift:453-459`; `Shared/Lens/LensAnswer.swift:195,204` | P1 | Letters first; numbers one-based; the longest contained option |
| 93 | ai-client | A failed result fetch is collected only after a relaunch | CONFIRMED | `Shared/LLM/CloudJobs.swift:221,240,269`; `CloudJobCollector.swift:96` | P1 | Retry; clear the launch on giving up |
| 94 | ai-client | A new generation cancels the running one and deletes its cloud job | CONFIRMED | `Shared/GenerationCenter.swift:32,67-73`; `CloudJobs.swift:269-273` | P1 | Refuse while one runs, or keep the cloud job |
| 95 | ai-client | Verified after one free vote with no source | CONFIRMED | `Shared/Accuracy/AccuracyModel.swift:116-137,160-196,260` | P1 | At least two of three voters for Verified |
| 96 | ai-client | A unit-less normal range is compared only with the US unit | CONFIRMED | `Shared/Accuracy/AccuracyRules.swift:61-67,332-363` | P1 | Any unit's reference passes when none is given |
| 97 | server | One vote is Verified, cached for everyone for a year | CONFIRMED | `server/accuracy.js:296-330`; `accuracy-model.js:28,52,60,86` | P1 | Write a verdict only with two votes |
| 98 | server | No guard on D1's 100,000 rows written a day | CONFIRMED (the guard is absent; the magnitude of a few first syncs is not verifiable) | `server/sync.js:90,213-241` | P0 | A daily rows-written counter before each batch, headroom kept for sign-in |
| 99 | server | Free device accounts can fill D1 through report, support and diagnostics | CONFIRMED with correction: per-row and per-account caps exist; no global cap; two tables never pruned | `server/accuracy.js:345-346`; `support.js:14-29`; `diagnostics.js:39-46,375-381`; `pair.js:14` | P0 | Global daily ceilings; nightly pruning |
| 100 | server | A lapsed Apple check is never cached; a fake id costs two Apple calls and a write per request | CONFIRMED | `server/ai.js:1103-1108,1181,1194-1196,1222-1228` | P1 | Honour the recheck interval before asking Apple |
| 101 | server | Counter and cache tables are never pruned | CONFIRMED | `worker.js:215-219`; `schema.sql:111-178` | P2 | Nightly deletes by age |
| 102 | server | Blob upload reads a chunked body with no limit | CONFIRMED | `server/sync.js:320-323`; `worker.js:95-131` | P2 | Require a length, or a bounded reader |
| 103 | server | The Apple sign-in nonce gives no replay protection | CONFIRMED | `worker.js:263-265`; `tokens.js:185-188` | P2 | Server-issued, single-use nonces |
| 104 | server | Evidence relies on the edge cache, possibly a no-op on workers.dev; openFDA without a key | CONFIRMED (code); the no-op claim UNKNOWN | `server/evidence.js:61-74,117` | P2 | A key and a D1 or KV cache |
| 105 | server | A deleted account's Apple or Google subject stays in released_tokens | CONFIRMED | `worker.js:452-455` | P2 | Prune by age, or store a hash |

## The P0 list, all batches

| # | finding | state |
|---|---|---|
| 2 | Unreadable library taken for an empty one | Fixed in db7fbf3; a regression in it fixed in 0f9925f |
| 16 | Failed write taken for a save | Fixed in db7fbf3 and 0f9925f |
| 73 | Zip64 overflow crash on a crafted file | Next |
| 80 | Replacing a recording deletes the old one first | Next |
| 46 | Mock paper sitting lost if iOS ends the app | Next |
| 98 | No guard on D1's daily write ceiling | Server; needs a deploy, which the owner approves |
| 99 | No global cap or pruning on report and support tables | Server; needs a deploy, which the owner approves |

## Coverage

All 127 findings are verified: 123 confirmed (some in part), 2 refuted (#20, #36), 2 unknown (#9 needs a device, #104's edge-cache claim needs a live test).
