# Stethoscore handoff context, 2026-10-01, 7:15 PM Cairo

Session: https://claude.ai/code/session_013TeS5vv11UfjdBc6bVKhj3 (session branch claude/new-session-013tes5v in each repo).

## Owner rules
- iPhone and iPad only, no computer steps; Mac work runs in CI.
- Never idle-wait; schedule check-ins. Make sensible choices without asking and say so. Cairo time (UTC+3), AM/PM.
- Never paste tokens or keys. Free tiers only; paid AI only for Pro subscribers after launch.
- Branches: personal plus the session branch (see above); CI skips claude/** pushes (a copy of personal); preview/graph for UI tests; ci/launch for the launch matrix; main only at the end, minus the owner-only bits behind PersonalBuild.isOn. Never force-push. No PRs unless asked.
- Deliverable: the Swift Playgrounds zip from tools/make_swiftpm.py ("Stethoscore Personal", com.cramdown.personal). Groin_Hernia.pdf and owner-claim.txt go only in the zip, never committed; not received this session.
- Never propose: Modal, Whisper fallback, GEMINI_API_KEY in the app, Gemini 3.6 Flash, challenge-a-friend, ranks or leaderboards, a personal self-learning model.
- From the owner's own brief (Claude-Code claude/swiftui-project-setup-m0r481 CLAUDE.md): you cannot see the app running, so after each build tell the owner exactly what to check on screen; explain problems in plain terms; keep token use per message low and script repeated procedures.
- Order of work (1 Oct): what can be checked locally (Linux Swift at /opt/swift, node, Python) first; anything that changes how the app looks last, batched into Mac runs. Decisions build on the research (docs/architecture/research) and the verification layer. Check locally (red-pen-ios tools/preflight.sh), let CI confirm; read CI with tools/ci_status.py; no multi-agent workflows that wait on Mac CI.
- Answered (1 Oct): the owner has no Apple Developer account (the Claude-Code brief was wrong), so no TestFlight; the Swift Playgrounds zip stays the way the app reaches the iPad, within the size limit.

## Versions
- Stethoscore (formerly Red Pen, CramDown): SwiftUI and SceneKit, iOS 26; red-pen-ios personal d5548c1; 104,000+ lines of Swift in the package. Backend: Cloudflare Worker in server/ with D1 redpen-auth, live at 3f11f51 since 12:08 AM Cairo, 1 Oct. NOT deployed: the switches, breakers, staged accuracy and claim gate merged since (tests green); deploy waits for the owner's word.
- AI: free chain (Gemini 3.5 Flash and Flash-Lite, Gemma 4 31B, Workers AI) plus Apple on-device. Task 3 added a Pro-only Claude Opus 5.5 lane (04c1fe0), inert until ANTHROPIC_API_KEY is a Worker secret and Pro money covers it. Transcription stays on Gemini 3.5 Flash.
- Verification layer: Chat-me personal d504978, moved into the §3c folders (api/, agents/, orchestration/, governance/, tools/, evals/, tests/, clients/ios), from medical-verifier-v0.1-commercial-safe: a deterministic curriculum-fidelity and source-integrity engine, 170 offline tests, no auth, not the §8 pipeline (0 verified, 3 partial, 2 missing, 5 divergent stages).

## Task state
- Task 0: done; icon rebuilt from the owner's image.
- Tasks 1, 1b, 1c: complete; ten outputs in Chat-me docs/architecture/research. Verdicts: §22a Jev in specific layers only (Layer 7 oath check, card/MCQ part of Layer 6); §22d question bank yes with restrictions; §22f: §22e not sound as written; §22g: no LangGraph now; §20: base case misses $4,000 in month 1, about $58,800 in year 1, Egypt regional pricing; §23a: one shared page template. §22c and §22d are under their word limits; §17 is 2,583 words against 2,400, every source kept.
- Task 2 build recovery: below.
- Task 3: done in 04c1fe0 (server/ai.js and tests, wrangler.toml, ModelSettingsView); its commit message is the migration log; tests green; live.
- Task 4: done, docs/architecture/audit/task4-chat-me-audit.md.
- Tasks 5 to 5d and the §3c migration (plan: red-pen-ios docs/architecture/plans/tasks-5-to-5d.md, kept current):
  - 5: steps 1-3 done; 4-5 wait for a Jev key and Pro money.
  - 5b: done in code. Licence allowlist, fetcher, pipeline (tools/question-bank/pipeline.mjs, bank.mjs; prompts/question-bank; tests/question-bank). Pilot runs on a push to qbank/run; results on question-bank-pilot/<run id>; nothing student-facing until a person reviews it.
  - 5c: done (data P0s/P1s; retries #88 #93 #94; switches.js and breakers.js; fault injection server/tests/faults.test.mjs and the app's NetworkFaults.swift with the faults suite).
  - 5d: steps 1 and 3 done (staged accuracy, claim gate); step 2 needs no change.
  - §3c: done in both repositories.

## Task 2, the Playgrounds build
- Building on the iPad (M4 iPad Pro, iPadOS 27.0.1, Swift Playground 4.7) fails since 25 Sep: "Build failed", empty console. Last package that built: 43e5b39 (24 Sep, 36,624 lines); first that failed: 4fced95 (71,748); today's package: 103,916. The blank template builds; a bare zip without the lecture failed.
- Xcode 26.2 and 26.3 compile every variant clean for the device, so the compiler is not the cause; the build service's memory is the leading hypothesis.
- Zips A (no 3D map, 83,500 lines) and B (78,500) failed. Owner decision: at most 36,000 lines in the Playgrounds build; the full app stays in the repo.
- make_swiftpm.py --without chunk (graph3d, lens, analytics, core) drops files and copies stand-ins from tools/playgrounds_stubs (README there). The core: 165 files, 33,806 lines, own shell. swiftpm-check.yml builds full and core on every push to personal; swiftpm-launch.yml on ci/launch launches them in iPhone and iPad simulators; the core compiled and launched alive.
- Sent 30 Sep 11:57 PM Cairo: zip 1, the core (3f11f51); zip 2, the 24 Sep app (43e5b39, 36,693 lines) as a control. Result pending. 1 builds: size is the cause, add chunks back one zip at a time (tools/playgrounds_cut.py lists the names a stand-in needs). 1 fails, 2 builds: bisect between them. Both fail: the iPad changed; compare the manifest with the blank template, try a multi-target package.
- Owner results: core built, core1 built, core2 crashed on the acid-base lecture (PronunciationLibrary missing; fixed and resent) and cloze answers now show in place. Next zip after the Ward Round UI lands.

## Repositories
- red-pen-ios: personal = session branch d5548c1. Merged today: the plan-5* branches, the ports from red-pen-transcribe and Claude-Code (design/port-*), the owner lanes stacked on design/lanes (gaps/* and wip/growth-*), the design-preview harness. Open: design/ward-round (Ward Round foundation, waiting for its Mac build and shots/ward-round), design/perf-core (100k theme engine, Linux-tested), wip/neuron-circuit-redesign (unverified). Linux CI: swift-tests runs most suites in a swift:6.4 container; macOS only for Apple-framework suites, app-build, screenshots.
- Chat-me: session branch with docs/architecture; main f1bfe1f; attached with write access on 1 Oct. Four repositories in all: red-pen-ios, Chat-me, red-pen-transcribe (private, transcription pipeline, about 20 branches) and Claude-Code (public, a README and one SwiftUI setup branch).
- Touched: tools/ (make_swiftpm.py, playgrounds_stubs, playgrounds_cut.py), the three workflows, Graph3DView.swift, ios/UITests, icon assets, the Task 3 files, Chat-me docs/architecture.

## Fail-safe, LangGraph, folders, 3D
- Fail-safe: §22f reduced protocol built (Task 5c): data integrity, retries and timeouts, kill switches (STETHOSCORE_OFF) and breakers, fault injection on both sides.
- LangGraph: nothing exists in any repo; verdict: not now.
- Folders: §3c done. red-pen-ios uses docs/architecture/, governance/, prompts/, tests/; the app (ios/) and Worker (server/) stay put. Chat-me holds the verifier in the full tree.
- Design targets: Ward Round is the whole app's design language (rollout plan docs/design/ward-round-rollout.md; foundation in Shared/Ward on design/ward-round). Then space shapes, neurons/circuit, the 100k performance theme (engine on design/perf-core; renderer next), the layered icon.
- 3D (in-app Ideas map): Space shipped; Neurons and Circuit v1 shipped, their redesigns (axon-classic, photonic) unverified WIP. Standalone 3D app (Task 10, §3d): nothing yet; theme images received.

## Decisions, 1 Oct
- The owner's design targets (Ward Round look and palette, space node styles, neurons and circuit vignettes, a performance theme for 100,000 nodes, the icon in layers) are written down in red-pen-ios docs/design/targets-2026-10-01.md; the images are in no repository.
- No Rank card, no Clerk badge, no XP; the app stays Stethoscore (the mock's "Mnemonia" is out).
- The 3D work targets the app's Ideas map first; the performance renderer is kept free of Stethoscore types so Task 10's standalone app can take it.
- The Playgrounds zips go in steps: core (built on the iPad), core1 (built), core2 (sent; the lecture crash fixed and cloze answers in place).

## Blockers and pending
- Pending from the owner: the word to deploy the Worker; Groin_Hernia.pdf and owner-claim.txt (ask when the final zip is near); the launch splash colour (midnight kept for now).
- The 127 unverified app audit findings (docs/architecture/audit/stethoscore-unverified-findings.md): not yet verified; next local work. Verify against the code before fixing; mark each verified/false/fixed in that file.

## Outdated plan rules
- §25 assumes one Playgrounds package holds the whole app; it cannot. §4's migration is an inert lane, not a swap. §22e should follow the §22f brief. §22g tasks and dashboards are moot. The app sections still say Red Pen and CramDown.

## Do not re-derive
- The size history; Xcode 26.0.1 cannot build for device on the runner, 26.2 and 26.3 can; Playground 4.7 is Swift 6 with the iOS 26 SDK; use macos-latest.
- Usage limits reset at 8 PM UTC; use absolute paths.
