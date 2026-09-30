# LANGGRAPH DEEP RESEARCH BRIEF (§22g) — 2026-09-30

Scope: LangGraph against Stethoscore's stack (§9). Claims cite [S#]; UNKNOWN marks missing evidence.

## 1. Factual baseline
- Python `langgraph` 1.2.12 (2026-09-21), MIT; 1.0.0 on 2025-10-17 [S1]. JS `@langchain/langgraph` 1.4.18, MIT; 1.0.0 on 2025-10-18 [S2].
- Self-described "low-level orchestration framework and runtime for ... long-running, stateful agents" that "does not abstract prompts or architecture" [S3].
- First-party production evidence: LinkedIn SQL Bot [S22]; Uber EAg-RAG (+27% acceptable answers, -60% incorrect guidance) [S23]; Elastic ("We use LangGraph to design and run our AI Agent workflows") [S24]. Replit and Klarna are vendor case studies only [S25][S27]; Klarna's own release credits OpenAI, not LangGraph [S26].
- The hosted runtime (LangSmith Deployment) is paid: Developer $0 has no deployment; Plus is $39/seat [S35]; out under the free-tier rule.

## 2. Architecture and core concepts
| Concept | What it is | How it works |
|---|---|---|
| State | Shared typed schema | Nodes return partial updates; reducers merge; snapshot per super-step [S4] |
| Nodes | Functions over state | Re-run from the top on resume; must be idempotent [S4] |
| Edges | Fixed or conditional transitions | Routing function; cycles allowed [S4] |
| Recursion limit | Cap on super-steps | Default 1000 since 1.0.6; `GraphRecursionError` [S4] |
| Send | Map-reduce fan-out | One `Send(node, payload)` per item [S4] |
| Command | Update plus `goto` | `Command(resume=...)` is the only resume input [S4][S5] |
| Subgraphs | Nested graphs | Shared keys: add as node; else wrapper; `checkpointer=False` = no durability [S6] |
| Durability | Checkpoint timing | `sync` blocks before next step; `async`; `exit` writes only at end [S7] |

## 3. Persistence and checkpointing
- Checkpoints are written at super-step boundaries per `thread_id`; savers: memory, SQLite, Postgres, Redis, Mongo (both languages), D1 (Python) [S4][S8][S10].
- The D1 saver is Cloudflare-maintained Python (v0.1.6, Aug 2026) [S10]; JS D1 savers are third-party one-maintainer packages [S11]. Official JS Postgres and Redis savers broke inside Workers (issue #1692, Sept 2025) [S9].
- On resume a node "runs again from the start of its function. Code and side effects before the pause run again" [S4]; the stage-9 audit chain needs idempotent writes.
- Independent critique (Diagrid, a Dapr vendor): "There is no supervisor, no watchdog, no heartbeat mechanism"; "LangGraph makes you the orchestrator"; no dead-letter queue [S16].
- §22g's "SQL injection leading to RCE" merges two advisories: SQLi bypasses filters [S13]; the RCE is the serializer [S12] (§8).

## 4. Human-in-the-loop
- `interrupt()` suspends the node, persists state and "waits indefinitely" for `Command(resume=...)` on the same `thread_id`; a checkpointer is mandatory [S5].
- The node restarts from its first line on resume, so `interrupt()` must come first [S5]. Included Health uses it to hand off to humans [S28].
- Fit: review is a queue; one interrupt per flagged claim leaves one open thread per item forever (F5); a `review_items` status row does the same job.

## 5. Multi-agent orchestration
- Included Health's "supergraph": team-owned sub-workflows, clinician annotation queues; "75% lift in chat engagement", clinical accuracy above a 95% target (LangChain blog, Sept 2026) [S28]. Included Health's own site names no framework [S29].
- Overhead is small: MAFBench (SIGMOD 2027) measured LangGraph p50 548 ms against 495 ms for a direct model call; residual framework latency "under 21 ms and below 2.2%"; the 60x outlier is Concordia [S19]. Latency comes from model calls and checkpoint round-trips.
- Tokens: AIMultiple's five-agent workflow (100 runs): LangGraph 2,589 per planner stage against CrewAI 5,339 and AutoGen 3,316; 2.2x faster than CrewAI [S20]. §22g's "10,750 tokens" and "20-40% cost" figures are absent: provenance UNKNOWN.
- Bugs: 5,669 reports across five frameworks; 22 root causes, 7 symptoms; 76% "Incorrect Functionality"; API and serialization causes dominate [S17]. A 409-bug study attributes 17 to LangGraph [S18]. §22g's "998 reports" study could not be located: UNKNOWN.

## 6. Comparison to alternatives
| Framework | Strengths | Weaknesses | When to use |
|---|---|---|---|
| LangGraph | Explicit graph, checkpoints, interrupts, Send; lowest tokens and latency [S19][S20] | Boilerplate; checkpoint DB; you build recovery [S16]; advisories [S12][S13] | >3 branch points, resumable multi-minute runs, durable HITL |
| CrewAI | Fast prototyping | About 2x tokens; 5-s deliberation before tools [S20] | Role-play crews |
| AutoGen / Semantic Kernel | Conversational agents, .NET and Python | Higher tokens [S20]; reported merger UNVERIFIED | Microsoft stack |
| OpenAI Agents SDK | Not researched | UNKNOWN | UNKNOWN |
| Claude Agent SDK | Claude Code harness as a library; built-in tools, subagents; self-hosted [S42] | Coding-agent shape, not a pipeline orchestrator | Autonomous coding agents |
| Hybrid claims | CREW-Wildfire benchmark exists [S21] | "96.1% hybrid" and "5.76x Claude SDK" results not found: UNKNOWN | - |

## 7. Production use cases in medical AI
| Use case | What they built | Relevance |
|---|---|---|
| Prostate cancer summarisation | UChicago capstone: MCP-governed, LangGraph-orchestrated RAG; 500 synthetic records; "preliminary evaluation" [S30] | Pattern only; no clinical validation |
| MASH HEOR landscape | Vendor poster (ISPOR 2026): Deep Agent, >1,000 sub-agent calls, >100-page report in <48 h [S31] | Offline batch; no accuracy metric |
| Patient triage from PDFs | PyPI `patient-triage` 0.7.2: intake, priority queue, specialists, capped loop; "not a diagnostic device" [S32] | Loop-cap pattern; no evaluation |
| Pharmacotherapy GraphRAG | LangGraph v1.0.4; extractor plus critic agents; F1 0.76 (n=12); QA accuracy 0.545 vs 0.443 (n=30) [S33] | Closest to stages 4-6; tiny n |
| Healthcare navigation | Included Health Dot (§5) [S28] | Production evidence; routing, not verification |
| Self-RAG | Paper plus official LangGraph agentic-RAG tutorial (grade, generate or rewrite) [S34] | Stages 2-6 exactly; four nodes, trivial to hand-code |
| Clinical interviews with KG; health tourism (BGE-M3, Qdrant, Qwen3-14B) | Not located | UNKNOWN |

## 8. Failure modes and challenges
1. Serializer RCE when untrusted data lands in checkpoints: CVE-2025-64439, CVSS 7.4, fixed `langgraph-checkpoint` 3.0.0; ours would hold model output and PubMed text [S12].
2. SQL injection via `get_state_history`/`list` filter keys: CVE-2025-67644, CVSS 7.3, fixed `langgraph-checkpoint-sqlite` 3.0.1 [S13]; 18 LangGraph-package advisories since Oct 2025 [S14][S15].
3. Checkpoint is not durable execution: no watchdog, retry supervisor or DLQ [S16]; F2, F3, F6 stay yours to write.
4. Re-execution duplicates side effects [S4]; audit chain, NCBI calls and D1 writes need idempotency keys.
5. Workers hostility: 10 ms CPU per request on Free [S37]; savers cancelled mid-write [S9]; no first-party JS D1 saver [S11].
6. Default recursion limit 1000 [S4] and interrupts that wait forever [S5]: the F4 cap and F5 expiry are yours.
7. API churn: 277 releases; API and serialization causes dominate bugs [S1][S17].
8. Free Postgres sleeps: Supabase pauses after 7 idle days [S39]; Neon suspends after 5 min, 100 CU-hours per project per month [S40]. Cold starts land inside the 10-s budget.
9. Schema drift between graph versions and stored checkpoints (F1, F7) has no built-in validator [S4].

## 9. Integration with Stethoscore
Only the Python FastAPI verifier could host it; LangGraph.js in the Worker is impractical [S9][S37], so the undeployed verifier plus a Postgres checkpointer become prerequisites. Done well: stages as nodes, `Send` fan-out, per-stage snapshots, review pauses. Done badly: 18 advisories in a medical app; a checkpoint database the stack lacks; none of the recovery §22e demands (F1-F7 exist only because LangGraph exists); an HITL model that mismatches a queue; a near-linear DAG with one fan-out, the "deterministic steps" case in the docs [S3].

## 10. Fit, use case by use case
| Use case | Verdict | Reason | Measurement required |
|---|---|---|---|
| Verification stages 1-9, per question, <=10 s | Defer | Linear DAG plus one fan-out; a FastAPI state machine maps directly to §22e | p95 <=10 s; checkpoint ms per stage; % runs recovered after failure |
| Parallel claim verification | Defer | `asyncio.gather` equals `Send` at this scale | Speed-up over sequential; tokens per claim |
| Expert review queue (stage 8) | Reject | A queue is a table; interrupts hold threads indefinitely | Time-to-resume; orphaned threads = 0 |
| Jev gate 3.5, 1-s timeout | Reject | A conditional edge is an if-statement | Gate p99 under 1 s |
| Background processing after B11 abort | Defer | The one real durable-execution need; Cloudflare Workflows covers it free [S36] | Completion rate; resume-after-crash success |
| Question-bank batch validation | Defer | LangGraph's strongest fit; revisit once the verifier is deployed | Items per hour; failed-item recovery |
| Adaptive difficulty and trajectory | Reject | Deterministic client state | - |
| Streaming verification | Reject | Streaming is Worker-to-SwiftUI; a Python graph adds a hop | - |
| Fail-safe recovery | Reject | Checkpoints are not recovery [S16] | - |

## 11. Overall verdict
Neutral at best today, worse if adopted now: no use case gains latency, cost or hallucination reduction from the framework itself, while checkpoint storage, sleeping Postgres and the security surface add failure paths.

## 12. Deployment recommendation
Now: no LangGraph. Write `pipeline/state_machine.py` in the FastAPI verifier (typed stage results, per-stage timeouts, circuit breakers) and use Cloudflare Workflows for background verification. Later, once the verifier is deployed and a graph needs more than three branch points or multi-minute resumable runs: LangGraph Python inside FastAPI, `langgraph-checkpoint>=3.0.0`, `langgraph-checkpoint-sqlite>=3.0.1` in development, Neon Postgres in production, `durability="sync"` only for background graphs, explicit `recursion_limit`, schema-validated state, idempotency keys on every write. Never: LangGraph.js in the Worker; LangSmith Deployment; `interrupt()` as the review queue; raw retrieved text in checkpoints.

## 13. Alternatives considered
| Alternative | Strengths | Weaknesses | Decision |
|---|---|---|---|
| Explicit state machine (FastAPI plus D1 status rows) | No dependency; maps 1:1 to §22e; pytest-testable | You write retries and timeouts | Chosen now |
| Cloudflare Workflows | Free: 1,024 steps, 100 MB state, 100 concurrent, retries, 3-day retention [S36] | JS under the Worker CPU limit [S37] | Chosen for background jobs |
| Durable Objects (SQLite) | Per-thread coordination; Free: 5M rows read/day, 100k written/day, 5 GB [S38] | Single-object serialization; same CPU limits | Later, if needed |
| Temporal | Real durable execution; MIT server [S41] | Cloud from $50 per million actions, $150 credit for 90 days, no free tier; self-hosting needs a cluster [S41] | Cost |
| Claude Agent SDK | Batteries-included agent loop [S42] | Wrong shape for a fixed verification DAG | Not a pipeline tool |

## 14. Sources
S1 https://pypi.org/pypi/langgraph/json · S2 https://www.npmjs.com/package/@langchain/langgraph · S3 https://docs.langchain.com/oss/python/langgraph/overview · S4 https://docs.langchain.com/oss/python/langgraph/graph-api · S5 https://docs.langchain.com/oss/python/langgraph/interrupts · S6 https://docs.langchain.com/oss/python/langgraph/use-subgraphs · S7 https://github.com/langchain-ai/langgraph/blob/main/libs/langgraph/langgraph/types.py · S8 https://docs.langchain.com/oss/javascript/langgraph/checkpointers · S9 https://github.com/langchain-ai/langgraphjs/issues/1692 · S10 https://github.com/cloudflare/langchain-cloudflare/blob/main/libs/langgraph-checkpoint-cloudflare-d1/README.md · S11 https://www.npmjs.com/package/langgraph-checkpoint-cloudflare-d1 · S12 https://github.com/advisories/GHSA-wwqv-p2pp-99h5 · S13 https://github.com/advisories/GHSA-9rwj-6rc7-p77c · S14 https://github.com/advisories?query=langgraph · S15 https://labs.cloudsecurityalliance.org/research/csa-research-note-langchain-langgraph-vulnerabilities-202603/ · S16 https://www.diagrid.io/blog/checkpoints-are-not-durable-execution-why-langgraph-crewai-google-adk-and-others-fall-short-for-production-agent-workflows · S17 https://arxiv.org/abs/2602.21806 · S18 https://arxiv.org/abs/2604.08906 · S19 https://arxiv.org/abs/2602.03128 · S20 https://aimultiple.com/agentic-orchestration · S21 https://arxiv.org/abs/2507.05178 · S22 https://www.linkedin.com/blog/engineering/ai/practical-text-to-sql-for-data-analytics · S23 https://www.uber.com/us/en/blog/enhanced-agentic-rag/ · S24 https://www.elastic.co/blog/elastic-security-generative-ai-features · S25 https://www.langchain.com/blog/customers-klarna · S26 https://www.klarna.com/international/press/klarna-ai-assistant-handles-two-thirds-of-customer-service-chats-in-its-first-month/ · S27 https://blog.langchain.com/customers-replit/ · S28 https://www.langchain.com/blog/how-included-health-built-federated-agents-for-healthcare-navigation-with-deep-agents-and-langgraph · S29 https://includedhealth.com/solutions/dot-ai-healthcare-assistant/ · S30 https://datascience.uchicago.edu/research/research-gpt-for-healthcare-mcp-driven-multi-agent-rag-enhanced-llm-system-for-prostate-cancer-temporal-summarization · S31 https://www.ispor.org/heor-resources/presentations-database/presentation-cti/ispor-2026/poster-session-4-3/a-multi-agent-genai-system-for-traceable-multi-country-landscape-assessment-in-mash · S32 https://pypi.org/project/patient-triage/ · S33 https://www.frontiersin.org/journals/medicine/articles/10.3389/fmed.2026.1898857/full · S34 https://arxiv.org/abs/2310.11511 and https://docs.langchain.com/oss/python/langgraph/agentic-rag · S35 https://www.langchain.com/pricing · S36 https://developers.cloudflare.com/workflows/reference/limits/ · S37 https://developers.cloudflare.com/workers/platform/limits/ · S38 https://developers.cloudflare.com/workers/platform/pricing/ · S39 https://supabase.com/docs/guides/platform/free-project-pausing · S40 https://neon.com/docs/introduction/plans · S41 https://temporal.io/pricing and https://github.com/temporalio/temporal/blob/main/LICENSE · S42 https://code.claude.com/docs/en/agent-sdk

## CRITICAL GATE
Verdict: LangGraph is not a good addition to Stethoscore today; wiring it in now would make the user experience slightly worse, not better. On the 10-s per-question path on a JS Worker with D1, LangGraph cannot run in practice, needs an undeployed Python service and a sleeping free Postgres, adds a checkpoint store with a recent RCE and SQL-injection history, and supplies none of the watchdog, retry or dead-letter behaviour §22e requires; so students would get the same answers with more cold starts and more ways to fail. It is "better in specific use cases" only in two deferred, offline places: question-bank batch validation and multi-minute background verification. Decision: do not integrate LangGraph now; build the explicit state machine and Cloudflare Workflows; re-open this gate only when the verifier is deployed and a graph needs more than three branch points or resumable multi-minute runs, then only inside that graph with the §12 safeguards.
