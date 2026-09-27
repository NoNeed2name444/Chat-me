"""
Adversarial 50-claim benchmark for the medical verifier.

40 reference-positive claims are paired with source-backed evidence snippets.
10 reference-negative claims deliberately invert or distort a nearby fact without
using the verifier's obvious negation/numeric/causal trigger words.

This is a software robustness benchmark, not clinical validation.
"""

from app.models.claim import ClaimRequest
from app.models.evidence import EvidenceItem
import app.verification.pipeline as pipeline


BENCHMARK = [
    # 40 source-backed positives
    ("T01", True, "Metformin monotherapy has minimal hypoglycemia risk.",
     "Because of its high efficacy in lowering HbA1c, metformin has minimal hypoglycemia risk when used as monotherapy.",
     "https://pmc.ncbi.nlm.nih.gov/articles/PMC10008140/"),
    ("T02", True, "Metformin monotherapy is generally well tolerated, with gastrointestinal adverse effects among the common adverse effects.",
     "Metformin monotherapy is generally well tolerated, with gastrointestinal adverse effects among the most common adverse effects.",
     "https://pmc.ncbi.nlm.nih.gov/articles/PMC2606813/"),
    ("T03", True, "Metformin primarily decreases hepatic glucose output.",
     "The major effect of metformin is to decrease hepatic glucose output and lower fasting glycemia.",
     "https://pmc.ncbi.nlm.nih.gov/articles/PMC2606813/"),
    ("T04", True, "Metformin should not be used when eGFR is below 30 mL/min/1.73 m2.",
     "Metformin should not be used in people with an eGFR below 30 mL/min/1.73 m2.",
     "https://pmc.ncbi.nlm.nih.gov/articles/PMC10008140/"),
    ("T05", True, "GLP-1 receptor agonist and SGLT2 inhibitor cardiorenal benefits can be considered independently of metformin use.",
     "The benefits of GLP-1 receptor agonists and SGLT2 inhibitors for cardiovascular and renal outcomes have been found to be independent of metformin use.",
     "https://pmc.ncbi.nlm.nih.gov/articles/PMC10008140/"),
    ("T06", True, "Statins inhibit HMG-CoA reductase in the cholesterol biosynthetic pathway.",
     "Statins inhibit 3-hydroxymethyl glutaryl coenzyme A reductase, the rate-determining enzyme in the cholesterol biosynthetic pathway.",
     "https://pmc.ncbi.nlm.nih.gov/articles/PMC10546337/"),
    ("T07", True, "Statins lower LDL cholesterol by inhibiting cholesterol biosynthesis.",
     "Statins are effective in lowering LDL-C and inhibit HMG-CoA reductase in cholesterol biosynthesis.",
     "https://pmc.ncbi.nlm.nih.gov/articles/PMC10546337/"),
    ("T08", True, "Statin therapy is associated with a modest increase in new-onset diabetes risk.",
     "Meta-analyses of clinical statin trials indicate a modest increase in the risk of developing new-onset diabetes.",
     "https://pmc.ncbi.nlm.nih.gov/articles/PMC4926726/"),
    ("T09", True, "Baseline metabolic syndrome features are associated with higher diabetes risk during statin therapy.",
     "Risk factors for diabetes in statin-treated persons include underlying diabetes risk at baseline, specifically features of metabolic syndrome.",
     "https://pmc.ncbi.nlm.nih.gov/articles/PMC4926726/"),
    ("T10", True, "Statin-associated myopathy is an uncommon adverse effect.",
     "Myositis and myopathy are listed as rare adverse effects of high-intensity statin therapy.",
     "https://pmc.ncbi.nlm.nih.gov/articles/PMC7403606/"),
    ("T11", True, "Statin-associated rhabdomyolysis is rare.",
     "Rhabdomyolysis is categorized as a rare adverse effect of statin therapy.",
     "https://pmc.ncbi.nlm.nih.gov/articles/PMC7403606/"),
    ("T12", True, "Clinically important drug-induced liver injury from statins is rare.",
     "Clinically important drug-induced liver injury is very rare with statin use.",
     "https://pmc.ncbi.nlm.nih.gov/articles/PMC3983981/"),
    ("T13", True, "Statin therapy can produce mild serum aminotransferase elevations.",
     "Mild elevations in serum aminotransferases occur in a minority of patients treated with statins.",
     "https://pmc.ncbi.nlm.nih.gov/articles/PMC4110177/"),
    ("T14", True, "Statin-associated autoimmune myopathy is a rare adverse effect.",
     "Statin-associated autoimmune myopathy with HMGCR antibodies is categorized as rare.",
     "https://pmc.ncbi.nlm.nih.gov/articles/PMC7403606/"),
    ("T15", True, "Statins are associated with a small upward shift in glycemia.",
     "Large-scale randomized statin trials found a small upward shift in glycemia with statin therapy.",
     "https://pmc.ncbi.nlm.nih.gov/articles/PMC7615958/"),
    ("T16", True, "The diabetes risk associated with statins is related in part to treatment intensity.",
     "Meta-analyses found greater diabetes incidence with more intensive-dose statin therapy than with moderate-dose therapy.",
     "https://pmc.ncbi.nlm.nih.gov/articles/PMC4926726/"),
    ("T17", True, "SGLT2 inhibitors are associated with an increased risk of genital infection.",
     "The risk of genital infection was significantly higher in the SGLT2 inhibitor group.",
     "https://pmc.ncbi.nlm.nih.gov/articles/PMC12112461/"),
    ("T18", True, "SGLT2 inhibitor therapy was associated with lower hyperkalemia risk in a large observational cohort.",
     "The risk of hyperkalemia was lower in the SGLT2 inhibitor group.",
     "https://pmc.ncbi.nlm.nih.gov/articles/PMC12112461/"),
    ("T19", True, "The absolute risk of diabetic ketoacidosis with SGLT2 inhibitors is low.",
     "In patients with diabetes, the absolute risk of ketoacidosis was low.",
     "https://pmc.ncbi.nlm.nih.gov/articles/mid/EMS155754/"),
    ("T20", True, "SGLT2 inhibitors are associated with lower heart-failure hospitalization in large placebo-controlled trials.",
     "SGLT2 inhibition improves heart failure outcomes, and the trials show reduced heart-failure hospitalization.",
     "https://pmc.ncbi.nlm.nih.gov/articles/mid/EMS155754/"),
    ("T21", True, "SGLT2 inhibitors have demonstrated kidney-outcome benefits across large placebo-controlled trials.",
     "Large placebo-controlled trials demonstrate kidney outcome benefits from SGLT2 inhibition.",
     "https://pmc.ncbi.nlm.nih.gov/articles/mid/EMS155754/"),
    ("T22", True, "Vitamin K antagonizes the anticoagulant action of warfarin.",
     "Warfarin exerts anticoagulant activity by inhibiting vitamin K epoxide reductase and reducing functional vitamin K-dependent clotting factors.",
     "https://pmc.ncbi.nlm.nih.gov/articles/PMC2756787/"),
    ("T23", True, "Warfarin interferes with regeneration of reduced vitamin K.",
     "Warfarin interrupts regeneration of the reduced active form of vitamin K by inhibiting vitamin K epoxide reductase.",
     "https://pmc.ncbi.nlm.nih.gov/articles/PMC2911546/"),
    ("T24", True, "Vitamin K is a cofactor for activation of vitamin K-dependent clotting factors.",
     "Reduced vitamin K is an essential cofactor for activation of vitamin K-dependent clotting factors.",
     "https://pmc.ncbi.nlm.nih.gov/articles/PMC2911546/"),
    ("T25", True, "Clopidogrel is a prodrug requiring metabolic activation.",
     "Clopidogrel is a prodrug and requires metabolic activation in vivo to exert its antiplatelet effect.",
     "https://pmc.ncbi.nlm.nih.gov/articles/PMC11300169/"),
    ("T26", True, "Clopidogrel is activated primarily by CYP2C19.",
     "Clopidogrel is activated primarily by the metabolic enzyme cytochrome P450 2C19.",
     "https://pmc.ncbi.nlm.nih.gov/articles/PMC11300169/"),
    ("T27", True, "Aspirin irreversibly inhibits platelet COX-1.",
     "Aspirin irreversibly acetylates and inhibits platelet cyclooxygenase-1.",
     "https://pmc.ncbi.nlm.nih.gov/articles/PMC2872576/"),
    ("T28", True, "Aspirin inhibits thromboxane A2 production in platelets.",
     "Aspirin blocks production of thromboxane A2 by acetylating COX-1.",
     "https://pmc.ncbi.nlm.nih.gov/articles/PMC2872576/"),
    ("T29", True, "Heparin requires antithrombin for its therapeutic anticoagulant effect.",
     "Antithrombin is required for therapeutic anticoagulation by heparin or low-molecular-weight heparin; heparin alone has no direct anticoagulant effect.",
     "https://pmc.ncbi.nlm.nih.gov/articles/PMC10571690/"),
    ("T30", True, "Heparin enhances antithrombin inhibition of thrombin and factor Xa.",
     "The anticoagulant effect of heparin occurs through antithrombin inhibition of thrombin and activated factor X.",
     "https://pmc.ncbi.nlm.nih.gov/articles/PMC10571690/"),
    ("T31", True, "Long-term proton-pump inhibitor use is associated with hypomagnesemia.",
     "Long-term proton-pump inhibitor use has been associated with hypomagnesemia.",
     "https://pmc.ncbi.nlm.nih.gov/articles/PMC4434191/"),
    ("T32", True, "Proton-pump inhibitors can reduce intestinal magnesium absorption.",
     "Proton-pump inhibitors may decrease intestinal magnesium absorption through effects on active and passive absorption pathways.",
     "https://pmc.ncbi.nlm.nih.gov/articles/PMC4527261/"),
    ("T33", True, "Levothyroxine absorption can be reduced by calcium supplements.",
     "Calcium and iron supplements can decrease levothyroxine bioavailability and reduce absorption.",
     "https://pmc.ncbi.nlm.nih.gov/articles/PMC8002057/"),
    ("T34", True, "Coadministration of calcium preparations can reduce levothyroxine absorption.",
     "Coadministration of calcium preparations significantly reduced levothyroxine absorption compared with levothyroxine alone.",
     "https://pmc.ncbi.nlm.nih.gov/articles/PMC3092723/"),
    ("T35", True, "ACE inhibitors are associated with dry cough.",
     "Dry cough is a common adverse effect associated with angiotensin-converting enzyme inhibitors.",
     "https://pmc.ncbi.nlm.nih.gov/articles/PMC10423763/"),
    ("T36", True, "ACE inhibitor cough has been linked to increased bradykinin.",
     "ACE inhibitors decrease bradykinin degradation, increasing bradykinin concentration and contributing to dry cough.",
     "https://pmc.ncbi.nlm.nih.gov/articles/PMC7808101/"),
    ("T37", True, "Warfarin inhibits vitamin K epoxide reductase.",
     "Warfarin is a potent inhibitor of vitamin K epoxide reductase.",
     "https://pmc.ncbi.nlm.nih.gov/articles/PMC2787394/"),
    ("T38", True, "Acetaminophen overdose can deplete hepatic glutathione.",
     "In acetaminophen overdose, excessive NAPQI formation can deplete intrahepatic glutathione stores.",
     "https://pmc.ncbi.nlm.nih.gov/articles/PMC2504411/"),
    ("T39", True, "Acetaminophen hepatotoxicity involves the reactive metabolite NAPQI.",
     "Acetaminophen is oxidatively metabolized to the reactive intermediate NAPQI, which is central to hepatocellular injury.",
     "https://pmc.ncbi.nlm.nih.gov/articles/PMC2504411/"),
    ("T40", True, "DOACs have shown lower recurrent intracranial hemorrhage risk than warfarin in patients with atrial fibrillation and prior intracranial hemorrhage.",
     "In a systematic review and meta-analysis of atrial fibrillation patients with prior intracranial hemorrhage, DOACs had a lower risk of recurrent intracranial hemorrhage than warfarin.",
     "https://pmc.ncbi.nlm.nih.gov/articles/PMC10033967/"),

    # 10 deliberately false, designed to avoid the verifier's obvious adversarial triggers
    ("F01", False, "Metformin monotherapy has a high hypoglycemia risk.",
     "Metformin monotherapy has minimal hypoglycemia risk when used as monotherapy.",
     "https://pmc.ncbi.nlm.nih.gov/articles/PMC10008140/"),
    ("F02", False, "Statin therapy is associated with a reduced risk of new-onset diabetes.",
     "Statin therapy is associated with a modestly increased risk of new-onset diabetes.",
     "https://pmc.ncbi.nlm.nih.gov/articles/PMC4926726/"),
    ("F03", False, "Vitamin K has a stronger anticoagulant effect on warfarin.",
     "Vitamin K interacts with warfarin, whose anticoagulant activity depends on antagonism of vitamin K-dependent clotting factor synthesis.",
     "https://ods.od.nih.gov/factsheets/vitaminK-HealthProfessional/"),
    ("F04", False, "SGLT2 inhibitors have a lower risk of genital infection.",
     "The risk of genital infection was higher in the SGLT2 inhibitor group.",
     "https://pmc.ncbi.nlm.nih.gov/articles/PMC12112461/"),
    ("F05", False, "SGLT2 inhibitors have a higher risk of hyperkalemia.",
     "The risk of hyperkalemia was lower in the SGLT2 inhibitor group.",
     "https://pmc.ncbi.nlm.nih.gov/articles/PMC12112461/"),
    ("F06", False, "Statins have a high incidence of clinically apparent liver injury.",
     "Clinically apparent drug-induced liver injury attributed to statins is rare.",
     "https://pmc.ncbi.nlm.nih.gov/articles/PMC4110177/"),
    ("F07", False, "Statins have a higher association with cataracts.",
     "In a major cholesterol guideline evidence table, cataracts were classified as an unfounded statin adverse effect.",
     "https://pmc.ncbi.nlm.nih.gov/articles/PMC7403606/"),
    ("F08", False, "Statins have a higher association with tendon rupture.",
     "In a major cholesterol guideline evidence table, tendon rupture was classified as an unfounded statin adverse effect.",
     "https://pmc.ncbi.nlm.nih.gov/articles/PMC7403606/"),
    ("F09", False, "DOACs have a higher rate of recurrent intracranial hemorrhage than warfarin.",
     "In atrial fibrillation patients with prior intracranial hemorrhage, DOACs had a lower risk of recurrent intracranial hemorrhage than warfarin.",
     "https://pmc.ncbi.nlm.nih.gov/articles/PMC10033967/"),
    ("F10", False, "Levothyroxine has increased absorption when coadministered with calcium.",
     "Calcium coadministration reduced levothyroxine absorption compared with levothyroxine given alone.",
     "https://pmc.ncbi.nlm.nih.gov/articles/PMC30927203/"),
]


def _fake_retrieval(assertion_text, request):
    for item_id, truth, claim, passage, source in BENCHMARK:
        if assertion_text == claim:
            return [EvidenceItem(
                id=item_id,
                title=f"Benchmark evidence {item_id}",
                source_type="systematic_review",
                publisher="Benchmark reference",
                passage=passage,
                url=source,
            )], []
    raise AssertionError(f"Missing benchmark claim: {assertion_text}")


def test_benchmark_shape():
    assert len(BENCHMARK) == 50
    assert sum(x[1] for x in BENCHMARK) == 40
    assert sum(not x[1] for x in BENCHMARK) == 10
    assert len({x[0] for x in BENCHMARK}) == 50


def test_50_claim_adversarial_benchmark(monkeypatch):
    monkeypatch.setattr(pipeline, "_retrieve_for_assertion", _fake_retrieval)

    results = []
    for item_id, truth, claim, passage, source in BENCHMARK:
        result = pipeline.verify(ClaimRequest(
            claim=claim,
            sources=["local"],
            requested_evidence_level="any",
        ))
        results.append((item_id, truth, result.verdict, result.confidence, result.decision_reasons))

    false_items = [x for x in results if not x[1]]
    false_caught = [x for x in false_items if x[2] != "SUPPORTED"]
    false_missed = [x for x in false_items if x[2] == "SUPPORTED"]
    true_rejected = [x for x in results if x[1] and x[2] != "SUPPORTED"]

    print("\n=== MEDICAL VERIFIER 50-CLAIM ADVERSARIAL BENCHMARK ===")
    print(f"False claims caught: {len(false_caught)}/10")
    print(f"False claims missed: {len(false_missed)}/10")
    print(f"True claims supported: {40-len(true_rejected)}/40")
    print(f"True claims rejected/withheld: {len(true_rejected)}/40")
    for row in false_items:
        print(f"{row[0]} verdict={row[2]} confidence={row[3]} reasons={row[4]}")

    # Deliberately do not assert that the verifier passes the benchmark.
    # A false-positive is the finding this test is designed to expose.
    assert len(false_missed) >= 1, (
        "The current benchmark did not expose a false-positive path; "
        "inspect the verifier before treating this as evidence of robustness."
    )
