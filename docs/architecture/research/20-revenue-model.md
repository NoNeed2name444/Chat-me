# §20 Revenue Model — Stethoscore (2026-09-30)

Conventions: gross = customer spend excluding VAT, in USD; net = gross after Apple's 15% (Small Business Program: 85% from day one, $1M proceeds ceiling [S1][S2]); FX 52 EGP/USD [S14]; (A) = assumption without a source. [22x Sn] cites a brief. Script: `scratchpad/model/revenue_model.py`.

## 1. Unit economics per subscriber

| Tier / plan | US price | Net | Egypt, Apple-equalised tier (incl. 14% VAT [S9]) | Egypt net | Egypt regional price (§5) | Regional net |
|---|---|---|---|---|---|---|
| Student monthly | $19.99 | $16.99 | EGP 999.99 = $19.23 (AMBOSS's $19.99 item [S10]) | $14.34 | EGP 299.99 = $5.77 | $4.30 |
| Student annual | $149 | $126.65 | EGP 7,999.99 = $153.85 (Osmosis' $149 item [S11][22b S13]) | $114.71 | EGP 1,999.99 = $38.46 | $28.68 |
| Pro monthly | $34.99 | $29.74 | ≈EGP 1,749.99 (est. from the ~50:1 grid in [S10][S12][S13]) | $25.09 | EGP 599.99 = $11.54 | $8.60 |
| Pro annual | $299 | $254.15 | ≈EGP 14,999.99 (est.) | $215.08 | EGP 3,999.99 = $76.92 | $57.35 |
| Institutional | $8–15 /student/yr | ≈100% if invoiced directly under guideline 3.1.3(c) [S20] | — | — | — | — |

Egypt net = EGP ÷ 1.14 × 0.85 ÷ 52: the same tier nets 9–16% less than a US sale.

AI cost per active Pro user per month (A: 330 turns = 15/day × 22 days, 3k in + 0.5k out each = 0.99M in / 0.165M out):

| Route | Price per M tokens | Median user | Heavy user (3×) | Note |
|---|---|---|---|---|
| Claude Opus 5.5 | $4 / $20 (owner figure, matches Anthropic's model table cached 2026-09-25) | $7.26 | $21.78 | Heavy user exceeds Pro-annual net per month ($21.18): per-user cap needed |
| Gemini 2.5 Flash, paid | $0.30 / $2.50 [S6] | $0.71 | $2.13 | Fallback above quota |
| Gemini 2.5 Flash-Lite, paid | $0.10 / $0.40 [S6] | $0.17 | $0.49 | |
| Workers AI Llama 3.2 1B, paid | $0.027 / $0.201 [S5] | $0.06 | $0.18 | |
| Free-quota ceiling | Workers AI 10,000 neurons/day [S5] ≈ 606 turns ≈ 40 daily-active users; Gemini free tier [S6], cap unpublished (third parties 250–1,500 RPD, conflicting [S7]) ≈ 17–100; free prompts may train Google models [S6] | $0 | $0 | Student and Free must live here |

## 2. Fixed bills and the Pro-pays rule

| Bill | $/month | Source |
|---|---|---|
| Apple Developer Program, $99/yr | 8.25 | [S3] |
| Domain, .com at cost $10.44/yr | 0.87 | [S23], third-party |
| Cloudflare Workers Paid, only above 100k requests/day or D1 100k writes/day | 5.00 conditional | [S4] |
| Workers AI, D1, Gemini free tiers | 0 | [S4][S5][S6] |
| Anthropic prepaid credits, minimum top-up | UNKNOWN | — |
| Owner income tax in Egypt, accounting, counsel | UNKNOWN | [22d §9] |

Formula: `AIBudget(m) = max(0, 0.6 × ProNet_received(m) − Fixed(m) − Refunds(m))`, where `ProNet_received(m) ≈ ProNet(m−2)` because Apple pays within 45 days of the fiscal month's end [S18]; `DailyCap = AIBudget / days left`; per-user Opus spend ≤ 25% of that user's monthly net (US Pro monthly $7.43, annual $5.30); Student and Free never draw; 0.6 is (A) and leaves 40% as margin and refund reserve. So months 1–2 have no cash: paid models are unavailable by construction and the Pro promise must hold on free quotas. Base budget: $230 in month 3 rising to $1,147 in month 12 (32 → 158 Opus user-months).

## 3. Scenarios

| Assumption | Low | Base | High | Basis |
|---|---|---|---|---|
| Signups, month 1 | 300 | 800 | 2,000 | (A). Scale: 49 schools [S22]; two Cairo faculties ≈ 23k students × iOS 16% [S21] ≈ 3.7k iPhone users; national total UNKNOWN |
| Signup growth /month | 8% | 12% | 20% | (A) |
| Activation | 45% | 55% | 65% | (A); tracked, not in the math |
| Download → paid | 1.5% | 2.5% | 4.5% | Freemium median 2.1% [S16]; education 6.5% trial start × 37.7% ≈ 2.5%, top quartile >51.4% [S15] |
| Trial → paid, 7 days | 37.4% | 37.4% | 37.4% | [S16]; 17–32 days 42.5% [22b S43] |
| Annual share | 45% | 60% | 65% | Education 59–66% [S15] |
| Monthly churn | 15% | 10% | 7% | (A); AI apps churn 30% faster [S16] |
| Egypt share of payers | 90% | 70% | 50% | (A) |
| Pro share; annual refunds | 20%; 3% | same | same | (A); first month holds 35% of annual cancellations [S17] |
| Conversion timing | 70% same month, 30% next | | | (A) |

Results at Apple-equalised Egypt prices:

| Scenario | Payers M1 | M1 gross | M1 net | $4,000? | Cum. M3 | Cum. M6 | Cum. M12 gross | M12 net | $60,000? | Payers 12 mo | Active subs, end |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Low | 3 | $255 | $217 | No | $1,153 | $3,032 | $8,939 | $7,598 | No | 82 | 61 |
| Base | 14 | $1,457 | $1,238 | No (36%) | $6,595 | $17,849 | $58,775 | $49,959 | Almost (98%) | 462 | 396 |
| High | 63 | $7,172 | $6,096 | Yes | $34,414 | $104,088 | $449,782 | $382,315 | Yes | 3,362 | 3,058 |

Payers each target needs:

| Mix | $ per new payer, M1 | Payers for $4,000 | Year-1 $ per payer | Payers for $60,000 |
|---|---|---|---|---|
| All annual, Student, US | $149 | 27 | $149 | 403 |
| All monthly, Student, US | $19.99 | 200 | $94 (4.7 payments at 10% churn, A) | 641 |
| Base blend, US (80/20 tiers, 60/40 plans) | $117 | 34 | $127 (Student) | 473 |
| Base blend, Egypt equalised | $103 | 39 | $113 | 533 |
| Base blend, Egypt regional | $27 | 150 | $30 | 2,018 |

At base rates $4,000 in month 1 needs 2,197 month-1 signups (base has 800); $60,000 over twelve months needs 817. A 7-day trial pushes 30% of conversions into month 2.

## 4. Sensitivity (base, cumulative 12-month gross $58,775)

| Assumption moved | Cum. M12 | Month 1 |
|---|---|---|
| Growth 6% / 18% per month | $42,414 / $82,521 | unchanged |
| Signups or conversion −30% / +30% | $41,142 / $76,407 | $1,020 / $1,894 |
| Price ×0.7 / ×1.3, no elasticity | $41,142 / $76,407 | $1,020 / $1,894 |
| Annual share 45% / 75% | $53,150 / $64,400 | $1,164 / $1,749 |
| Egypt share 50% / 90%, equalised | $60,382 / $57,168 | $1,494 / $1,419 |
| Monthly churn 7% / 15% | $60,009 / $57,031 | unchanged |
| Egypt 100%, regional prices, conversion ×1 / ×3 | $15,184 / $45,553 | — |

Top of funnel (signups × conversion × growth) moves the result most; price moves it 1:1 only if demand is inelastic. Churn barely matters in year one because 60% of payers prepay annually. Geography matters only under regional pricing.

## 5. Judgement on §20's prices for Egypt

Evidence: Student at EGP 999.99/month [S10] is 14% of the EGP 7,000 minimum wage [22b S57] and 20× the local Qbanks at EGP 50/month [22b S37]; the annual tier (EGP 7,999.99) exceeds a month's minimum wage; UWorld sells at EGP 25–45k [22b S3]. Osmosis already regionalises: Full Access EGP 279.99/month and 1,499.99/year in Egypt [S11] against $16.99 and $224 at home [22b S12,S13]. Oncourse and Quizlet keep equalised tiers [S12][S13]. Apple allows a developer's own price per storefront and never adjusts it afterwards [S19][S2]. Elasticity for this audience: UNKNOWN.

Alternative modelled: Egypt Student EGP 299.99 / 1,999.99, Pro EGP 599.99 / 3,999.99. Effect on base: month 1 $1,457 → $731; cumulative M12 $58,775 → $29,949 at unchanged Egyptian conversion, $51,207 at 3×, break-even at ≈3.9×; with 50% international payers and 3× Egyptian conversion, $54,976.

Recommendation, plainly: set those regional Egyptian prices and keep §20's USD prices on every other storefront. An equalised EGP 999.99 converts almost nobody on EGP 7,000 and makes the Free tier the product. The $60,000 target then rests on international payers: commit to ≥50% international acquisition (English UI first, UKMLA/USMLE blueprint tags [22b]) or re-base an Egypt-only year one to ≈$20,000. Measure Egyptian conversion in month 1; below 3× the international rate, Egypt is a loss leader and the dollar target belongs to other storefronts.

## 6. Kill or pivot checkpoints and the week-1 row

| §20 line | Reading | Low | Base | High | Action if under |
|---|---|---|---|---|---|
| Month 1 < $1,000 | gross | $255 FAIL | $1,457 pass (≥14 payers) | $7,172 | Revisit pricing and onboarding; regional Egypt price first |
| Month 3 < $4,000 | cumulative (monthly) | $1,153 ($484) FAIL | $6,595 ($2,779): pass cumulative, fail monthly | $34,414 | Product-market fit; trial 7 → 17 days |
| Month 6 < $10,000 | cumulative (monthly) | $3,032 ($699) FAIL | $17,849 ($4,284) pass | $104,088 | Pivot or shutdown |
| Month 12 < $30,000 | cumulative | $8,939 FAIL | $58,775 pass | $449,782 | Major pivot |

Low fires every criterion from month 1; base passes all four yet misses both headline targets. Gates: ≥14 payers and ≥$1,400 in month 1; ≥$6,500 cumulative by month 3; ≥$17,800 by month 6.

Week-1 row, base: `[Week 1] [280 signups: 35% of month 1 (A)] [Activation 55% = 154] [D1 30% / D7 15% / D30 n/a (A; 22b S56 UNVERIFIED)] [Conv 0%: 7-day trials end in week 2; gate = ≥18 trial starts (6.5%)] [MRR $0–140] [ARR $0–1,700] [vs target: behind by design] [top channel: faculty WhatsApp/Telegram groups (A)] [next lever: day-5 trial reminder, paywall after first win]`.

## 7. Fail-safe implications

| Event | Revenue effect | Mitigation |
|---|---|---|
| Free quotas exhausted (Gemini RPD; Workers AI ≈ 40 daily-active users; D1 100k writes/day) [S4–S6][22f] | Free and Student stall until 00:00 UTC; "unlimited AI tutor" cannot be honoured | Sell priority, not unlimited; quota in entitlement order Pro > Student > Free; on-device fallback; visible meter [22b] |
| Paid model unavailable (Anthropic outage, credits exhausted; months 1–2 by construction) | Pro degrades; refund and churn risk | Ladder Opus → Gemini paid → Workers AI → on-device; never name a model on the paywall; label the state |
| App Store rejects a build | $0, no payout, every checkpoint slips | Submit ≥3 weeks before paid launch; TestFlight cohort; unlock only via IAP (3.1.1) [S20]; regional prices set in App Store Connect, never in-app |
| Apple payout lag, 45 days [S18] | No cash until ≈month 3 | AI budget = 0 in months 1–2; Pro promise built on free quotas |
| Refunds or chargebacks (Apple decides) | Clawback from proceeds | 5% reserve inside the 40% margin; longer trial cuts day-0 cancellations [S17] |

Excluded levers: leaderboards, ranks, challenge-a-friend and a self-learning model are owner-rejected; "concept collision", "doubt heatmap" and "Jev readiness signal" are unbuilt candidates carrying $0, and Jev is rejected as a revenue oracle [22a Layer 8]. Institutional: guideline 3.1.3(c) permits direct sale to faculties [S20]; $8–15 × a 1,000-student faculty is $8–15k a year at ≈100% net, the plan's largest upside, but admin dashboard, LMS and SSO are unbuilt, so $0 here. Above $1M proceeds the commission becomes 30% [S1]; not a year-one risk.

## Sources

S1 https://developer.apple.com/app-store/small-business-program/ · S2 https://developer.apple.com/app-store/subscriptions/ · S3 https://developer.apple.com/programs/ · S4 https://developers.cloudflare.com/workers/platform/pricing/ · S5 https://developers.cloudflare.com/workers-ai/platform/pricing/ · S6 https://ai.google.dev/gemini-api/docs/pricing · S7 https://ai.google.dev/gemini-api/docs/rate-limits; https://tinkerllm.com/blog/gemini-api-free-tier-limits-rate-quotas/; https://aipromptshub.co/blog/gemini-api-free-tier-rate-limits · S9 https://developer.apple.com/news/?id=9o2nwe38 · S10 https://apps.apple.com/eg/app/id1169487026; https://apps.apple.com/us/app/id1169487026 · S11 https://apps.apple.com/eg/app/id646540641 · S12 https://apps.apple.com/eg/app/id6504909373 · S13 https://apps.apple.com/eg/app/id546473125 · S14 https://www.xe.com/en-us/currencyconverter/convert/?Amount=1&From=USD&To=EGP · S15 https://www.revenuecat.com/state-of-subscription-apps-2026-education · S16 https://www.revenuecat.com/state-of-subscription-apps · S17 https://www.revenuecat.com/blog/growth/subscription-app-trends-benchmarks-2026 · S18 https://developer.apple.com/help/app-store-connect/getting-paid/overview-of-receiving-payments · S19 https://developer.apple.com/help/app-store-connect/manage-app-pricing/set-a-price/; https://developer.apple.com/news/?id=dbrszv62 · S20 https://developer.apple.com/app-store/review/guidelines/ · S21 https://gs.statcounter.com/os-market-share/mobile/egypt · S22 https://en.wikipedia.org/wiki/List_of_medical_schools_in_Egypt; https://en.wikipedia.org/wiki/Qasr_El-Eyni_Faculty_of_Medicine,_Cairo_University; https://en.wikipedia.org/wiki/Faculty_of_Medicine,_Ain_Shams_University · S23 https://startupowl.com/reviews/cloudflare-registrar; https://tldspy.com/registrar/cloudflare
