# DNA RESEARCH BRIEF (§16a, revision 2)

Revision 2, 1 October 2026, for the rulebook's Task 1 (§4, the DNA goal). Revision 1 is kept in git history. Nothing it said is dropped: its science stays below, and its 3D-theme principles move to Appendix A. This revision adds:
- transcription, the genetic code and translation;
- kinetic proofreading;
- RNA and protein quality control, and replication licensing;
- the mapping onto §4's eight verification layers;
- the DNA way to code;
- the medical improvements.

Evidence:
- PubMed (NCBI) records, every DOI resolved;
- NCBI Bookshelf;
- the medication-safety lists named under Sources.

Every number carries a key [Sn]. **Contested** marks a number the literature disputes. UNKNOWN means it was not found.

## 1. The science

### Structure
- A–T / G–C pairing, ~10.4 bp per turn [S13]. The rise is 3.4 Å per bp in B-form [S11].
- B-DNA is right-handed, 10 bp/turn. A-DNA is right-handed, 11 bp/turn. Z-DNA is **left**-handed, 12 bp/turn [S11].
- Pair stability is contextual: nearest-neighbour stacking sets ΔG and Tm [S10].
- Two complementary strands mean every base has a backup. Repair can cut out a damaged strand and rebuild it from its partner [S13].
- The nucleosome core is 146–147 bp on a histone octamer [S6], repeating every ~200 bp [S54].
- Telomeres are TTAGGG repeats [S11]. Centromeres are α-satellite arrays of a 171-bp monomer, only 50–75% identical [S12].

### Replication
- **Three sequential filters:**
  1. Base selection: ≈10⁻⁵/bp.
  2. 3'→5' exonuclease proofreading: escape ≈10⁻².
  3. Mismatch repair: a further ~10²–10³× [S1][S2][S3].
- **Net error:** ≈10⁻⁹–10⁻¹¹/bp. This is **contested**: it is near 10⁻¹⁰ and varies by polymerase, error type and sequence context [S1][S3].
- **Germline outcome:** 1.20×10⁻⁸ mutations/nt/generation, plus ~2 per year of the father's age [S4].
- **Licensing:** origins are licensed once, in late mitosis and G1, by loading MCM2–7. They fire only in S phase. The two phases never overlap, so no stretch is copied twice and none is left uncopied [S61].
- **Okazaki fragments:** 1000–2000 nt in bacteria and 100–200 nt in eukaryotes, where nucleosome spacing sets the size [S9].
- **Ribonucleotides:** ~2 are misinserted per kb and cleared by RNase H2-initiated repair (RER) [S21].

### Transcription
- **What it is:** RNA polymerase copies one gene into short-lived RNA.
- **Error rate:** 10⁻³–10⁻⁵/nt, and 1–3% of elongation complexes misincorporate. This is **contested**; other estimates give 10⁻⁵–10⁻⁶ [S5].
- **Proofreading by backtracking:** after a misincorporation the polymerase steps back. TFIIS (GreA/B in bacteria) stimulates cleavage of the error-bearing 3' end, and copying resumes [S5].
- **The budget principle:** RNA is cheap and disposable, so its fidelity is ~10⁵× looser than the genome's. Accuracy is spent in proportion to how long an error lasts and how far it spreads [S5][S50].

### The genetic code and translation
- **The code:** 64 codons; 61 name the 20 amino acids and 3 are stops. Most amino acids have several codons (degeneracy) [S13].
- **Wobble:** the third codon base pairs loosely, so many third-position changes are silent [S57].
- **The code minimises errors:**
  - A likely error (a point mutation or a misreading) usually gives the same amino acid or a chemically similar one.
  - Once translation biases are weighted in, only ~1 in 10⁶ random codes does better [S55].
- **Charging tRNAs:** aminoacyl-tRNA synthetases edit a wrong amino acid both before and after transfer. The pre-editing error is ~10⁻⁴ [S50].
- **Decoding, in two stages separated in time:**
  1. Initial selection, before GTP hydrolysis.
  2. Proofreading, after it.
  - Induced fit speeds up only correct codon–anticodon pairs [S58].
  - Net error: 10⁻³–10⁻⁴ per codon [S50].
- **Kinetic proofreading:**
  - An energy-paid delay between two discriminations lets a system beat the accuracy a single binding step allows. In the ideal case the error fraction squares (f → f²) [S56].
  - Accuracy costs energy and time. Real systems tend to be "fast while errors stay tolerable", not maximally accurate [S64].

### RNA and protein quality control
- **Nonsense-mediated decay:** a stop codon more than ~50–55 nt upstream of the last exon–exon junction marks the mRNA as faulty. It is degraded, not translated into a truncated protein [S59].
- **Unfolded protein response:** three branches (IRE1, PERK, ATF6) raise the ER's folding capacity. If the stress stays unmitigated, they switch to apoptosis [S60].

### Repair

| Pathway | Sensors | Effectors | Error rate | Disease |
|---|---|---|---|---|
| Direct reversal | lesion-specific | photolyase; MGMT, no patch | UNKNOWN [S14] | MGMT alkylator resistance [S14] |
| BER | glycosylases (UNG, OGG1) | APE1, pol β, LIG3/XRCC1; 1-nt patch | high-fidelity; ~10⁴–2×10⁴ abasic sites/cell/day [S51] | MUTYH polyposis [S51] |
| NER | XPC–HR23B (GG); stalled pol II (TC) | TFIIH, XPA, RPA, XPG, ERCC1-XPF; excises **24–32 nt** [S15] | high-fidelity, pol δ/ε fills | xeroderma pigmentosum, Cockayne [S14] |
| MMR | MutS / MSH2-MSH6 | MutL/MLH1-PMS2, nicking, exo1, pol δ | loss → 225× (yeast null) to 388×/mitosis (Msh2-null) [S17] | Lynch, MSI-high [S16][S17] |
| DSB–HR | MRN, ATM, RPA-ssDNA | BRCA1/2, RAD51; template-directed | high-fidelity [S18] | BRCA1/2 cancers [S18] |
| DSB–NHEJ / MMEJ | Ku70/80; PARP1 | DNA-PKcs, LIG4/XRCC4; pol θ | error-prone, MMEJ always deletes; rate UNKNOWN [S18] | LIG4 syndrome, ataxia telangiectasia [S18] |
| ICL (Fanconi) | FANCM at stalled fork | FA core → FANCD2/I ubiquitination → nucleases, HR | mutagenic if mis-routed [S18] | Fanconi anaemia [S18] |
| TLS | PCNA ubiquitination | pol η, ι, κ, ζ, Rev1; no proofreading | ≈10⁻²–10⁻³/base, approximate [S19][S20] | XP-variant (POLH) [S19] |
| RER | RNase H2 (A/B/C) | FEN1, pol δ, PCNA/RFC, LIG1 | accurate; failure → single-ended DSBs [S21] | Aicardi–Goutières [S22] |

- **Sensors are specialists:** at least 11 mammalian DNA glycosylases each recognise a few related lesions, with some overlap. They find damaged bases among a vast excess of normal ones without spending energy [S62].
- **The supply is cleaned before copying:** MTH1 (NUDT1) sanitises the nucleotide pool, so oxidised precursors are never built in [S63].
- **Decay is constant:** hydrolysis, oxidation and non-enzymatic methylation go on all the time in vivo. Repair is a standing load, not an emergency [S65][S51].
- **Pathways are defences that can be exploited:** cells without BRCA1/2 (no homologous recombination) die when PARP is inhibited. This "synthetic lethality" became a cancer therapy [S67][S68].

### Checkpoints
- **Ordered gates** [S23]:
  1. G1/S: p53→p21, Rb/CDK inhibitors.
  2. Intra-S: ATR–Chk1.
  3. G2/M: ATM–Chk2 inhibits Cdc25 and sustains Wee1.
  4. The spindle assembly checkpoint (SAC).
- **One defect holds everything:** the SAC blocks anaphase while any single kinetochore is unattached [S23].
- **p53 dynamics decide fate:** pulsed p53 leads to repair, sustained p53 to senescence [S24].
- Checkpoints arrest; they do not repair [S23].

### Apoptosis
- **Intrinsic route:** BH3-only proteins → BAX/BAK pore → cytochrome c → apoptosome → caspase-9 [S25].
- **Extrinsic route:** Fas/TNFR → DISC → caspase-8.
- Both converge on executioner caspases 3/6/7 in one irreversible commitment step [S25].
- **Controlled demolition:** contents are packaged, not spilled as in necrosis, and the ways a cell can die are formally typed [S25][S26].

### SOS response
- **A specific trigger, not general stress:** RecA polymerises on ssDNA at stalled forks (RecA\*), which drives LexA autocleavage [S33][S34].
- **De-repression is ordered:** more than 40–50 genes, accurate repair first and error-prone Pol IV/V late. Prolonged induction creates a hypermutator state [S33][S35].
- **Recovery is automatic:** RecA\* depletes and the self-regulating LexA re-accumulates [S33].
- **The human analogue:** escalation goes repair → arrest → senescence → apoptosis, through p53 dynamics [S24].

### Mutation types

| Type | Mechanism | Consequence |
|---|---|---|
| Silent | change at a degenerate codon position | none; degeneracy absorbs it [S49] |
| Missense | substitution changes an amino acid | compiles, behaviour altered — hardest to see [S29] |
| Nonsense | substitution creates a stop | truncation / NMD [S29][S59] |
| Insertion / deletion (in-frame) | slippage; ID signatures | local gain/loss of residues [S29] |
| Frameshift | indel not a multiple of 3 | whole downstream frame corrupt [S29] |
| Duplication | unequal crossover, slippage | dosage change, redundancy [S29] |
| Inversion | two breaks, reversed rejoin | order and orientation reversed [S29] |
| Translocation | rejoining across non-homologues | fusion genes, misplaced regulation [S29] |
| Doublet / clustered | one event hits adjacent bases | 11 DBS + 4 clustered signatures catalogued [S29] |
| Repeat expansion | slipped-strand mispairing | Huntington: 36–39 CAG reduced penetrance, ≥40 full [S30] |
| Aneuploidy | SAC failure, missegregation | whole-chromosome dosage catastrophe [S23] |
| Somatic vs germline | post-zygotic vs gametic | clonal vs heritable, 1.2×10⁻⁸/nt/gen [S4] |

- **Mutational signatures:** the pattern of errors names the process that caused them: 49 single-base, 11 doublet and 17 indel signatures, each tied to a mutagen or a repair defect [S29].

### Fidelity

| Process | Error rate | Why |
|---|---|---|
| Base selection alone | ≈10⁻⁵/bp | geometric + H-bond discrimination in the active site [S1][S2] |
| + proofreading | ≈10⁻⁷/bp (escape ~10⁻²) | excises the just-inserted error before extension [S2][S3] |
| + MMR = net replication | ≈10⁻⁹–10⁻¹¹/bp (**contested**) | strand-directed review against the parent [S1][S3] |
| Transcription | 10⁻³–10⁻⁵/nt (**contested** vs 10⁻⁵–10⁻⁶) | RNA is disposable; Gre/TFIIS resolve backtracking [S5] |
| tRNA charging (aaRS) | ~10⁻⁴ pre-editing | activation is the weak step, proofread in cis and trans [S50] |
| Translation | 10⁻³–10⁻⁴/codon | two-stage selection with kinetic proofreading; bad protein is degraded, not inherited [S50][S58] |
| TLS | ≈10⁻²–10⁻³/base, approximate | Y-family polymerases lack proofreading [S19][S20] |

### Chromatin and epigenetics
- **Scale:** ~2 m of DNA per cell fits in a ~10 µm nucleus. Nucleosomes are the first level of compaction [S54][S6].
- **The 30-nm fibre is contested:** ChromEMT finds disordered 5–24 nm chains in human cells. 30-nm fibres appear only in chicken red-cell preparations [S7].
- **Domains:** promoters sit in nucleosome-depleted regions [S28]. TADs nest in A/B compartments, and their boundaries limit which regions can touch [S8].
- **Methylation:** 5-methylcytosine at CpG is written by DNMT3A/3B, maintained by DNMT1 and erased by TET oxidation. The marks gate expression without changing the sequence [S27].
- **Remodellers:** four families (SWI/SNF, ISWI, CHD, INO80) reposition nucleosomes [S28].

## 2. How this improves the verification layer

Each of §4's eight layers, with the DNA mechanism it copies, what Stethoscore does today (read in the code on 1 Oct, red-pen-ios personal d5548c1) and the change it implies.

| §4 layer | DNA mechanism | Rule for the layer | Stethoscore today | Change |
|---|---|---|---|---|
| 1 Proofreading during generation | Polymerase geometry + 3'→5' exo excise an error before the next base is added [S1][S2]; the ribosome's two-stage selection [S58]; kinetic proofreading [S56] | Check each unit as it is written, before anything is built on it. Make a second, independent discrimination after a delay | Prompts demand strict JSON. MCQRepair and the 14 rules run after the whole batch. Voters are told to "first work out the single best answer yourself", but they are shown "Keyed answer" and the explanation first (server/accuracy-rules.js itemText) | Validate each item as it is parsed and re-ask only for the one that failed. Add a **blind re-solve** as the second discrimination: stem and options only, key and explanation hidden, then compare |
| 2 Mismatch repair | MMR reads the new strand against the parent; a strand signal says which copy is wrong [S16] | The source is the template and the generated text is the new strand. A disagreement is presumed to be the output's error, unless evidence shows the source itself is out of date | sourceMatch, the evidence stage, and voters told "the lecture itself can be wrong" | When the lecture disagrees with current evidence, tell the student ("your lecture says…, current evidence says… [source]") instead of correcting silently |
| 3 Targeted excision | BER removes one base; NER removes 24–32 nt. The patch fits the lesion [S15][S62] | Repair the smallest unit that holds the error: one option, one sentence, one number | Voters can fix by field: key, explanation, answer, text | Patch at claim level: split an explanation into atoms (server/claims.js decomposeClaim) and rewrite only the atom that failed, from evidence |
| 4 Major rebuild | HR rebuilds a broken stretch from an intact homologous template. NHEJ rejoins fast but leaves indels [S18] | An item too damaged to patch (two or more severe hits, truncated, garbled) is regenerated whole from its source passage, then re-enters Layer 1. Fragments are never stitched together | No rebuild path; damaged items are flagged | Add the rebuild: once, capped, and after that Layer 7 |
| 5 Checkpoints | G1/S, intra-S and G2/M arrest progress until damage clears; the SAC holds anaphase for one unattached kinetochore [S23] | One unresolved check holds that unit. Arrest; never improvise | Code: preflight, Linux suites, Mac app-build. Content: two votes before Verified (MIN_VERIFY_VOTERS = 2); the question bank keeps an item only if every check passes, then a person reviews it | Code that touches data or UI waits for the Mac check on its own branch before merging; low-risk changes merge fast (the speed–accuracy finding [S64]). Content gets a release checkpoint before exam mode, sharing or export |
| 6 Emergency bypass | TLS polymerases copy past damage at 10⁻²–10⁻³ error, only when a fork stalls. SOS induces the mutagenic Pol V last [S19][S33][S35] | Bypass comes last, is typed, marked and temporary, and is always followed by repair | Breakers and kill switches (server/breakers.js, switches.js). Closed means Unverified or queued. A gate failure is asked again later | Never bypass dose, management or diagnosis items (the oath items): they wait. Show "unverified since <time>". Guarantee a re-check when a breaker closes |
| 7 Apoptosis | p53-led, irreversible, clean demolition when damage persists; senescence is permanent arrest [S24][S25] | An unrecoverable item is removed cleanly with a typed reason, never half-shown | The question bank drops items with their reasons; the app labels items Flagged | Quarantine generated items: out of review scheduling and mock exams, with the reason and a rebuild button. A student's own notes are never deleted: that is senescence, not apoptosis |
| 8 Damage response triage | Lesion-specific sensors (glycosylases, MutS, XPC, Ku, PARP1, ATM/ATR) send each lesion to its own pathway [S14][S18][S62]. Signatures name the mutagen [S29] | Every error gets a type, and each type has one sensor, one repair, one severity and one escalation | 14 typed rules (dose-range, lab-unit, lab-implausible, reference-range, negation-mismatch, numbers-disagree, direction-conflict, key-called-wrong, key-explanation-conflict, no-key, duplicate-option, all-and-none, all-above-contradiction, non-answer-position), plus the claim gate's relation, polarity, time and population checks. Voters' issues are free text | Sort voters' issues into the same types. Add the missing sensors (MEDICAL IMPROVEMENTS 2). Count errors by type for each model, prompt and source |

**Rules across all layers:**
1. **Independence multiplies; correlation does not.** 10⁻⁵ → 10⁻¹⁰ came from three mechanistically different filters [S1][S3]. So checkers must differ in mechanism: rules, retrieval, a blind solve, and a vote from a different model family. Each layer's catch rate is measured on seeded errors (checker-bench.yml, accuracy-bench.yml), not assumed.
2. **The error budget follows persistence and reach.** Genome ≈10⁻¹⁰, RNA ≈10⁻⁵, protein ≈10⁻⁴ [S1][S5][S50]. In the app:
   - **"Germline":** the shared question bank and exam mode. Strictest.
   - **"Somatic":** a student's own generated set.
   - **"Transcript":** a passing chat answer. Fast, but the safety sensors are always on.
3. **Fail closed.** A sensor that cannot run never counts as "no damage": unknown means Unverified.
4. **Fixed order.** Repair before bypass, bypass before death. De-escalate automatically when the cause clears [S33].
5. **Clean the supply.** Errors in the input (transcript, OCR) are fixed before generation, not after [S63].

## 3. How this improves the way I code

The DNA coding contract. Every code change from now on follows it.

| Biology | Coding rule | Where it shows in these repos |
|---|---|---|
| Nucleotides and base pairing | The smallest units carry meaning that fits only its partner. Names carry units (doseMg, seconds); types make a unit slip fail to build | WardToken → WardPalette table; units in dose names |
| Codon and reading frame | One statement, one effect. Explicit start and stop: a guard at entry, one result out, no fall-through | exhaustive switches, guard clauses |
| Error-minimising code [S55] | Make the likeliest mistake the cheapest: safe defaults, enums over strings, exhaustive switches, defaults that fail closed | AccuracySendOutcome.of: no answer → queued, never done |
| Gene: promoter, exons, terminator | A function has a precondition (when it may run), a body and a defined result, and can be tested alone | Foundation-only logic files, each with a Linux suite |
| Operon | Features that switch together live together behind one switch | switches.js (STETHOSCORE_OFF); PersonalBuild.isOn |
| Alternative splicing | Variants are assembled from one source, never forked | make_swiftpm.py variants core, core1, core2 |
| Chromatin, TADs, A/B compartments | Folders are domains. Boundaries limit imports. Hot code is kept apart from cold | §3c folders; Shared/Ward names no Space type; Playgrounds chunks with stand-ins |
| Central dogma (no write-back) | One source of truth. Derived copies are generated, never hand-edited | WardPalette → colour tokens; tools/swift_suites.txt read by every runner |
| Proofreading exonuclease | Check each edit before building on it, within seconds | tools/preflight.sh; swift_suites.py --affected |
| MMR strand signal | Review every change as a diff against its parent branch | git diff origin/personal before any merge |
| Patch size = lesion size [S15] | The smallest diff that fixes the defect; never a rewrite for a typo | |
| HR, not NHEJ [S18] | Rebuild from a known-good sibling pattern; never stitch fragments together | new suites copy an existing suite's shape |
| Checkpoints and SAC [S23] | One failing check stops the push | preflight exit code; CI gates |
| Licensing [S61] | Run-once guards and idempotency keys | cloud job ids; the once-per-process splash |
| Telomeres | Every retry and loop has a cap | a 429 waits once, at most 30 s; transcription retries once |
| Kinetic proofreading [S56] | Irreversible steps need two independent confirmations | Worker deploys need the owner's word; never force-push |
| Speed–accuracy [S64] | Spend checks where errors cost most (data, medical claims); keep everything else fast | data paths fully tested; docs merge fast |
| Diploidy and paralogs | Redundancy that differs in mechanism | library backups with set-aside copies; free-chain fallbacks across providers |
| Apoptosis vs necrosis [S25] | Undo cleanly: revert commits, write atomically, leave no partial state | revert commits, never force-push |
| Mutational signatures [S29] | Classify a bug before fixing it. A recurring pattern means finding its cause (a prompt, a model, a habit) | |

## 4. Contested and UNKNOWN
- **Contested:**
  - net replication fidelity (10⁻⁹–10⁻¹¹);
  - the transcription error rate;
  - how "optimal" the genetic code is, which depends on the error-cost function chosen [S55];
  - the 30-nm fibre.
- **UNKNOWN:**
  - the error rate of direct reversal;
  - the per-event indel rate for NHEJ vs MMEJ in human cells;
  - synaptic delay in ms;
  - synapses per human cortical neuron.

## Appendix A. Carried over from revision 1, for the 3D app and the folders
- **Neuron facts:**
  - Four zones: soma, dendrites (input), axon (output) and terminals. Inputs are read out at the axon hillock [S37].
  - Chemical synapses are unidirectional. Excitatory vs inhibitory is a postsynaptic property [S37].
  - ~86 billion neurons [S36]. Myelinated axons conduct at up to ~120 m/s, unmyelinated at ~0.5–3 m/s [S38].
- **Circuit facts:**
  - A circuit needs a closed loop (source, load, conductors); ground is the shared reference [S39].
  - Kirchhoff: current in = current out [S40].
  - Every trace needs a continuous return path. Turn with two 45° bends [S41].
  - Gap junctions are bidirectional [S37].
- **3D theme principles:**
  1. Directed edges get one terminal.
  2. Show the integration point.
  3. Continuous sheaths read as fast.
  4. Colour excitation and inhibition at the receiving end.
  5. Two-way links only for symmetric relations.
  6. Every loop closes.
  7. Name source, load and ground.
  8. The return path runs under the signal.
  9. Turn with two 45° bends.
  10. Animate propagation as travel.
  [S37]–[S41]
- **Folder principles:**
  1. Two levels carry the load.
  2. Don't mandate a higher-order shape.
  3. Boundaries limit reach.
  4. Keep entry points shallow.
  5. Position deliberately.
  6. Separate hot code from cold.
  7. Size work to the storage unit.
  8. Mark version boundaries explicitly.
  [S6]–[S11][S28]

## Sources
Articles retrieved from PubMed; each DOI is linked.
- [S1] Kunkel 2004 JBC, PMID 14988392, https://doi.org/10.1074/jbc.R400006200
- [S2] Kunkel & Bebenek 2000, PMID 10966467, https://doi.org/10.1146/annurev.biochem.69.1.497
- [S3] St Charles et al. 2015 DNA Repair, https://www.sciencedirect.com/science/article/abs/pii/S1568786415000907
- [S4] Kong et al. 2012 Nature, PMID 22914163, https://doi.org/10.1038/nature11396
- [S5] James et al. 2017 NAR, PMID 28180286, https://doi.org/10.1093/nar/gkw969
- [S6] Luger et al. 1997 Nature, PMID 9305837, https://doi.org/10.1038/38444
- [S7] Ou et al. 2017 Science (ChromEMT), PMID 28751582, https://doi.org/10.1126/science.aag0025
- [S8] Dixon et al. 2012 Nature, PMID 22495300
- [S9] Balakrishnan & Bambara 2013, PMID 23378587; https://pmc.ncbi.nlm.nih.gov/articles/PMC3552508/
- [S10] SantaLucia & Hicks 2004, PMID 15139820
- [S11] B/A/Z-DNA parameters, https://bio.libretexts.org/
- [S12] Sullivan et al. 2017, https://pmc.ncbi.nlm.nih.gov/articles/PMC5597293/
- [S13] Alberts MBoC, https://www.ncbi.nlm.nih.gov/books/NBK26821
- [S14] Marteijn et al. 2014, PMID 24954209
- [S15] NER 24–32 nt, https://genesdev.cshlp.org/content/13/7/768
- [S16] Jiricny 2013, PMID 23545421
- [S17] https://www.nature.com/articles/s41467-022-29920-2 ; https://www.ncbi.nlm.nih.gov/pmc/articles/PMC3755907/
- [S18] Lopez-Martinez et al. 2016, PMID 27094386, https://doi.org/10.1007/s00018-016-2218-x
- [S19] Cruet-Hennequart et al. 2010, PMID 20012583
- [S20] pol η error rate, PMID 11554790
- [S21] Sparks et al. 2012, https://pmc.ncbi.nlm.nih.gov/articles/PMC3470915/ ; PMID 39159820
- [S22] Rice et al., https://www.sciencedirect.com/science/article/pii/S0002929707630481
- [S23] Musacchio 2015 Curr Biol, PMID 26485365, https://doi.org/10.1016/j.cub.2015.08.051
- [S24] Purvis et al. 2012 Science, PMID 22700930, https://doi.org/10.1126/science.1218351
- [S25] Taylor et al. 2008, PMID 18073771
- [S26] Galluzzi et al. 2018 NCCD, PMID 29362479
- [S27] Greenberg & Bourc'his 2019, PMID 31399642
- [S28] Clapier et al. 2017, PMID 28512350; positioning PMID 23463311
- [S29] Alexandrov et al. 2020 Nature, PMID 32025018, https://doi.org/10.1038/s41586-020-1943-3
- [S30] Jih et al. 2023, https://journals.lww.com/jcma/fulltext/2023/01000/reduced_penetrance_huntington_s_disease_causing.10.aspx
- [S31] Church et al. 2012 Science, PMID 22903519, https://doi.org/10.1126/science.1226355
- [S32] Erlich & Zielinski 2017 Science, PMID 28254941, https://doi.org/10.1126/science.aaj2038
- [S33] Erill et al. 2007, PMID 17883408
- [S34] Chatterjee & Matheshwaran 2026, PMID 41763507
- [S35] https://elifesciences.org/articles/42761 ; https://pmc.ncbi.nlm.nih.gov/articles/PMC9982712/
- [S36] Azevedo et al. 2009, PMID 19226510
- [S37] Purves *Neuroscience*, NBK11103, NBK11164, NBK11117
- [S38] https://www.ncbi.nlm.nih.gov/pmc/articles/PMC6978798/
- [S39] OpenStax College Physics 20.2
- [S40] Kirchhoff's rules, https://phys.libretexts.org/
- [S41] NXP AN13335; Microchip AN-56; https://jlcpcb.com/blog/pcb-routing-rules-best-practices
- [S42]–[S48] Revision 1's gene-editing and gene-therapy sources: restriction types (NEB); Anzalone 2019, PMID 31634902; Komor 2016, PMID 27096365; Gaudelli 2017, PMID 29160308; Tsai 2015, PMID 25513782; Hacein-Bey-Abina 2003, PMID 14564000; Raper 2003, PMID 14567964; FDA Casgevy approval, 8 Dec 2023
- [S49] Pan et al. 2008, PMID 18978789
- [S50] Ling et al. 2009, PMID 19239893; https://pmc.ncbi.nlm.nih.gov/articles/PMC2840151/
- [S51] Mutagenesis 2004;19:169, https://academic.oup.com/mutage/article/19/3/169/1482185
- [S54] https://doi.org/10.1021/cr500373h
- [S55] Freeland & Hurst 1998 J Mol Evol 47:238, PMID 9732450, https://doi.org/10.1007/pl00006381
- [S56] Hopfield 1974 PNAS 71:4135, PMID 4530290, https://doi.org/10.1073/pnas.71.10.4135
- [S57] Crick 1966 J Mol Biol 19:548, PMID 5969078, https://doi.org/10.1016/s0022-2836(66)80022-0
- [S58] Rodnina & Wintermeyer 2001 Annu Rev Biochem 70:415, PMID 11395413, https://doi.org/10.1146/annurev.biochem.70.1.415
- [S59] Nagy & Maquat 1998 Trends Biochem Sci 23:198, PMID 9644970, https://doi.org/10.1016/s0968-0004(98)01208-0
- [S60] Walter & Ron 2011 Science 334:1081, PMID 22116877, https://doi.org/10.1126/science.1209038
- [S61] Blow & Dutta 2005 Nat Rev Mol Cell Biol 6:476, PMID 15928711, https://doi.org/10.1038/nrm1663
- [S62] Krokan & Bjørås 2013 Cold Spring Harb Perspect Biol 5:a012583, PMID 23545420, https://doi.org/10.1101/cshperspect.a012583
- [S63] Huber et al. 2014 Nature 508:222, PMID 24695225, https://doi.org/10.1038/nature13194
- [S64] Banerjee, Kolomeisky & Igoshin 2017 PNAS 114:5183, PMID 28465435, https://doi.org/10.1073/pnas.1614838114
- [S65] Lindahl 1993 Nature 362:709, PMID 8469282, https://doi.org/10.1038/362709a0
- [S66] Bradford et al. 2011 J Med Genet 48:168, PMID 21097776, https://doi.org/10.1136/jmg.2010.083022
- [S67] Farmer et al. 2005 Nature 434:917, PMID 15829967, https://doi.org/10.1038/nature03445
- [S68] Bryant et al. 2005 Nature 434:913, PMID 15829966, https://doi.org/10.1038/nature03443
- [S69] The Joint Commission, Official "Do Not Use" List (2004; 2009 revision), copy at https://mh.alabama.gov/wp-content/uploads/2020/11/Joint-Commission-Do-Not-Use-List-2009.pdf
- [S70] ISMP List of Confused Drug Names (2023), https://www.ismp.org/system/files/resources/2023-10/ISMP_ConfusedDrugNames_2023.pdf

## MEDICAL IMPROVEMENTS THIS UNLOCKS

Each item says what the student gets, the DNA mechanism behind it, where it goes, and its status.

1. **Wrong answer keys caught by a blind re-solve.**
   - A wrong key is the costliest MCQ error, because the student learns the wrong answer as right.
   - Today voters see the keyed answer before they solve, which anchors them.
   - Fix: a first pass with key and explanation hidden, then a comparison (kinetic proofreading [S56], two-stage selection [S58]).
   - Where: server/accuracy.js votePrompt and accuracy-rules.js itemText.
   - Status: new.
2. **Dedicated sensors for the medical errors that read as plausible (the "missense" class).**
   - **Decimal and zero errors in doses.** The Joint Commission's "Do Not Use" list bans trailing zeros ("1.0 mg" read as 10 mg), missing leading zeros (".5 mg" read as 5 mg) and "U" for units [S69].
   - **mcg↔mg 1000-fold slips.**
   - **Look-alike, sound-alike drug pairs** from the ISMP list, e.g. hydralazine/hydroxyzine [S70].
   - **Laterality.**
   - **Population mismatch** (pregnancy, children, renal) and **out-of-date guidance**, both partly in the claim gate.
   - Mechanism: one glycosylase per lesion class [S62].
   - Where: accuracy-rules.js. 14 rules exist today; the decimal, look-alike and laterality sensors are missing.
   - Status: partly built.
3. **Error signatures per generator.**
   - Count typed errors by model, prompt and source to find the "mutagen", e.g. a model that slips units or a prompt that breeds "all of the above".
   - Down-weight or retire it through the existing accuracy-model weights.
   - Mechanism: mutational signatures [S29].
   - Status: new; needs voter issues to be typed first (§2, Layer 8).
4. **Lecture vs current evidence, shown to the student.**
   - When an item agrees with the lecture but evidence contradicts it, the student sees both, with the source. This teaches that guidance changes, instead of correcting it out of sight.
   - Mechanism: MMR strand discrimination [S16].
   - Status: the voter prompt already handles it; surfacing it to the student is new.
5. **Strictness that follows reach.**
   - The shared question bank and exam mode get the strictest gate: two votes, the oath check, evidence and human review for high stakes.
   - A student's own sets get the standard gate.
   - Chat answers get a fast pass, with the dose and drug-name sensors always on.
   - Mechanism: fidelity budgets by persistence [S1][S5][S50].
   - Status: partly built.
6. **Flagged items quarantined, not just labelled.**
   - Generated items that fail for good leave spaced review and mock exams automatically. They stay visible with the reason and a "rebuild from the lecture" action.
   - Mechanism: apoptosis and senescence [S24][S25].
   - Where: this is the "SRS with verification gate" feature in student features.
   - Status: new.
7. **Measured residual error.**
   - Seed known errors and measure each layer's catch rate, as fidelity is measured stage by stage. Report an estimated error rate per content tier.
   - Keep the layers different in mechanism so their catch rates multiply [S1][S3].
   - Where: checker-bench.yml and accuracy-bench.yml exist; per-layer seeding is new.
8. **Clean inputs before generating.**
   - A misheard or misread drug name or number in a transcript or OCR becomes the error in every question built from it.
   - Fix: run a drug-lexicon and number pass on transcripts and slides, and ask the student about near-miss names before generating.
   - Mechanism: nucleotide-pool sanitising [S63].
   - Where: LectureTranscriber's MedicalTerms and the OCR path.
   - Status: new.
9. **Truncated output never shown.**
   - An explanation cut off at the token limit, or an item missing a part, is rebuilt rather than shown half-finished.
   - Mechanism: nonsense-mediated decay [S59].
   - Status: transcripts already do this (CloudTranscript.salvage and cutLoops); generated items are new.
10. **Bypass never on safety-critical items.**
    - When checkers are down, ordinary items may show as Unverified with how long they have waited, and they are re-checked automatically when the checkers recover.
    - Dose, management and diagnosis items wait instead.
    - Mechanism: TLS and SOS ordering [S19][S33].
    - Status: mostly built; the "unverified since" age and the guaranteed re-check are new.
11. **Every citation the app shows is checked by its identifier.**
    - Resolve the PMID or DOI, and compare its title with what the item claims, before showing it. A mismatch reads "citation not found", a sign of fabrication.
    - Evidence: while writing this brief, 2 of 15 PubMed IDs recalled from memory pointed to unrelated papers, and resolving them caught both.
    - Where: Chat-me's citation_integrity module, carried into the Worker for model-written references.
    - Status: partly built.
12. **High-yield curriculum from this research, verified before use.**
    - DNA repair is core exam material:
      - xeroderma pigmentosum: NER; non-melanoma skin cancer risk ×10,000 and melanoma ×2,000 under age 20 [S66];
      - Lynch syndrome: MMR, microsatellite instability [S16][S17];
      - BRCA and PARP-inhibitor synthetic lethality [S67][S68];
      - ataxia telangiectasia: ATM;
      - Fanconi anaemia: interstrand crosslinks;
      - XP-variant: pol η;
      - Aicardi–Goutières: RNase H2;
      - MUTYH polyposis: BER;
      - Huntington CAG thresholds [S30];
      - nonsense-mediated decay [S59].
    - A cited seed set goes through the question-bank pipeline, which applies the licence, quality, novelty and accuracy checks, then a person's review. Nothing reaches students before that review.
    - Status: proposal.
