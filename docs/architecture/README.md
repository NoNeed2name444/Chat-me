# Architecture notes

`docs/architecture/` is the documentation slot of the platform folder contract
(the §3c tree: `agents/`, `tools/`, `orchestration/`, `prompts/`, `api/`,
`governance/`, `evals/`, `tests/`, `docs/architecture/`). The research briefs
and audits the plan calls for live here, one file per protocol, so a decision
can be traced to the evidence it rested on. Each brief was researched with
tools from primary sources, cites a URL for every claim, marks contested points
and says UNKNOWN where nothing could be verified. They are written once; a
brief is re-run only when its inputs change.

## Research briefs (`research/`)

| File | Protocol | Verdict in one line | Date |
|---|---|---|---|
| `16a-dna-brief.md` | §16a, biology as a verification model | The plan's biological analogies checked against primary sources (repair, proofreading, immune tolerance), with the numbers that could and could not be verified; the mapping to verification stages is kept where the biology actually supports it. | 2026-09-30 |
| `17-islamic-verification-brief.md` | §17, Islamic sciences of verification | Isnad, rijal, the five conditions, matn criticism, 'illal, tarjih, tahqiq and the fail-safe principles (tawaqquf, ihtiyat) mapped to the verification stages; design principles for the layer; the Jev mappings written as conditional on the §22a verdict. Note: 2,843 words against the protocol's 2,400 limit; trim on the next revision, do not re-run. | 2026-09-30 |
| `22a-jev-brief.md` | §22a, Jev (TypeSafe AI) | Better in specific layers, not across the board: deploy only Layer 7 (fail-closed oath check) and the card/MCQ part of Layer 6; shadow-pilot Layers 3 and 4; reject Layers 1 and 8. Paid per call with no free tier, so behind Pro or capped pilots only. | 2026-09-30 |
| `22b-app-features-brief.md` | §22b, medical-student AI app features | Table stakes Stethoscore lacks: an exam-date daily plan, a source citation on every generated item, guaranteed offline review, a transparent trial and AI quota, highlighting and Dynamic Type in the readers. Differentiators no competitor has: ten modes from the student's own material, draw-from-memory recall, the 3D idea graph, spoken-answer commute audio, Arabic. Avoid: leaderboards and challenges, a video library, a 3D anatomy atlas, an open-web chatbot, hard paywalls. | 2026-09-30 |
| `22f-fail-safe-brief.md` | §22f, fail-safe design (judging §22e) | §22e is not sound as written: overengineered for this shape (breakers, health pings, hash-chained logs for components no repo has) and silent on the real risks (daily quota exhaustion, push-wins data loss, unreadable file treated as empty, one model vote minting "Verified"). Adopt the reduced protocol: data integrity first, then retries and timeouts, then Worker breakers and kill switches, with CI fault injection. | 2026-09-30 |
| `22g-langgraph-brief.md` | §22g, LangGraph | Do not integrate now: it cannot run on the 10-second Worker path, needs an undeployed Python service and a sleeping free Postgres, and supplies none of the watchdog, retry or dead-letter behaviour required. Build the explicit state machine and Cloudflare Workflows; re-open only for a deployed verifier graph with more than three branch points or resumable multi-minute runs. | 2026-09-30 |

## Audits (`audit/`)

| File | Scope | Recommendation in one line | Date |
|---|---|---|---|
| `task4-chat-me-audit.md` | Task 4: the `medical-verifier` package against the plan's §7, §8, §10, §13, §17, §22e, §22g and §3c, and against Stethoscore's deployed Worker accuracy engine | The Worker engine stays the verification spine; the Python verifier folds in as modules (deterministic guards ported as a pre-vote claim gate, evidence hashing and revalidation, the curriculum lane and verdict schema, the shared test vectors as a parity corpus) and stays a GitHub Actions batch tool. | 2026-09-30 |
| `audit/experiments/probe*.py` | The scripts run during the audit (fail-open checks, guard behaviour, audit-store behaviour) | Kept so the audit's E-numbered findings can be reproduced. | 2026-09-30 |

Still to come, in the plan's order: §22d (question bank from free sources),
§22c (general student helper apps), §23a (App Store pages), §20 (revenue model).
