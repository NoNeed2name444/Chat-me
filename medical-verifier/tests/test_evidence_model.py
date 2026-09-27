from app.verification.evidence_model import check_entailment

def test_direction_mismatch_blocks_support():
    result = check_entailment("Statins increase new-onset diabetes risk.","Statins decrease new-onset diabetes risk.")
    assert result["status"] == "contradiction" and "direction" in result["mismatches"]

def test_modality_mismatch_blocks_certain_claim():
    result = check_entailment("Metformin definitely prevents hypoglycemia.","Metformin may reduce the risk of hypoglycemia.")
    assert result["status"] == "neutral" and "modality" in result["mismatches"]

def test_population_mismatch_blocks_transfer():
    result = check_entailment("The treatment reduces mortality in children.","The treatment reduces mortality in adults.")
    assert result["status"] == "neutral" and "population" in result["mismatches"]

def test_dose_unit_normalization_accepts_equivalent_units():
    result = check_entailment("The dose is 1 g daily.","Participants received 1000 mg daily.")
    assert result["status"] == "entailment" and result["axes"]["dose_unit"]["matched"] is True

def test_dose_mismatch_blocks_support():
    result = check_entailment("The dose is 1 g daily.","Participants received 500 mg daily.")
    assert result["status"] == "neutral" and "dose_unit" in result["mismatches"]

def test_causal_claim_does_not_entail_association():
    result = check_entailment("Statins cause diabetes.","Statin use is associated with a modest increase in diabetes risk.")
    assert result["status"] == "neutral" and "causal_meaning" in result["mismatches"]

def test_association_can_remain_compatible_with_association():
    result = check_entailment("Statins are associated with increased diabetes risk.","Statin use is associated with a modest increase in diabetes risk.")
    assert result["status"] == "entailment"

def test_causal_evidence_can_entail_association():
    result = check_entailment("Statins are associated with increased diabetes risk.","Statins increase diabetes risk.")
    assert result["status"] == "entailment"

def test_time_window_mismatch_blocks_support():
    result = check_entailment("The effect occurs within 24 hours.","The effect occurs after 6 months.")
    assert result["status"] == "neutral" and "time_window" in result["mismatches"]

def test_outcome_mismatch_blocks_support():
    result = check_entailment("The treatment reduces stroke risk.","The treatment reduces bleeding risk.")
    assert result["status"] == "neutral" and "outcome" in result["mismatches"]

def test_paraphrase_invariance():
    left = check_entailment("Statin therapy is associated with increased diabetes risk.","Statin treatment is associated with a higher risk of diabetes.")
    right = check_entailment("Statin therapy is associated with increased diabetes risk.","Statin treatment is linked to more diabetes risk.")
    assert left["status"] == right["status"] == "entailment"

def test_critical_dimension_change_changes_relation():
    base = check_entailment("Statins increase diabetes risk.","Statins increase diabetes risk.")
    changed = check_entailment("Statins increase diabetes risk.","Statins decrease diabetes risk.")
    assert base["relation"] == "entailment" and changed["relation"] == "contradiction"

def test_insufficient_subject_overlap():
    result = check_entailment("Statins increase diabetes risk.","Aspirin reduces bleeding risk.")
    assert result["relation"] == "insufficient" and result["status"] == "insufficient"


def test_negated_claim_does_not_match_positive_evidence():
    result = check_entailment("Statins do not increase diabetes risk.","Statins increase diabetes risk.")
    assert result["relation"] == "contradiction" and "polarity" in result["mismatches"]

def test_dose_mismatch_remains_relevant_for_quantity_axis():
    result = check_entailment("The dose is 1 g daily.","Participants received 500 mg daily.")
    assert result["relevance"] == "relevant"
