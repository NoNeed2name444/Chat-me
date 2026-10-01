# ISLAMIC VERIFICATION RESEARCH BRIEF, for medical verification (§17, revision 2)

**What this revision is:**
- Revision 2, 1 October 2026, for the rulebook's Task 2 (§5).
- **The owner's clarification (1 Oct):** the subject is *medical* verification, and the Islamic sciences are the method. So each science is paired with the medical check it becomes.
- **What happened to revision 1 (kept in git history):** its verification content is folded into §1–§2. Everything else is kept in substance in Appendix A, and its 59 sources are kept as they were.

**Keys:**
- [S#] is a source.
- **Contested** means scholars disagree. **UNKNOWN** means no evidence was found. **Correction** means the protocol text was wrong.
- Medical sources were checked in PubMed, and every DOI is linked.
- "Today" means what the code did when read on 1 Oct (red-pen-ios personal d5548c1, Chat-me personal).

## 1. The sciences, and the medical check each becomes

| # | Science | What the scholars did | The medical check it becomes |
|---|---|---|---|
| 1 | **Isnad** | Every report carries its chain. Ibn al-Mubarak: without isnad "whoever wished would say whatever he wished" [S11]. Gaps are named by position: mu'allaq (start missing), mursal, munqati' (one link), mu'dal (two or more) [S13]. Verify backwards, latest link first [S6] | Every claim carries its chain: claim → exact passage → source → how it was retrieved → who checked it. Missing links are typed: no source at all (mu'allaq); a review cited for a primary finding (mursal-like); a source that does not contain the claim (munqati') |
| 2 | **Ittisal, tabaqat, tarikh** | Continuity checked by generations, death dates and transmission formulas. Bukhari required a proven meeting; Muslim accepted contemporaneity with a possible meeting. **Contested** [S12][S8][S59]. Sufyan al-Thawri: "we used history against them" [S50]. Tadlis hides a weak link behind 'an, so a mudallis needs explicit sama' [S13] | Dates falsify. A source dated after the lecture cannot be the lecture's basis. A cited guideline edition must have existed at the cited date. A secondary source presented as primary ("citation laundering", the medical tadlis) needs its primary found |
| 3 | **Ilm al-rijal** | Dictionaries record each narrator's name, teachers, students, verdicts and death date (al-Mizzi: ~8,000 narrators); Taqrib grades in 12 ranks [S39][S8]. A majhul narrator is rejected by most, and majhul al-'ayn is lifted when two known scholars transmit from him [S5]. A mubham narrator is rejected even with praise [S6] | **A source registry:** identity; type (guideline, systematic review, RCT, cohort, case report, textbook, lecture, model memory); publisher; date and edition; retraction status; funding; grade. An unidentifiable source ("studies show") is majhul: Unverified until two known sources corroborate it |
| 4 | **Jarh wa ta'dil** | Ten causes of ta'n, including lying, gross error, heedlessness, contradiction, innovation and bad memory [S7]. Explained jarh beats ta'dil "even from thirty" [S5][S6]. **Correction:** tawthiq beats only *unexplained* jarh. Al-Dhahabi: two masters never agreed to authenticate a weak narrator or weaken a reliable one [S10]. Criticism among rivals (kalam al-aqran) is disregarded, especially when it comes from jealousy or school rivalry [S62] | Checkers' verdicts must be explained and sourced. An explained objection ("contraindicated in pregnancy, per [S2]") outweighs bare agreement. Agreement between independent checkers is the strongest label. A rival's unexplained critique (a competitor attacking a drug) is weighed, not trusted |
| 5 | **'Adalah and the innovator rule** | A narrator with a lesser innovation is accepted if he does not propagate it, unless he narrates what supports his innovation; then he is rejected (al-Juzajani) [S61][S6] | **Conflict-of-interest rule:** a source's claim that favours its own sponsor or product is never accepted alone; it needs independent corroboration. Industry-sponsored drug studies report favourable efficacy more often (RR 1.27) and favourable conclusions more often (RR 1.34) [S67] |
| 6 | **Dabt and ikhtilat** | Accuracy is checked by collating with precise narrators [S5][S6]. For trustworthy narrators who became confused late in life, what was taken before the confusion is accepted and what came after is rejected [S60] | The claim must reproduce its source faithfully: numbers, units, population, negation. Reliability depends on the date: a source is trusted for what it said before it was retracted, withdrawn or superseded. A 2019 lecture is judged "correct as taught in 2019, changed in 2023" |
| 7 | **The five conditions of sahih** | Continuity, integrity, accuracy, no shudhudh, no hidden 'illah. All five must hold [S1][S13] | Five automated gates per claim (§2, D) |
| 8 | **Shadh and munkar** | A reliable narrator contradicting more reliable ones is shadh; a weak one contradicting reliable ones is munkar [S2][S13] | A single study against a guideline or systematic review is shadh: held, not adopted. A weak source against strong ones is dropped |
| 9 | **'Ilal** | Hidden defects show only when all chains are collated (Ibn al-Madini): tafarrud, mukhalafa, qara'in [S13][S3]. Al-Hakim illustrated ten kinds [S14]. Ibn Abi Hatim catalogued ~2,840 cases, al-Daraqutni 4,000+ [S13][S15] | Collect every version before judging: preprint, published, erratum, retraction notice; guideline editions; every retrieved passage; the item's own regenerations. A claim only one source makes is flagged (tafarrud) |
| 10 | **I'tibar: mutaba'at and shawahid** | Light weakness is upgraded by corroboration (hasan li-ghayrihi); heavy weakness (lost 'adalah) is not [S54][S6]. Chains that converge on one narrator, the common link, share his weakness and are not independent [S42] | Corroboration counts *independent origins* only: several papers from one trial or one dataset, or all citing one review, are one witness. Covert duplicate publication: 17% of ondansetron trial reports and 28% of the patient data were duplicated, inflating efficacy by 23% [S66]. Citation networks can manufacture "unfounded authority" through bias, amplification and invention [S65] |
| 11 | **Tawatur vs ahad** | Enough independent transmitters at every layer that collusion is impossible [S21]. No fixed number; **contested** [S13][S20]. Ahad is probable (zann), never certain [S20] | Certainty tiers. "Verified, strong": independent high-grade sources agree at every layer, e.g. a guideline and a systematic review from different bodies. Otherwise "Verified, single source". One chain never yields certainty |
| 12 | **Mawdu'at** | "Worst of the weak"; knowingly narrating one without disclosure is unlawful [S4]. **Signs:** disproportionate rewards or punishments; praise of particular groups or places; detailed, dated prophecies; unsuitable or non-Arabic style; fanciful claims; something said "before many" yet reported by none [S63]. Pious forgers "for religion": intent is no defence [S4]. Weak is not fabricated [S18] | **Medical signs of fabrication:** an unresolvable PMID or DOI, or a title that does not match; "all guidelines recommend…" when none is found (said before many, reported by none); disproportionate effects (cures, 100%, never/always); precise numbers with no source; a guideline or trial name that does not exist. A fabricated item is quarantined and never shown as fact, even if the generator "meant well" |
| 13 | **Matn criticism** | Reject what contradicts the Quran, mutawatir Sunna, ijma' or sound reason (al-Khatib) [S17]. A sound isnad does not make a sound matn (Ibn al-Qayyim) [S19]. Early critics did criticise matn (Brown; **contested** against Goldziher and Schacht) [S16] | Check content independently of its chain: physiology, drug class, arithmetic, units and ranges, internal consistency (key vs explanation). A perfectly cited claim that breaks physiology is still wrong |
| 14 | **Al-ta'arud wa al-tarjih** | Ibn Hajar: reconcile; else naskh, if the later text is established; else tarjih; else tawaqquf [S6]. Kamali: reconcile, prefer, abrogate, suspend. Hanafis put naskh before tarjih; **contested** [S26]. Genuine conflict exists only among probable evidence [S26]. Tarjih criteria: mutawatir > mashhur > ahad; better memory; jurist narrator; affirmative > negative; prohibition > permission [S26] | **Conflict order for medical claims**, Ibn Hajar's: a dated, explicit replacement should beat a strength comparison. (§5's order — harmonise, prefer, abrogate — is Kamali's.) Steps: (1) harmonise by scope: population, dose, setting, jurisdiction. Example: stage 1 hypertension is 130–139/80–89 under ACC/AHA 2017 [S73], but NICE diagnoses at clinic ≥140/90 with ABPM/HBPM ≥135/85 [S74]. That is a scope difference, not an error. (2) Dated, explicit supersession. (3) Prefer by evidence grade. (4) Otherwise "Unresolved", with both sides shown |
| 15 | **Naskh** | Needs a later origin and separate texts. Lateness is known only by explicit report, a Companion's statement or consensus, "not by ijtihad" [S25] | Supersession only on explicit, dated evidence. Sepsis-3 (2016) says its definitions "should replace previous definitions" and calls "severe sepsis" redundant [S72], so content teaching SIRS-based "severe sepsis" is flagged "superseded by Sepsis-3 (2016)". A newer date alone never implies supersession |
| 16 | **Maqasid, ihtiyat and the legal maxims** | Five essentials, life among them, at three levels [S35]. Prohibition beats permission; averting harm comes before bringing benefit; doubt cancels a penalty [S26][S56][S57]. Certainty is not removed by doubt [S32][S57] | When evidence stays ambiguous, safety decides: the more cautious statement, no dose specifics, uncertainty marked. A verified item stays verified until equal or stronger evidence arrives; a new claim starts Unverified |
| 17 | **Tahqiq** | The editor presents and never improves. Collate the copies, record the variants, state the criteria for preferring one. No talfiq: never build a text that no witness contains [S40] | Every correction keeps the earlier edition and its reason. Never compose a statement from two sources when neither makes it (a dose from one guideline and an interval from another); a synthesis is labelled as one |
| 18 | **Takhrij** | Trace every report to its primary sources, by several routes [S38]. Tracing overturns grades: al-Hakim's "sahih" became al-Dhahabi's "they never met" [S13] | Resolve every citation to its primary identifier (PMID, DOI, guideline id) by more than one route. Registries disagree about retractions [S70], so check both PubMed and Crossref–Retraction Watch [S69] |
| 19 | **Sama'at and tahammul** | Audition certificates log who heard what, when and where; 6,100+ are digitised [S49]. Transmission modes differ in strength: sama' > ijaza > wijada [S59] | A provenance certificate per item: model and version, prompt version, sources, date, checkers. The retrieval mode is recorded: a quoted passage (sama'), a summary (ijaza), model memory (wijada, the weakest) |
| 20 | **"La adri"** | Malik answered "I do not know" to 32 of 48 questions [S55] | Abstention is a correct answer. "Not established" or "sources disagree" beats a confident guess, and the checker bench should score it that way |

## 2. How this improves the verification layer

| Component | From §1 | Today | Change |
|---|---|---|---|
| A. A chain per claim (isnad plus certificate) | 1, 2, 19 | Voters cite evidence ids [Sn]; passages and verdicts are kept per batch; Chat-me has provenance and source_manifest modules | Store one chain per claim, with the item; show it as "Why trust this?" |
| B. Source registry (rijal) | 3, 5, 6, 18 | Evidence comes from Europe PMC (reviews and guidelines only, recent, with an abstract), MedlinePlus and openFDA (server/evidence.js). Retracted records are not excluded; there are no funding or edition fields | Exclude retracted records, checking both PubMed's "Retracted Publication" [pt] and Crossref–Retraction Watch. Record type, date, edition and funding. Grade every source |
| C. Explained criticism weighs more (jarh wa ta'dil) | 4 | Two votes before Verified (MIN_VERIFY_VOTERS = 2). The four default voters span four model families: Gemini, gpt-oss, Nemotron, Gemma. The writer model never votes on its own item | The two agreeing votes must come from different families, for independence. Explained, cited objections weigh more than bare "supports" |
| D. Five gates per claim | 7 | The pieces exist across stages (claim gate, rules, evidence, votes) but are not reported as five named gates | Report continuity, integrity, accuracy, conformity and hidden defect per claim. Any failure demotes it |
| E. Content checks (matn) | 13 | 14 typed rules, including dose-range, lab ranges, key–explanation conflict and numbers-disagree | Add drug-class and mechanism consistency, together with the DNA brief's missing sensors |
| F. Independent corroboration (tawatur, i'tibar) | 10, 11 | evidenceCount is a model feature; sources are not de-duplicated by origin | Collapse shared origins (same trial, dataset or review). Label strong vs single source |
| G. All-versions sweep ('ilal) | 9 | Chat-me's revalidation and temporal_guard; nothing in the Worker | Before Verified, check whether each cited source has a later erratum, retraction or new edition |
| H. Fabrication screen (mawdu'at) | 12 | Chat-me's citation_integrity. The Worker's own citations come from records it fetched, but references written by a model go unchecked | Resolve and title-match every reference; apply the medical signs list; quarantine what fails |
| I. Conflict order, and "Unresolved" (ta'arud wa tarjih) | 14, 15 | Voters judge, with no explicit order and no Unresolved verdict | Scope → dated supersession → grade → Unresolved. Exam mode leaves out Unresolved items |
| J. Safety tie-break (maqasid) | 16 | Oath items (dose, management, diagnosis) need two votes | When still ambiguous, show the more cautious statement and withhold dose specifics |
| K. Editions, and no talfiq (tahqiq) | 17 | A fix overwrites the item | Keep each edition with its reason; label syntheses |
| L. Stakes against grade | 4, 10, 16 | Partly: the oath items' strictness | P0 needs sahih grade: a connected chain to a guideline or systematic review, and two independent checkers. P1 accepts hasan, with a caveat. P2 may carry light weakness, with a caveat. P0 never rests on weak evidence [S54] |
| M. Abstention | 20 | Unverified exists | Tutor and explanations may say "not established", and the bench scores that as correct |

**Verdict vocabulary.** The GRADE column is this brief's proposed approximate mapping, not an established equivalence.

| Hadith grade | Meaning | App verdict | GRADE certainty of the evidence [S64] |
|---|---|---|---|
| Sahih, corroborated at every layer | five conditions, independent chains | Verified, strong | High |
| Sahih, ahad | five conditions, one chain | Verified, single source | Moderate to high |
| Hasan | lighter accuracy | Verified with caveat; never P0 | Moderate to low |
| Da'if | a condition fails | Unverified | Low to very low |
| Mawdu' | fabricated | Rejected, quarantined | none |
| Tawaqquf | conflict cannot be resolved | Unresolved | none |

## 3. Where this meets the DNA brief (for Task 5)
- **DNA gives the machinery:** layered, independent filters; a sensor and a repair for each error type; checkpoints; rules for bypass and apoptosis [16a §2].
- **The Islamic sciences give the epistemology:**
  - whom to trust: rijal, jarh wa ta'dil;
  - how a claim must connect to its source: isnad, ittisal;
  - how to count corroboration: tawatur, i'tibar;
  - how to resolve conflict: ta'arud wa tarjih, naskh;
  - what to do in doubt: maqasid, tawaqquf, "la adri".
- **Where both agree:**
  - independence counts, not numbers;
  - an explained diagnosis comes before any repair or verdict;
  - fail closed;
  - always record the reason.

## 4. Contested and UNKNOWN
- **Contested:**
  - liqa' vs mu'asara [S12];
  - the minimum number for tawatur [S13][S20];
  - whether naskh or tarjih comes first [S26];
  - whether early critics practised matn criticism [S16].
- **UNKNOWN** (carried from revision 1):
  - the protocol's "7 ways" of 'illah;
  - "thousands per generation" for transmission of the Quran.
- **A proposal, not an established equivalence:** the hadith grade ↔ GRADE mapping in §2.

## Appendix A. Revision 1 material outside medical verification, kept in substance
- **Ilm al-dirayah:** Ibn Jama'a: "rules by which the states of sanad and matn are known". Riwaya transmits; diraya judges chain and text separately [S9]. Ibn al-Salah: later scholars rely on canonical grading. **Contested** [S1].
- **Quranic sciences:**
  - Tafsir: Quran by Quran, then Sunna, then Companions, then Successors (Ibn Taymiyya). Tirmidhi 2952: opinion-based tafsir "has erred" even when right [S24].
  - Compilation: Zayd wrote nothing without two witnesses. Uthman sent one copy to each province [S22].
  - Accepted readings (Ibn al-Jazari): Arabic fit, the Uthmanic rasm, a sahih chain. Ibn al-Hajib demanded tawatur. **Contested** [S23].
  - Naskh details; al-Suyuti kept 20 cases [S25].
- **Evidence hierarchy:**
  - Al-Ghazali: Book, Sunna, ijma', rational proof [S27]; Kamali adds qiyas [S26].
  - "Strength does not consist in number"; qat'i beats zanni [S26]. Ahad is 0.51+, never 1 [S20].
  - The Mu'adh hadith is itself weak [S37].
- **Qiyas, ijma', ijtihad:**
  - The four pillars of qiyas [S26]. Ibn Hazm rejects qiyas [S36]. Sukuti ijma' is **contested** [S26].
  - Al-Shatibi on objectives [S35].
- **Kalam and mantiq:**
  - Ibn Rushd: burhan, jadal, khataba [S29]. Al-Ghazali on logic [S27][S28]. Ibn Taymiyya's attack is **contested** [S30].
  - Grades of assent: yaqin, zann, shakk, wahm (al-Jurjani) [S31]; 100%, 51–99%, 50%, <50% (Milani) [S32]. Taqabul [S34].
  - Yaqin: 'ilm, 'ayn and haqq al-yaqin [S32]; al-Farabi on certitude [S33].
- **Compared with modern methods:**
  - Isnad as a social network (Şentürk) [S41]; authenticity was probabilistic (Hallaq) [S20]; the doubt canon (Rabb) [S56].
  - ICMA [S42]. Raja 2026 ported isnad-rijal to agent provenance, with mixed results [S43].
- **Digital work:** surveys and tools [S44]–[S48].
- **Additional:**
  - Poor Arabic as a sign of forgery [S18].
  - Falak: calculation vs sighting [S52]. Fara'id: 'awl [S53].
  - Tibb nabawi: authority is bound to its domain [S51].
- **Connection principles, for the 3D map:**
  - 'illah-typed edges; atraf indexing;
  - a teacher–student graph; ashbah and furuq;
  - takhrij al-furu' and munasabat; tabaqat layers with a certificate per edge.
  - [S8][S26][S38][S39][S41][S49][S58]
- **Jev mapping:**
  - Noul is one narrator's ahad verdict, so it needs a second, independent verifier before any P0 claim.
  - Choice gives the claim type. Score takes the weakest link, raised by independent chains.
  - Probability bands: <0.5 discard, 0.5 abstain, P0 needs the sahih band.
  - [S13][S20][S26][S31][S32][S43][S54]
- Revision 1's 17 design principles (T1–T27) and its nine-stage application are folded into §2 (A–M).

## Sources
Revision 1's sources S1–S59, unchanged:
- [S1] Ibn al-Salah, Muqaddima 1: https://ar.wikisource.org/wiki/مقدمة_ابن_الصلاح/النوع_الأول
- [S2] Muqaddima 13: https://ar.wikisource.org/wiki/مقدمة_ابن_الصلاح/النوع_الثالث_عشر
- [S3] Muqaddima 18: https://ar.wikisource.org/wiki/مقدمة_ابن_الصلاح/النوع_الثامن_عشر
- [S4] Muqaddima 21: https://ar.wikisource.org/wiki/مقدمة_ابن_الصلاح/النوع_الحادي_والعشرون
- [S5] Muqaddima 23: https://ar.wikisource.org/wiki/مقدمة_ابن_الصلاح/النوع_الثالث_والعشرون
- [S6] Nukhbat al-Fikar (tr.): https://asimiqbal2nd.wordpress.com/wp-content/uploads/2009/06/ibnhajarchosenthoughts.pdf
- [S7] Nuzhat al-Nazar, ten causes: https://shamela.ws/book/25955/219
- [S8] Taqrib ranks, tabaqat: https://kwpublications.com/papers_submitted/17909/
- [S9] Tadrib al-Rawi definitions: https://www.alukah.net/sharia/0/169021/
- [S10] al-Dhahabi, Fath al-Mughith: https://www.islamweb.net/ar/library/content/82/369/
- [S11] Sahih Muslim intro 27, 32: https://sunnah.com/muslim/introduction/26
- [S12] Mu'an'an dispute: https://www.islamweb.net/en/fatwa/86172/
- [S13] Hasan, Science of Hadith: https://d1.islamhouse.com/data/en/ih_books/single/en_A_Introduction_to_the_Science_of_Hadith.pdf
- [S14] al-Hakim's ten ajnas: https://www.alukah.net/sharia/0/53530/
- [S15] al-Daraqutni: https://www.europeanproceedings.com/article/10.15405/epsbs.2020.10.02.73
- [S16] Brown, ILS 15 (2008), doi:10.1163/156851908X290574
- [S17] al-Kifaya criteria: https://mjs.um.edu.my/index.php/JUD/article/download/3227/1300/8829
- [S18] Forgery signs: https://www.abuaminaelias.com/dailyhadithonline/2016/01/19/ibn-jawzi-weak-mawdu-hadith/
- [S19] Ibn al-Qayyim, ikhtilaf: https://tsaqafah.journal.unida.gontor.ac.id/index.php/tsq/article/download/35/2
- [S20] Hallaq 1999: https://almuslih.org/wp-content/uploads/Library/Hallaq,%20W%20-%20The%20authenticity.pdf
- [S21] Tawatur conditions: https://www.islamweb.net/en/article/183231
- [S22] Bukhari 4986; two witnesses: https://sunnah.com/bukhari:4986
- [S23] Ibn al-Jazari; Ibn al-Hajib: https://islam.stackexchange.com/questions/5866/
- [S24] Ibn Taymiyya; Tirmidhi 2952: https://ia803205.us.archive.org/3/items/dawrah2021/Muqaddimah-Fi-Usool-Al-Tafsir.pdf
- [S25] Naskh: http://hmazeem.blogspot.com/2018/12/theory-of-abrogation-naskh.html
- [S26] Kamali, Principles pt 2: https://d1.islamhouse.com/data/en/ih_books/parts/Principles_of_Islamic_Jurisprudence/en_Principles_of_Islamic_Jurisprudence_Part_2.pdf
- [S27] al-Ghazali, Mustasfa: https://shamela.ws/index.php/book/5459
- [S28] al-Munqidh on logic: https://isamveri.org/pdfdrg/D03380/2010_3_2/2010_3_2_VURALM.pdf
- [S29] Ibn Rushd, Fasl al-Maqal: https://dergipark.org.tr/tr/download/article-file/10185
- [S30] Hallaq 1993, via https://link.springer.com/rwe/10.1007/978-1-4020-9729-4_303
- [S31] al-Jurjani, Ta'rifat: https://www.ghazali.org/arabic/jurjani-tarifat.htm
- [S32] Milani ch. 7: https://al-islam.org/thirty-principles-islamic-jurisprudence-sayyid-fadhil-milani/chapter-7-certainty-not-challenged
- [S33] al-Farabi certitude: https://plato.stanford.edu/entries/al-farabi-psych/
- [S34] Taqabul: https://ar.wikipedia.org/wiki/تقابل_(منطق)
- [S35] al-Shatibi: https://www.alukah.net/sharia/0/113245/
- [S36] Ibn Hazm: https://www.ajis.org/index.php/ajiss/article/download/1099/432/1578
- [S37] Abu Dawud 3592: https://sunnah.com/abudawud:3592
- [S38] al-Tahhan; Tuhfat al-Ashraf: http://tuhfataltullab.blogspot.com/2013/08/a-summary-of-usul-al-takhrij.html
- [S39] Tahdhib al-Kamal; rijal: https://en.wikipedia.org/wiki/Tahdhib_Al-Kamal_fi_Asma'_Al-rijal
- [S40] Harun, Tahqiq al-Nusus: https://dergipark.org.tr/tr/download/article-file/5067005
- [S41] Şentürk: https://www.sup.org/books/title/?id=9033
- [S42] ICMA: https://en.wikipedia.org/wiki/Isnad-cum-matn_analysis
- [S43] Raja 2026: https://arxiv.org/abs/2607.24117
- [S44] Hakak 2020: https://ouci.dntb.gov.ua/en/works/7Bm38G39/
- [S45] Review 2026: https://link.springer.com/article/10.1007/s00521-026-12188-8
- [S46] Narrator SNA: https://www.sciencedirect.com/science/article/pii/S1319157821000215
- [S47] Browser extension: https://arxiv.org/pdf/1701.07382
- [S48] Computational/blockchain: https://www.semanticscholar.org/paper/8aaa2fc1d76f633a64b14d706351001efddf98ed
- [S49] Audition certificates: https://www.leidenspecialcollectionsblog.nl/articles/mapping-medieval-scholarship-arabic-audition-certificates-from-the-leiden-special-collection
- [S50] Sufyan al-Thawri: https://www.islamweb.net/ar/article/1431/
- [S51] Ibn Khaldun; Qadi 'Iyad: https://hadithnotes.org/prophetic-medicine-between-revelation-and-traditional-knowledge/
- [S52] Hisab/ru'ya: https://www.masud.co.uk/hisab-ruya-or-matla-al-budur/
- [S53] 'Awl: https://faraidhub.com/blog/awl-explained.html
- [S54] Weak hadith: https://islamqa.info/en/answers/44877
- [S55] Malik "la adri": https://seekersguidance.org/articles/general-artices/a-motto-from-our-masters-i-do-not-know/
- [S56] Rabb, Doubt in Islamic Law: https://www.cambridge.org/core/books/doubt-in-islamic-law/3F33884C01782919E73DC857A504D434
- [S57] Legal maxims: https://www.dar-alifta.org/en/article/details/361/islamic-legal-maxims
- [S58] Furuq; ashbah: https://islamiclaw.blog/2018/10/17/al-qarafis-collection-of-legal-distinctions/
- [S59] Transmission formulas: https://hadithanswers.com/the-difference-between-haddathana-and-akhbarana/
Added in revision 2. The medical articles were retrieved from PubMed; each DOI is linked:
- [S60] Ibn al-Salah, Muqaddima, naw' 62 (those who became confused late in life): https://ar.wikisource.org/wiki/مقدمة_ابن_الصلاح/النوع_الثاني_والستون
- [S61] Ibn Hajar, Nukhbat al-Fikar (the innovator's narration): https://www.kalamullah.com/Books/Nukhbat_al_Fikr.pdf
- [S62] al-Dhahabi on kalam al-aqran: https://www.salafiri.com/contextualising-hafiz-adh-dhahabis-statement-on-criticising-contemporaries/
- [S63] Signs of fabricated hadith (eight criteria): https://islamonline.net/en/fabricated-hadiths-3/
- [S64] Guyatt et al. 2008, GRADE, BMJ 336:924, PMID 18436948, https://doi.org/10.1136/bmj.39489.470347.AD
- [S65] Greenberg 2009, citation distortion, BMJ 339:b2680, PMID 19622839, https://doi.org/10.1136/bmj.b2680
- [S66] Tramèr et al. 1997, covert duplicate publication, BMJ 315:635, PMID 9310564, https://doi.org/10.1136/bmj.315.7109.635
- [S67] Lundh et al. 2017, industry sponsorship and research outcome, Cochrane MR000033, PMID 28207928, https://doi.org/10.1002/14651858.MR000033.pub3
- [S68] Fang, Steen & Casadevall 2012, misconduct and retractions, PNAS 109:17028, PMID 23027971, https://doi.org/10.1073/pnas.1212247109
- [S69] Crossref, Retraction Watch data in the Crossref API: https://www.crossref.org/documentation/retrieve-metadata/retraction-watch/ ; acquired 12 Sep 2023: https://www.infodocket.com/2023/09/12/retaction/
- [S70] PubMed's "Retracted Publication" [pt]: https://mdanderson.libanswers.com/faq/266875 ; registries disagree on retracted status: https://pmc.ncbi.nlm.nih.gov/articles/PMC8243230/
- [S71] Continued use of retracted papers in biomedicine (Quantitative Science Studies 2021): https://direct.mit.edu/qss/article/2/4/1144/107356/
- [S72] Singer et al. 2016, Sepsis-3, JAMA 315:801, PMID 26903338, https://doi.org/10.1001/jama.2016.0287
- [S73] Whelton et al. 2017 ACC/AHA high blood pressure guideline, Hypertension 71:e13, PMID 29133356, https://doi.org/10.1161/HYP.0000000000000065
- [S74] NICE NG136, Hypertension in adults: https://www.nice.org.uk/guidance/ng136/chapter/recommendations

## MEDICAL IMPROVEMENTS THIS UNLOCKS

Each item says what the student gets, the method behind it, and its status.

1. **"Why trust this?" on every claim.**
   - Each claim shows its chain:
     - the exact passage quoted;
     - the source's title, type, date and grade;
     - how it was retrieved (quoted, summarised or from model memory);
     - which checkers judged it, when, with what verdict and reason.
   - The student sees the evidence behind a dose or a diagnostic criterion, and learns appraisal while studying.
   - Method: isnad and sama'at [S11][S49].
   - Status: partly built. Voters cite [Sn] ids; the per-claim record and the view are new.
2. **No retracted evidence.**
   - Every source is checked against PubMed's "Retracted Publication" type and the Crossref–Retraction Watch data before it can support a claim.
   - Why both: the registries disagree [S70], retracted papers keep being cited [S71], and 67.4% of retractions are for misconduct [S68].
   - Method: jarh of a discredited narrator [S5][S7].
   - Status: new. Today Europe PMC results are filtered to reviews and guidelines but not for retraction.
3. **Conflict-of-interest weighting.**
   - A finding that favours its own sponsor's product is not accepted alone; it needs independent corroboration.
   - Why: industry-sponsored drug studies report favourable efficacy (RR 1.27) and conclusions (RR 1.34) more often [S67].
   - Method: the innovator-propagandist rule [S61].
   - Status: design. Funding metadata is only partly available from free sources.
4. **Corroboration that counts independent origins only.**
   - Papers from one trial, one dataset or one review count as one witness.
   - Why: duplicated trial reports inflated ondansetron's apparent efficacy by 23% [S66], and citation chains can manufacture authority [S65].
   - Labels: "Verified, strong" when independent high-grade sources agree; "Verified, single source" otherwise.
   - Method: tawatur and the common link [S21][S42].
   - Status: new.
5. **Guideline differences explained, not marked wrong.**
   - The app knows which guideline system the student's exam follows (Egypt, UK, US). It checks against that system and shows the other system as a note.
   - Example: stage 1 hypertension is ≥130/80 under ACC/AHA 2017 but needs clinic ≥140/90 under NICE [S73][S74].
   - Method: harmonise by scope first [S6][S26].
   - Status: new. The exam catalogue already exists to anchor it.
6. **Superseded teaching flagged, with what replaced it.**
   - Content built on replaced definitions carries the replacing source and its date.
   - Example: SIRS-based "severe sepsis" is superseded by Sepsis-3 (2016), which says its definitions "should replace previous definitions" [S72].
   - Only explicit replacements count; a newer date alone never does.
   - Method: naskh rules [S25].
   - Status: partly built (the temporal guard). The guideline-edition registry is new.
7. **"Unresolved" as an honest verdict.**
   - When scope, supersession and evidence grade cannot settle a conflict, the student sees both positions with their sources instead of a coin toss. Unresolved items stay out of exam mode.
   - Method: tawaqquf [S6][S26].
   - Status: new.
8. **Explained objections outweigh bare agreement.**
   - One checker's specific, sourced objection ("contraindicated in pregnancy per [S2]") outweighs any number of unexplained "supports" votes.
   - The two votes needed for Verified must come from different model families. The pool already spans Gemini, gpt-oss, Nemotron and Gemma, but two Google votes can still verify an item today.
   - Method: explained jarh, plus the independence of tawatur [S5][S6][S21].
   - Status: partly built.
9. **Strictness that matches the stakes.**
   - Doses, contraindications, management and diagnostic criteria need sahih-grade support: a connected chain to a guideline or systematic review, and two independent checkers.
   - Lower-stakes facts may carry a caveat. A dose never rests on weak evidence.
   - Method: Ibn Hajar's conditions for weak reports, and ihtiyat [S54][S57].
   - Status: partly built (oath items need two votes); the full table is new.
10. **Fabrication caught by its signs.**
    - Signs: unresolvable PMIDs or DOIs and title mismatches; "all guidelines recommend…" when none is found; cures and 100% effects; precise unsourced numbers; guidelines or trials that do not exist.
    - Such items are quarantined and never shown as fact.
    - Method: ilm al-mawdu'at [S4][S63].
    - Status: partly built (Chat-me's citation_integrity). References written by a model in the Worker path are unchecked.
11. **Reliability that depends on the date.**
    - A source is trusted for what it said before it was retracted, withdrawn or superseded.
    - A lecture is judged as of its date, so the student sees "taught this way in 2019; changed in 2023" rather than "your lecture is wrong".
    - Method: ikhtilat [S60].
    - Status: new.
12. **Editions, and no stitched facts.**
    - Every correction keeps the earlier edition and the reason, so the student sees what changed and why.
    - No statement is composed from two sources that neither makes, such as a dose from one guideline with an interval from another. Any synthesis is labelled as one.
    - Method: tahqiq and the ban on talfiq [S40].
    - Status: new.
13. **"I don't know" from the tutor.**
    - Explanations and the tutor may answer "not established" or "sources disagree" instead of guessing, and the checker bench counts that as correct.
    - Method: Malik's "la adri" [S55].
    - Status: partly built (Unverified exists); tutor abstention is new.
14. **Evidence literacy as a by-product.**
    - Verdicts sit beside a GRADE-style certainty label (high, moderate, low, very low) [S64], so students learn how strong the evidence behind each fact is, as clinicians must.
    - Method: graded verdict vocabulary [S6][S13].
    - Status: new display.
