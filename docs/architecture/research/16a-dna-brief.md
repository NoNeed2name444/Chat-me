# DNA RESEARCH BRIEF (§16a — grounding for §16)

Evidence from PubMed (NCBI), NCBI Bookshelf (Alberts, Purves) and engineering refs. Every number carries a key `[Sn]`.

- **Structure:**
  - A–T / G–C pairing, ~10.4 bp per turn [S13]; rise 3.4 Å per bp in B-form [S11].
  - B right-handed 10 bp/turn; A right-handed 11 bp/turn; Z **left**-handed 12 bp/turn [S11]. Pair stability is contextual: nearest-neighbour stacking sets ΔG/Tm [S10].
  - Nucleosome core 146–147 bp on a histone octamer [S6], repeat ~200 bp [S54]; telomeres are TTAGGG repeats [S11]; centromeres are α-satellite arrays of a 171-bp monomer, only 50–75% identical [S12].

- **Replication:**
  - Three sequential filters: base selection ≈10⁻⁵/bp → 3'→5' exonuclease proofreading (escape ≈10⁻²) → mismatch repair (~10²–10³×) [S1][S2][S3].
  - Net ≈10⁻⁹–10⁻¹¹/bp — **contested**; near 10⁻¹⁰, varying by polymerase, error type and sequence context [S1][S3]. Germline outcome: 1.20×10⁻⁸ mutations/nt/generation, +~2 per year of paternal age [S4].
  - Okazaki fragments 1000–2000 nt (bacteria) vs 100–200 nt (eukaryotes, set by nucleosome periodicity) [S9]; ~2 ribonucleotides misinserted per kb, cleared by RNase H2-initiated RER [S21].

- **Repair:**

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

- **Checkpoints:**
  - Ordered gates: G1/S (p53→p21, Rb/CDK inhibitors) → intra-S (ATR–Chk1) → G2/M (ATM–Chk2 inhibits Cdc25, sustains Wee1) → SAC [S23].
  - SAC blocks anaphase while any single kinetochore is unattached — one defect holds the whole cell [S23].
  - p53 **dynamics** decide fate: pulsed → repair, sustained → senescence [S24]. Checkpoints arrest; they do not repair [S23].

- **Apoptosis:**
  - Intrinsic: BH3-only → BAX/BAK pore → cytochrome c → apoptosome → caspase-9 [S25].
  - Extrinsic: Fas/TNFR → DISC → caspase-8; both converge on executioner caspases 3/6/7 — one irreversible commitment step [S25].
  - Controlled demolition: contents packaged, not spilled as in necrosis; death modes are formally typed [S25][S26].

- **Epigenetics:**
  - 5-methylcytosine at CpG written by DNMT3A/3B, maintained through replication by DNMT1, erased by TET oxidation [S27].
  - Same sequence, different output: marks gate transcription without editing DNA; imprinting and X-inactivation are reversible inherited switches [S27].
  - Four remodeller families (SWI/SNF, ISWI, CHD, INO80) slide, evict or exchange nucleosomes, using ATP [S28].

- **Mutation types:**

| Type | Mechanism | Consequence |
|---|---|---|
| Silent | change at a degenerate codon position | none; degeneracy absorbs it [S49] |
| Missense | substitution changes an amino acid | compiles, behaviour altered — hardest to see [S29] |
| Nonsense | substitution creates a stop | truncation / NMD [S29] |
| Insertion / deletion (in-frame) | slippage; ID signatures | local gain/loss of residues [S29] |
| Frameshift | indel not a multiple of 3 | whole downstream frame corrupt [S29] |
| Duplication | unequal crossover, slippage | dosage change, redundancy [S29] |
| Inversion | two breaks, reversed rejoin | order and orientation reversed [S29] |
| Translocation | rejoining across non-homologues | fusion genes, misplaced regulation [S29] |
| Doublet / clustered | one event hits adjacent bases | 11 DBS + 4 clustered signatures catalogued [S29] |
| Repeat expansion | slipped-strand mispairing | Huntington: 36–39 CAG reduced penetrance, ≥40 full [S30] |
| Aneuploidy | SAC failure, missegregation | whole-chromosome dosage catastrophe [S23] |
| Somatic vs germline | post-zygotic vs gametic | clonal vs heritable, 1.2×10⁻⁸/nt/gen [S4] |

- **Fidelity:**

| Process | Error rate | Why |
|---|---|---|
| Base selection alone | ≈10⁻⁵/bp | geometric + H-bond discrimination in the active site [S1][S2] |
| + proofreading | ≈10⁻⁷/bp (escape ~10⁻²) | excises the just-inserted error before extension [S2][S3] |
| + MMR = net replication | ≈10⁻⁹–10⁻¹¹/bp (**contested**) | strand-directed review against the parent [S1][S3] |
| Transcription | 10⁻³–10⁻⁵/nt; 1–3% of elongation complexes misincorporated (**contested** vs 10⁻⁵–10⁻⁶) | RNA is disposable; Gre/TFIIS resolve backtracking [S5] |
| tRNA charging (aaRS) | ~10⁻⁴ pre-editing | activation is the weak step, proofread in cis and trans [S50] |
| Translation | 10⁻³–10⁻⁴/codon | worst of the three, but bad protein is degraded, not inherited [S50] |
| TLS | ≈10⁻²–10⁻³/base, approximate | Y-family polymerases lack proofreading [S19][S20] |

- **SOS response:**
  - Specific trigger, not general stress: RecA polymerises on ssDNA at stalled forks (RecA\*), driving LexA autocleavage [S33][S34].
  - De-repression is **ordered**: >40–50 genes, accurate repair first, error-prone Pol IV/V late; prolonged induction = hypermutator state [S33][S35].
  - Recovery is automatic: RecA\* depletes, autoregulated LexA re-accumulates [S33]. The human analogue escalates repair → arrest → senescence → apoptosis via p53 dynamics [S24].

- **Chromatin structure:**
  - ~2 m of DNA per cell in a ~10 µm nucleus; nucleosomes are the first compaction level [S54][S6].
  - The 30-nm fibre is **contested**: ChromEMT finds disordered 5–24 nm granular chains in human cells in situ, 30-nm fibres only in chicken erythrocyte preparations; compaction is by local density [S7].
  - Promoters sit in a nucleosome-depleted region with a positioned +1 nucleosome [S28]; the genome is partitioned into megabase TADs nested in A/B compartments, boundaries limiting possible contacts [S8].

- **Neuron structure:**
  - Four zones: soma, dendrites (input), axon (output), terminals; inputs are read out at one place, the axon hillock / initial segment [S37].
  - Chemical synapses are **unidirectional**; excitatory vs inhibitory is a *postsynaptic* property (reversal potential vs threshold), not of the wire [S37].
  - ~86 billion neurons, ~85 billion non-neuronal cells [S36]; myelinated conduction up to ~120 m/s vs ~0.5–3 m/s unmyelinated, via saltatory jumps between nodes of Ranvier [S38].

- **Circuit structure:**
  - Minimum circuit = source + load + conductors in a closed loop; break it and nothing flows. Ground is the shared reference; V = IR; resistors dissipate, capacitors store [S39].
  - Kirchhoff: current in = current out at every junction, every closed loop sums to zero [S40].
  - Every trace needs a continuous return path beneath it (ground plane, stitching vias at layer changes); turn with two 45° bends, never 90° [S41]. Gap junctions are the exception: **bidirectional**, near-instant, synchronising populations [S37].

- **Design principles for code:**
  1. Layer independent checks instead of strengthening one — 10⁻⁵→10⁻¹⁰ came from three different filters composed. ← Fidelity [S1][S3]
  2. Proofread inside the write: excision precedes extension, so lint per block, not per PR. ← Replication [S2]
  3. Always diff the copy against the parent — MMR is strand-directed and trusts the template. ← Repair [S16]
  4. Match patch size to lesion size: BER 1 nt, NER 24–32 nt. Never rewrite a file for a typo. ← Repair [S15]
  5. Prefer template-guided rebuilds: HR is high-fidelity, NHEJ/MMEJ always leave indels — copy a working pattern. ← Repair [S18]
  6. Hunt missense, not nonsense: nonsense and frameshift announce themselves, missense ships silently. ← Mutations [S29]
  7. Classify before repairing: error shape implies mechanism (49 SBS + 11 DBS + 17 ID signatures). ← Mutations [S29]
  8. Spend accuracy budget on sources, not outputs: transcription and translation are ~10⁵× sloppier by design. ← Fidelity [S5][S50]

- **Design principles for fail-safe:**
  1. Name a detectable trigger (RecA\* on ssDNA at a stalled fork), not "something went wrong". ← SOS [S33]
  2. Escalate in fixed order — accurate repair before error-prone bypass, never in parallel. ← SOS [S33][S35]
  3. Arrest before improvising — a gate that stops the pipeline is doing its job. ← [S23]
  4. Treat bypass as a timed, mutagenic loan (TLS ≈10⁻²–10⁻³): every TLS-BYPASS needs a scheduled repair. ← Fidelity [S19][S34]
  5. Make de-escalation automatic — LexA re-accumulates as damage clears; degraded mode must lift itself. ← SOS [S33]
  6. Let signal *shape* choose: pulsed p53 recovers, sustained commits. Repeated failures, not one, trigger apoptosis. ← [S24]
  7. One unresolved item blocks the whole commit — SAC holds anaphase for one unattached kinetochore. ← [S23]
  8. Once discard is chosen, converge irreversibly and leave no partial state. ← Apoptosis [S25]
  9. Budget a routine error load — ~10⁴ abasic sites/cell/day, ~2 rNMP/kb, no alarm. ← Repair [S51][S21]

- **Design principles for folder structure:**
  1. Two levels carry the load — unit (147-bp nucleosome) then domain; skip the mythical middle tier. ← Chromatin [S6][S7]
  2. Don't mandate a higher-order shape — the 30-nm fibre is contested; let depth follow file density. ← Chromatin [S7]
  3. Boundaries limit reach — as TADs constrain contacts, folder boundaries should constrain imports. ← Chromatin [S8]
  4. Keep entry points shallow: promoters sit in nucleosome-depleted regions. ← Chromatin [S28]
  5. Position deliberately, then allow sanctioned remodelling (+1 nucleosome plus four remodeller families). ← Epigenetics [S28]
  6. Separate hot from cold — A/B compartments keep active apart from silent code. ← Chromatin [S8]
  7. Chunk work to the storage unit — eukaryotic Okazaki fragments are nucleosome-sized; size PRs likewise. ← Replication [S9]
  8. Mark version boundaries explicitly — telomeres are dedicated structures, not ordinary sequence. ← Structure [S11]

- **Design principles for 3D themes (Neurons + Circuit):**
  1. Directed edges get exactly one terminal, at one end — chemical synapses are unidirectional. ← Neuron [S37]
  2. Show the integration point: the hillock cone is functional, because read-out happens there. ← Neuron [S37]
  3. Continuity buys speed (~120 vs ~0.5–3 m/s): unsegmented sheath reads fast, beads read as pods. ← Neuron [S38]
  4. Colour excitation/inhibition at the receiving end — it is a postsynaptic property. ← Neuron [S37]
  5. Reserve visibly two-way links for genuinely symmetric relations; only gap junctions are bidirectional. ← Circuit [S37]
  6. Every loop must close (current in = current out): return rails are required, not ornament. ← Circuit [S40]
  7. Name source, load and ground per tile: source cell, terminating leaves, one shared reference rail. ← Circuit [S39]
  8. Keep the return path under the signal and unbroken; never hide a rail behind a tile. ← Circuit [S41]
  9. Turn with two 45° bends, never 90° — the orthogonal + 45° light-guide grid is engineering-correct. ← Circuit [S41]
  10. Animate propagation as travel (moving depolarisation, periodic pulse), not a global fade. ← Neuron [S38]

- **Sources:** [S1] Kunkel 2004 JBC, PMID 14988392, https://doi.org/10.1074/jbc.R400006200 · [S2] Kunkel & Bebenek 2000, PMID 10966467, https://doi.org/10.1146/annurev.biochem.69.1.497 · [S3] St Charles et al. 2015 DNA Repair, https://www.sciencedirect.com/science/article/abs/pii/S1568786415000907 · [S4] Kong et al. 2012 Nature, PMID 22914163, https://doi.org/10.1038/nature11396 · [S5] James et al. 2017 NAR, PMID 28180286, https://doi.org/10.1093/nar/gkw969 · [S6] Luger et al. 1997 Nature, PMID 9305837, https://doi.org/10.1038/38444 · [S7] Ou et al. 2017 Science (ChromEMT), PMID 28751582, https://doi.org/10.1126/science.aag0025 · [S8] Dixon et al. 2012 Nature, PMID 22495300 · [S9] Balakrishnan & Bambara 2013, PMID 23378587; https://pmc.ncbi.nlm.nih.gov/articles/PMC3552508/ · [S10] SantaLucia & Hicks 2004, PMID 15139820 · [S11] B/A/Z-DNA parameters, https://bio.libretexts.org/ · [S12] Sullivan et al. 2017, https://pmc.ncbi.nlm.nih.gov/articles/PMC5597293/ · [S13] Alberts MBoC, https://www.ncbi.nlm.nih.gov/books/NBK26821 · [S14] Marteijn et al. 2014, PMID 24954209 · [S15] NER 24–32 nt, https://genesdev.cshlp.org/content/13/7/768 · [S16] Jiricny 2013, PMID 23545421 · [S17] https://www.nature.com/articles/s41467-022-29920-2 ; https://www.ncbi.nlm.nih.gov/pmc/articles/PMC3755907/ · [S18] Lopez-Martinez et al. 2016, PMID 27094386, https://doi.org/10.1007/s00018-016-2218-x · [S19] Cruet-Hennequart et al. 2010, PMID 20012583 · [S20] pol η error rate, PMID 11554790 · [S21] Sparks et al. 2012, https://pmc.ncbi.nlm.nih.gov/articles/PMC3470915/ ; PMID 39159820 · [S22] Rice et al., https://www.sciencedirect.com/science/article/pii/S0002929707630481 · [S23] Musacchio 2015 Curr Biol, PMID 26485365, https://doi.org/10.1016/j.cub.2015.08.051 · [S24] Purvis et al. 2012 Science, PMID 22700930, https://doi.org/10.1126/science.1218351 · [S25] Taylor et al. 2008, PMID 18073771 · [S26] Galluzzi et al. 2018 NCCD, PMID 29362479 · [S27] Greenberg & Bourc'his 2019, PMID 31399642 · [S28] Clapier et al. 2017, PMID 28512350; positioning PMID 23463311 · [S29] Alexandrov et al. 2020 Nature, PMID 32025018, https://doi.org/10.1038/s41586-020-1943-3 · [S30] Jih et al. 2023, https://journals.lww.com/jcma/fulltext/2023/01000/reduced_penetrance_huntington_s_disease_causing.10.aspx · [S31] Church et al. 2012 Science, PMID 22903519, https://doi.org/10.1126/science.1226355 — 2 bits/nt ⇒ 455 EB/g theoretical (https://pmc.ncbi.nlm.nih.gov/articles/PMC5935598/); **§16a's "10¹⁹ bits/gram" is unit-dependent and below this ceiling — contested** · [S32] Erlich & Zielinski 2017 Science, PMID 28254941, https://doi.org/10.1126/science.aaj2038 — 215 PB/g demonstrated with fountain-code redundancy · [S33] Erill et al. 2007, PMID 17883408 · [S34] Chatterjee & Matheshwaran 2026, PMID 41763507 · [S35] https://elifesciences.org/articles/42761 ; https://pmc.ncbi.nlm.nih.gov/articles/PMC9982712/ · [S36] Azevedo et al. 2009, PMID 19226510 · [S37] Purves *Neuroscience*, NBK11103, NBK11164, NBK11117 · [S38] https://www.ncbi.nlm.nih.gov/pmc/articles/PMC6978798/ · [S39] OpenStax College Physics 20.2 · [S40] Kirchhoff's rules, https://phys.libretexts.org/ · [S41] NXP AN13335; Microchip AN-56; https://jlcpcb.com/blog/pcb-routing-rules-best-practices · [S42] Restriction types I/II/III (qualitative), https://www.neb.com/en-us/products/restriction-endonucleases/types-of-restriction-endonucleases · [S43] Anzalone et al. 2019 prime editing, PMID 31634902, https://doi.org/10.1038/s41586-019-1711-4 · [S44] Komor et al. 2016, PMID 27096365; Gaudelli et al. 2017, PMID 29160308 (~50% A•T→G•C, ≥99.9% purity, ≤0.1% indels) · [S45] Tsai et al. 2015 GUIDE-seq, PMID 25513782 · [S46] Hacein-Bey-Abina et al. 2003 LMO2 leukaemia, PMID 14564000 · [S47] Raper et al. 2003 OTC death, PMID 14567964 · [S48] FDA Casgevy approval, 8 Dec 2023 · [S49] Pan et al. 2008, PMID 18978789 · [S50] Ling et al. 2009, PMID 19239893; https://pmc.ncbi.nlm.nih.gov/articles/PMC2840151/ · [S51] Mutagenesis 2004;19:169, https://academic.oup.com/mutage/article/19/3/169/1482185 · [S54] https://doi.org/10.1021/cr500373h

- **UNKNOWN:** error rate for direct reversal; per-event indel rate for NHEJ vs MMEJ in human cells; synaptic delay in ms (chemical vs electrical); synapses per human cortical neuron.
