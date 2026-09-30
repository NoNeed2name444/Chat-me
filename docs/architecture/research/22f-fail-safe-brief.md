# §22f Fail-Safe Deep Research Brief — Stethoscore

Date 2026-09-30. Judges §22e against sources fetched today; [Sn] points to Sources.

**Ground truth (grep of all four repos today).** In code: on-device Foundation Models, MetricKit, one Worker + D1 + Workers AI, Durable-Object job retries (`jobs.js`, RETRY_SECONDS=20), Gemini 429 `retryDelay` parsing, 17 atomic file writes, foreground `timeoutInterval`s of 12/60/180/300 s. In no repo: Jev, MedCPT, mDeBERTa, Qdrant/pgvector, LangGraph, PubMed E-utilities, NWPathMonitor, waitsForConnectivity, any circuit breaker. The four audited fail-open items remain unverified by grep.

## Reliability fundamentals
- 100% is the wrong target; a 99.9% Worker SLO is enough when the primary copy is on the device [S1].
- Retries: ≤3 attempts per request, ~10% retry budget, randomized exponential backoff, never at two layers (4 attempts × 3 layers = 64 backend hits), deadline propagation [S2][S3].
- At hundreds of users the cascade mechanism is quota, not threads: Workers Free 100k requests/day, 10 ms CPU, 6 concurrent upstream connections [S4]; Workers AI 10,000 neurons/day hard stop [S5]; D1 100k writes/5M reads per day, then queries fail until 00:00 UTC [S6].
- Fallback modes entered only during chaos are the paths that fail; one always-exercised path (offline-first) beats exotic fallbacks [S7]. No N+1/2N is affordable; redundancy = device copy + server copy + tested restore [S60].
- Resilience engineering (Woods' four concepts; Hollnagel's Safety-II) values the operator's adaptive capacity over mechanism count — design so the owner can see and act [S73][S74].
- MTTD: MetricKit delivers crash diagnostics immediately on iOS 15+ [S8]; content errors are found only by students, so a report button is detection.

## Circuit breaker patterns
|pattern|source|when to use|failure modes|recommendation for Stethoscore|
|---|---|---|---|---|
|Nygard/Fowler breaker; log state changes; retry through it|[S9][S10][S76]|remote dependency that hangs or fails in bulk|binary; hides partial failure|one per upstream, Worker only|
|Hystrix defaults 20 calls/10 s, 50%, 5 s sleep, 1 s timeout; maintenance mode|[S11][S12]|Netflix traffic|volume threshold never met here → never opens|do not copy numbers|
|resilience4j: 50%, window 100, min 100 calls, open 60 s, 10 half-open calls|[S13]|JVM services|10 probes = half-open storm|1 probe|
|Polly: 10%, min 100, 30 s sample, 5 s break|[S14]|.NET|guarding on state blocks half-open|always call through breaker|
|cockatiel ConsecutiveBreaker(5), halfOpenAfter 10 s; opossum 50%/30 s reset|[S15][S16]|low-traffic JS|—|consecutive semantics fit our scale|
|"Fuse (Swift)"|[S17][S18][S19]|—|fuse-swift is fuzzy search; Fuse breaker is Erlang; Kitura/CircuitBreaker: 3 commits since 2020|~60-line Swift actor, no dependency|
|Retry token bucket|[S20]|many short-lived clients|—|app side: beats a breaker|
|Hedged requests after p95|[S21]|replicated backends|doubles paid-model cost|avoid|
|Herd/half-open storms|[S22]|—|Google 2025-06-12: no randomized backoff → herd on Spanner|jitter the open duration|

Numbers: Worker per-upstream breaker opens after 3 consecutive failures or 2 timeouts, open 30 s ±50% jitter, one probe; a separate "quota exhausted" state holds until 00:00 UTC [S5][S6]. App: 60 s cooldown per dependency after 3 failures; none on the on-device model.

## Bulkhead patterns
- Partition per dependency; combine with retry/breaker/throttling; don't rebuild platform controls [S23].
- Swift bulkhead = structured concurrency: UI never awaits AI; AI runs in cancellable Tasks that check `Task.isCancelled` [S24]; Foundation Models are availability-checked (`deviceNotEligible`, `appleIntelligenceNotEnabled`, `modelNotReady`) before a feature is offered [S25].
- Worker: 6 upstream connections and 10 ms CPU mean one upstream call per request; slow work stays in DO jobs (alarms retry 6× with backoff, at-least-once) [S4][S26].
- Study features read local files only; verdicts live in a separate file; a missing or invalid verdict is Unverified.

## Timeout patterns
Apple defaults: request timer 60 s (resets per byte), resource 7 days; use `waitsForConnectivity` instead of reachability gating; background sessions always wait [S27][S28][S29]. Every integration point gets a timeout [S9]; attention breaks at 10 s [S30]; the Worker uses `AbortSignal.timeout` [S31].

|dependency|timeout|note|
|---|---|---|
|Worker health/entitlement/config|5 s|shallow, cached|
|Sync push/pull|15 s request, 60 s resource|idempotent, resumable|
|Workers AI free models|20 s Worker; 30 s app|schema-validate output|
|Gemini text|45 s Worker-side|honour `retryDelay` on 429 [S34]|
|Gemini transcription|job: 120 s upload resource, 10 s poll, 10 min budget via DO alarm|audio kept until ack|
|PubMed/NCBI (when built)|5 s, background only|API key; ≤3 rps without [S71]|
|On-device model|20 s interactive, 60 s batch|cooperative cancel|
|Jev 1 s|N/A|not built|
|any UI-blocking wait|≤10 s, then background job with status|[S30]|

Today's 180–300 s foreground timeouts are acceptable only when the UI stays free and the work resumes.

## Retry patterns
- 3 attempts, base 1 s, cap 30 s (app) or the DO alarm cadence (server), full jitter; honour Retry-After; retry only 408/429/5xx/timeouts [S2][S32][S33][S75].
- Non-idempotent writes carry a client UUID idempotency key stored with the result [S35][S36].
- Never retry the same call in app and Worker; DO alarms already retry [S2][S26].
- §22e's five retries to a 60 s cap means six attempts and ~63 s of foreground waiting: too long [S30].

## Graceful degradation
Offline-first is the normal mode: local files are truth, sync is opportunistic, cached verdicts follow stale-while-revalidate/stale-if-error with visible age [S37]. Three labels: Verified (date, model), Cached verification (date), Unverified. Kill switch per AI feature in server config, read at launch [S38]. Storage full: block downloads, keep answers in memory plus a small journal.

## Chaos engineering
Principles: steady state, hypothesis, real events, contained blast radius [S39]. Cluster tools (Gremlin, Litmus, FIS) don't apply. Realistic here: XCTest fault injection through a `URLProtocol` stub (500/429/timeout/garbage/offline) [S40]; a debug-only FailureInjector (disk full, corrupt JSON, model unavailable) — OpenAI adopted fault injection after its outage [S41]; Worker tests with stubbed `fetch`; a monthly game day on the owner's iPhone (airplane mode mid-quiz, kill mid-save, restore from backup); all on GitHub Actions macOS runners [S42]. No production chaos.

## Distributed systems failure modes
A star (devices ↔ one Worker ↔ one D1): no consensus, split-brain or Raft; CAP framing itself misleads [S43]. Real risks: lost updates under "push wins" (Anki's manual: when both sides changed, one side is lost) [S44]; clock skew — order by server cursor, never device time; omission — a D1 write rejected at quota must not advance the cursor [S6]; Byzantine = malicious clients → per-token limits.

## Regulatory requirements
|regulation|applies?|requirements|compliance plan|
|---|---|---|---|
|FDA device software/SaMD|No: flash cards, Q&A quiz and board-prep apps are listed as not devices [S46][S47]|none|keep intended use educational; no patient-specific advice|
|FDA General Wellness|No (lifestyle products) [S48]|none|none|
|IEC 62304|Not required (device software) [S49]|classes A–C|voluntary Class-A discipline, no lifecycle paperwork|
|ISO 14971|Not required [S50]|risk file|one-page hazard list|
|HIPAA|No: covered entities/business associates only [S51]|—|treat lecture audio as sensitive anyway|
|GDPR|Yes for EU students [S52]|72 h breach notice|data minimisation, encrypted backups|
|EU AI Act|Annex III(3) covers admission and evaluating learning outcomes in institutions; self-study not listed — contested for AI grading [S53]|—|monitor|
|OWASP fail securely|Yes [S54]|deny on exception|entitlement/auth default false|

## Mobile fail-safes (iOS)
NWPathMonitor drives UI state only [S55]; requests rely on `waitsForConnectivity` and errors [S29]. Make atomic writes universal; quarantine an unreadable file as `.corrupt-<date>`, never treat it as empty [S56][S60]. BGProcessingTask runs only when idle and dies when the user returns, so queued transcription must be server-side and resumable [S57][S58]. `modelNotReady` is retry-later, not permanent [S25]. Memory warnings drop caches, never in-flight edits.

## Web fail-safes (browser)
Not applicable: no browser client (§22e C7 is another product). Server note: keep `/health` shallow and cached; deep probes cascade [S59].

## Data integrity
Restores, not backups; soft delete; layered copies; "you only know you can recover if you do" [S60]. Atomic replace [S56]; SQLite WAL with `synchronous=NORMAL` trades power-loss durability for consistency [S61]; D1 Time Travel gives 7 days of point-in-time restore free [S62]; idempotency keys on pushes [S35]. A SHA-256 chain written by the one writer that could rewrite it adds no integrity; append-only JSON lines plus a restore test do (contested).

## AI/ML fail-safes
Provider quality can degrade silently for weeks and evaluations can miss it [S63]; provider status incidents are frequent [S78]. One free model's vote must never mint "Verified". Abstention is the recognised mitigation: the verifier outputs Supported/Contradicted/Unverifiable and any error maps to Unverifiable [S64]. Schema-validate model JSON; never show partial output as final; key verdict caches by model version with a 30–90 day TTL; fallback chains only if exercised weekly [S7].

## Production incident learnings
|incident|cause|what would have helped|applicability|
|---|---|---|---|
|Google Cloud 2025-06-12, ~3 h [S22]|null pointer in unflagged code path; no randomized backoff → herd|feature flags, error handling, jitter|Worker config parsing; retry jitter|
|OpenAI 2024-12-11, 4 h 22 m [S41]|telemetry rollout overloaded control plane|phased rollout, fault injection|device-side 60 s pings are self-inflicted load|
|Anthropic Aug–Sep 2025 [S63]|three infra bugs degraded outputs; evals missed|production quality evals|never trust one model verdict|
|Cloudflare 2025-11-18, ~6 h [S38]|config file doubled, hard-coded limit, panic|treat internal config as untrusted; kill switches|theme-mapping/config JSON|
|AnkiWeb Sept 2023 [S45]|server fault; hours to restore|daily full + incremental backups; local study continued|offline-first works|
|Anki sync design [S44]|one-way sync loses one side|per-record merge|replace push-wins|
|AMBOSS 2026-05-21 web outage 11 h 28 m (aggregator) [S65]|UNKNOWN, no postmortem|—|—|
|UWorld, Quizlet [S66][S67]|UNKNOWN: no public reports|—|—|

## Security fail-safes
Deny on exception for auth and entitlement [S54]; set the Worker's over-quota mode to fail closed [S4]; per-token rate limits (`diagnostics.js` already budgets); rotate secrets via Wrangler; server-side token revocation; Cloudflare absorbs DDoS; the daily cap is the real exposure.

## UX of failure
Visible, precise, constructive, preserves input, no blame, no humour [S68]; 0.1/1/10 s limits [S30]; explanations restore trust better than bare apologies, whose effect is mixed (robot/vehicle studies) [S69][S70]. For students: label state ("Unverified — check failed"), no ETAs on free tiers, one banner per failure class, batch non-urgent notices.

## §22e evaluation
|proposed pattern|verdict|reason|research source|
|---|---|---|---|
|P1 fail closed|keep, define|closed = Unverified/queued, never a frozen UI|[S54]|
|P7 breaker on every dependency|adjust|Worker upstreams only; app uses bounded retries + cooldown; none for local model|[S20][S13]|
|60 s health check|remove|1,440 req/device/day against 100k/day; self-inflicted load; piggyback on real calls|[S4][S41][S59]|
|per-session storage integrity scan|adjust|validate on read + quarantine|[S56][S60]|
|24 h breaker review|remove|no reviewer; log state changes|[S10]|
|A1–A5 Opus|adjust|no Opus in code; generic hosted-model class; malformed → schema reject; hallucination → abstain|[S63][S64]|
|A2 retry with shorter prompt|remove|unproven; one retry after backoff, else queue|[S32]|
|A3 backoff 1–60 s|adjust|3 attempts, cap 30 s, jitter, honour retryDelay|[S2][S34]|
|A6 queue audio|keep|DO jobs already do it|[S26]|
|A7 local crash → cloud|adjust|availability check first; cloud only behind cost gate|[S25]|
|B1–B2 PubMed|adjust|unbuilt; key, ≤3 rps, background only|[S71]|
|B3–B8, B11|remove for now|components absent; keep "verifier error ⇒ Unverifiable"|grep, [S7]|
|B6 skip Jev → full pipeline|remove|never-exercised fallback|[S7]|
|B9 hash-chain halt/rebuild|adjust|append-only log + restore test|[S60]|
|B10 reject citation without source|keep|—|[S64]|
|C1 bedside mode|keep, make default|offline-first|[S45][S37]|
|C2, C4, C6|keep|as written; C2 adds an in-memory journal|[S68][S42]|
|C3 rebuild from backup|adjust|quarantine, never overwrite, restore tested in CI|[S60][S56]|
|C5 sync retry + status|keep, add|idempotency key; cursor advances only on ack|[S35][S2]|
|C7 artifact republish|N/A|not this app|—|
|recovery: last write wins|adjust|conflict copies for student content; LWW for derived data only|[S44]|
|breaker 5/60 s, 30 s open, 5-min reset|adjust|3 consecutive, 30 s ±50%, 1 probe; drop the 5-min health reset|[S15][S13]|
|escalation P0–P2 ≤1 h|adjust|one person: GitHub issue + push; auto-halt only "served as verified"|[S77]|
|SHA-256 immutable logs|adjust|JSON lines + D1 table|[S60]|
|staging chaos, weekly failover|adjust|CI fault injection + monthly game day|[S39][S40][S42]|
|F1–F7 LangGraph|remove|absent from all repos|grep|
|G1–G7 3D|keep, soften G6|dry-run + confirm, not auto-rewrite|[S38]|
|messages with ETA|adjust|no ETAs|[S68]|
|tool "Fuse"|remove|wrong library|[S17]|
|streamed answer + retraction banner|adjust|don't stream unverified medical answers|[S63]|
|every feature defines its failure mode|keep|STPA-lite unsafe control actions|[S72]|

## Gaps in §22e
1. Daily quotas as a failure class, with a mid-day "budget exhausted" state [S4][S5][S6].
2. The four audited fail-open behaviours: each becomes a failing test first.
3. Idempotency keys for sync pushes [S35].
4. Remote kill switch per AI feature [S38].
5. Model-version-keyed verdict cache with bounded TTL [S63].
6. Student "report a problem" as detection [S77].
7. Restore drill in CI [S60].
8. One-page hazard list (STPA/ISO 14971-lite) [S72][S50].
9. Deadline propagation app→Worker (remaining-budget header) [S2].
10. Worker over-quota mode set to fail closed [S4].

## Overengineering in §22e
Breakers on every dependency including the local model; 60 s health pings; 24 h breaker review; SHA-256 chained immutable logs; the LangGraph category; weekly failover tests; staging chaos; hedged requests/N+1; five retries to 60 s; P0–P2 with hourly SLAs for one person; twelve scenarios for unbuilt components; ETAs in messages; "Fuse".

## Recommended additions to §22e
The ten gap items and the timeout table, written into §22e as binding text; drop the removed rows.

## Top 10 fail-safe patterns to implement
1. Atomic write + quarantine for every library file — never lose work [S56][S60].
2. Verification state machine defaulting to Unverified; verdict cache keyed by model version, 30–90 day TTL [S63][S64].
3. Bounded retries (3, 1→30 s, full jitter, Retry-After), one layer [S2][S32][S34].
4. Timeout table on every call; UI waits ≤10 s then becomes a job [S27][S30].
5. Idempotent sync: key per push, cursor on ack, conflict copies [S35][S44].
6. Worker breakers (3 consecutive/30 s jittered/1 probe) plus quota state [S15][S5].
7. Kill switch per AI feature [S38][S22].
8. Fault-injection tests + restore test in GitHub Actions [S40][S42][S60].
9. Structured failure log + MetricKit issues + report button [S8][S77].
10. Offline-first with waitsForConnectivity; NWPathMonitor for UI only [S29][S55].

## Top 5 fail-safe patterns to avoid
1. Periodic device health pings [S41][S59].
2. Volume-threshold breakers (20–100 calls) that never trip at this traffic [S11][S13].
3. Untested fallback chains (Jev→mDeBERTa, local→cloud) [S7].
4. Hash-chained client logs, LangGraph checkpoints, staging chaos — cost without safety [S60].
5. Streaming unverified answers then retracting; ETAs in errors [S63][S68].

## Sources
S1 https://sre.google/sre-book/embracing-risk/
S2 https://sre.google/sre-book/addressing-cascading-failures/
S3 https://sre.google/sre-book/handling-overload/
S4 https://developers.cloudflare.com/workers/platform/limits/
S5 https://developers.cloudflare.com/workers-ai/platform/pricing/
S6 https://developers.cloudflare.com/d1/platform/pricing/
S7 https://aws.amazon.com/builders-library/avoiding-fallback-in-distributed-systems/
S8 https://developer.apple.com/documentation/metrickit/mxmetricmanager
S9 https://pragprog.com/titles/mnee2/release-it-second-edition/ https://john.dev/posts/2019-04-14-release-it-notes.html
S10 https://martinfowler.com/bliki/CircuitBreaker.html
S11 https://github.com/Netflix/Hystrix/wiki/Configuration
S12 https://github.com/Netflix/Hystrix
S13 https://resilience4j.readme.io/docs/circuitbreaker
S14 https://www.pollydocs.org/strategies/circuit-breaker.html
S15 https://github.com/connor4312/cockatiel
S16 https://github.com/nodeshift/opossum
S17 https://github.com/krisk/fuse-swift
S18 https://github.com/jlouis/fuse
S19 https://github.com/Kitura/CircuitBreaker/commits/master
S20 https://brooker.co.za/blog/2022/02/28/retries.html
S21 https://blog.acolyer.org/2015/01/15/the-tail-at-scale/ (summary; CACM 2013)
S22 https://status.cloud.google.com/incidents/ow5i3PPK96RduMcb1SsW
S23 https://learn.microsoft.com/en-us/azure/architecture/patterns/bulkhead
S24 https://docs.swift.org/swift-book/documentation/the-swift-programming-language/concurrency/
S25 https://developer.apple.com/documentation/foundationmodels/systemlanguagemodel/availability-swift.enum/unavailablereason
S26 https://developers.cloudflare.com/durable-objects/api/alarms/
S27 https://developer.apple.com/documentation/foundation/urlsessionconfiguration/timeoutintervalforrequest
S28 https://developer.apple.com/documentation/foundation/urlsessionconfiguration/timeoutintervalforresource
S29 https://developer.apple.com/documentation/foundation/urlsessionconfiguration/waitsforconnectivity
S30 https://www.nngroup.com/articles/response-times-3-important-limits/
S31 https://developer.mozilla.org/en-US/docs/Web/API/AbortSignal/timeout_static
S32 https://aws.amazon.com/builders-library/timeouts-retries-and-backoff-with-jitter/
S33 https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/
S34 https://ai.google.dev/gemini-api/docs/rate-limits
S35 https://docs.stripe.com/api/idempotent_requests
S36 https://aws.amazon.com/builders-library/making-retries-safe-with-idempotent-APIs/
S37 https://www.rfc-editor.org/rfc/rfc5861
S38 https://blog.cloudflare.com/18-november-2025-outage/
S39 http://principlesofchaos.org/
S40 https://www.hackingwithswift.com/articles/153/how-to-test-ios-networking-code-the-easy-way
S41 https://status.openai.com/incidents/ctrsv3lwd797
S42 https://docs.github.com/en/actions/use-cases-and-examples/building-and-testing/building-and-testing-swift
S43 https://arxiv.org/abs/1509.05393
S44 https://docs.ankiweb.net/syncing.html
S45 https://forums.ankiweb.net/t/ankiweb-temporarily-offline/34613
S46 https://www.fda.gov/medical-devices/device-software-functions-including-mobile-medical-applications/examples-software-functions-are-not-medical-devices
S47 https://www.fda.gov/regulatory-information/search-fda-guidance-documents/policy-device-software-functions-and-mobile-medical-applications
S48 https://www.fda.gov/regulatory-information/search-fda-guidance-documents/general-wellness-policy-low-risk-devices
S49 https://blog.johner-institute.com/iec-62304-medical-software/safety-class-iec-62304/
S50 https://www.iso.org/standard/72704.html
S51 https://www.cms.gov/priorities/key-initiatives/burden-reduction/administrative-simplification/hipaa/covered-entities
S52 https://gdpr-info.eu/art-33-gdpr/
S53 https://artificialintelligenceact.eu/annex/3/
S54 https://owasp.org/www-community/Fail_securely
S55 https://developer.apple.com/documentation/network/nwpathmonitor
S56 https://developer.apple.com/documentation/foundation/nsdata/writingoptions/atomic
S57 https://developer.apple.com/documentation/backgroundtasks/bgtaskscheduler
S58 https://developer.apple.com/documentation/backgroundtasks/bgprocessingtask
S59 https://learn.microsoft.com/en-us/azure/architecture/patterns/health-endpoint-monitoring
S60 https://sre.google/sre-book/data-integrity/
S61 https://www.sqlite.org/wal.html
S62 https://developers.cloudflare.com/d1/platform/limits/
S63 https://www.anthropic.com/engineering/a-postmortem-of-three-recent-issues
S64 https://arxiv.org/abs/2407.18418
S65 https://status.amboss.com/history https://isdown.app/status/amboss/incidents/361135-amboss-web-platform-outage
S66 https://x.com/UWorld/status/1791188975924678743
S67 https://downdetector.com/status/quizlet
S68 https://www.nngroup.com/articles/error-message-guidelines/
S69 https://arxiv.org/abs/2510.21716
S70 https://arxiv.org/abs/2412.15787
S71 https://ncbiinsights.ncbi.nlm.nih.gov/2017/11/02/new-api-keys-for-the-e-utilities/
S72 https://psas.scripts.mit.edu/home/get_file.php?name=STPA_handbook.pdf
S73 https://www.sciencedirect.com/science/article/abs/pii/S0951832015000848
S74 https://www.sciencedirect.com/science/article/pii/S2093791120303619
S75 https://learn.microsoft.com/en-us/azure/architecture/patterns/retry
S76 https://learn.microsoft.com/en-us/azure/architecture/patterns/circuit-breaker
S77 https://sre.google/sre-book/monitoring-distributed-systems/
S78 https://statusgator.com/services/anthropic/outage-history?page=22

## CRITICAL GATE verdict
§22e is not sound as-is: it is overengineered for Stethoscore's real shape and unsafe in places. It specifies breakers, health pings, hash-chained logs, LangGraph handling and twelve scenarios for components that exist in no repo, while omitting the failures that will actually hit a free-tier Worker + D1 + Workers AI backend serving hundreds of students: daily quota exhaustion, lost updates under "push wins", an unreadable file treated as empty, and a single model vote minting "Verified". Its six-attempt retry ladder, 60-second health checks and streamed-then-retracted answers would worsen load and user experience. Recommendation: adopt the reduced protocol above (top-10 patterns, timeout table, evaluation-table adjustments, ten gap items) in this order: data-integrity fixes and verification defaults, then retries and timeouts, then Worker breakers and kill switches, with CI fault injection throughout. Defer B3–B8, B11 and F until those components exist, then re-run only the changed parts of this brief.
