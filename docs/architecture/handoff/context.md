# Stethoscore handoff context, 2026-10-01, 5:00 AM Cairo

Session: https://claude.ai/code/session_014rUkUW7dRK6Mox54Ygz44U.

## Owner rules
- iPhone and iPad only, no computer steps; Mac work runs in CI.
- Never idle-wait; schedule check-ins. Make sensible choices without asking and say so. Cairo time (UTC+3), AM/PM.
- Never paste tokens or keys. Free tiers only; paid AI only for Pro subscribers after launch.
- Branches: personal plus the session branch (this one: claude/new-session-eskvv8, all four repos); preview/graph for UI tests; ci/launch for the launch matrix; main only at the end, minus the owner-only bits behind PersonalBuild.isOn. Never force-push. No PRs unless asked.
- Deliverable: the Swift Playgrounds zip from tools/make_swiftpm.py ("Stethoscore Personal", com.cramdown.personal). Groin_Hernia.pdf and owner-claim.txt go only in the zip, never committed; not received this session.
- Never propose: Modal, a Whisper fallback, GEMINI_API_KEY in the app, Gemini 3.6 Flash, challenge-a-friend, ranks or leaderboards, a personal self-learning model.

## Versions
- Stethoscore (formerly Red Pen, CramDown, Vignette): SwiftUI and SceneKit, iOS 26; red-pen-ios personal 9892ecf; 104,000 lines of Swift in the package. Backend: Cloudflare Worker in server/ with D1 redpen-auth, live at 3f11f51 since 12:08 AM Cairo, 1 Oct.
- AI: free chain (Gemini 3.5 Flash and Flash-Lite, Gemma 4 31B, Workers AI) plus Apple on-device. Task 3 added a Pro-only Claude Opus 5.5 lane (04c1fe0), inert until ANTHROPIC_API_KEY is a Worker secret and Pro money covers it. Transcription stays on Gemini 3.5 Flash.
- Verification layer: Chat-me medical-verifier-v0.1-commercial-safe at 2f4fd4e: a deterministic curriculum-fidelity and source-integrity engine, 170 offline tests, no auth, not the §8 pipeline (0 verified, 3 partial, 2 missing, 5 divergent stages).

## Task state
- Task 0: done; icon rebuilt from the owner's image (707763a).
- Tasks 1, 1b, 1c: complete; ten outputs in Chat-me docs/architecture/research. Verdicts: §22a Jev in specific layers only (Layer 7 oath check, card/MCQ part of Layer 6); §22d question bank yes with restrictions; §22f: §22e not sound as written; §22g: no LangGraph now; §20: base case misses $4,000 in month 1, about $58,800 in year 1, Egypt regional pricing; §23a: one shared page template. §22c and §22d are under their word limits; §17 is 2,583 words against 2,400, every source kept.
- Task 2 build recovery: below.
- Task 3: done in 04c1fe0 (server/ai.js and tests, wrangler.toml, ModelSettingsView); its commit message is the migration log; tests green; live.
- Task 4: done, docs/architecture/audit/task4-chat-me-audit.md.
- Tasks 5 to 5d: gated on the verdicts, not started; STOP holds until the owner says go.

## Task 2, the Playgrounds build
- Building on the iPad (M4 iPad Pro, iPadOS 27.0.1, Swift Playground 4.7) fails since 25 Sep: "Build failed", empty console. Last package that built: 43e5b39 (24 Sep, 36,624 lines); first that failed: 4fced95 (71,748); today's package: 103,916. The blank template builds; a bare zip without the lecture failed.
- Xcode 26.2 and 26.3 compile every variant clean for the device, so the compiler is not the cause; the build service's memory is the leading hypothesis.
- Zips A (no 3D map, 83,500 lines) and B (78,500) failed. Owner decision: at most 36,000 lines in the Playgrounds build; the full app stays in the repo.
- make_swiftpm.py --without chunk (graph3d, lens, analytics, core) drops files and copies stand-ins from tools/playgrounds_stubs (README there). The core: 165 files, 33,806 lines, own shell. swiftpm-check.yml builds full and core on every push to personal; swiftpm-launch.yml on ci/launch launches them in iPhone and iPad simulators; the core compiled and launched alive.
- Sent 30 Sep 11:57 PM Cairo: zip 1, the core (3f11f51); zip 2, the 24 Sep app (43e5b39, 36,693 lines) as a control. Result pending. 1 builds: size is the cause, add chunks back one zip at a time (tools/playgrounds_cut.py lists the names a stand-in needs). 1 fails, 2 builds: bisect between them. Both fail: the iPad changed; compare the manifest with the blank template, try a multi-target package.
- app-build.yml was red on one design-preview test, testHoldForOptions: the scripted hold set SwiftUI state inside updateUIView, where it is dropped; 9892ecf defers it (same idiom as apply's); its run was pending at handoff. The rest of the UI suite passes.

## Repositories
- red-pen-ios: personal 9892ecf = session branch ec6b080; ci/launch b97434d; preview/graph and claude/continue-session-t3r79j at c347755 (stale); main 9eb5430 (23 Sep). Unmerged: gaps/{a11y, wardpocket, audio, saveideas, wardround, l10n, onboarding} and wip/{neuron-circuit-redesign, growth-p0-1, growth-p0-2a} (unverified).
- Chat-me: session branch with docs/architecture; main f1bfe1f. red-pen-transcribe and Claude-Code untouched.
- Touched: tools/ (make_swiftpm.py, playgrounds_stubs, playgrounds_cut.py), the three workflows, Graph3DView.swift, ios/UITests, icon assets, the Task 3 files, Chat-me docs/architecture.

## Fail-safe, LangGraph, folders, 3D
- Fail-safe: researched (§22f); designed as a protocol: none; implemented ad hoc: crash reporter and triage, AI health check, neuron reservation and true-up, backups with set-aside copies, undo, sync queue, retries and timeouts (7 server, 16 app files), no circuit breakers or backoff; tested: 46 Swift suites, 11 server test files, no failure injection; untested: the fail-open behaviours in the findings list.
- LangGraph: nothing exists in any repo; verdict: not now.
- Folders: §3c tree agents/, tools/, orchestration/, prompts/, api/, governance/, evals/, tests/, docs/architecture/; only docs/architecture/ exists, in Chat-me; Stethoscore unchanged; migration default pending the owner.
- 3D (in-app Ideas map): Space shipped; Neurons and Circuit v1 shipped, their redesigns (axon-classic, photonic) unverified WIP. Standalone 3D app (Task 10, §3d): nothing yet; theme images received.

## Blockers and pending
- Pending: the result on zips 1 and 2, Groin_Hernia.pdf and owner-claim.txt, the go for Tasks 5 to 5d and the §3c migration.
- The 127 unverified app audit findings: Chat-me docs/architecture/audit/stethoscore-unverified-findings.md; verify before fixing.

## Outdated plan rules
- §25 assumes one Playgrounds package holds the whole app; it cannot. §4's migration is an inert lane, not a swap. §22e should follow the §22f brief. §22g tasks and dashboards are moot. The app sections still say Red Pen and CramDown.

## Do not re-derive
- The size history; Xcode 26.0.1 cannot build for device on the runner, 26.2 and 26.3 can; Playground 4.7 is Swift 6 with the iOS 26 SDK; use macos-latest.
- Usage limits reset at 8 PM UTC; use absolute paths.
