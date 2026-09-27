from app.verification.evidence_model import check_entailment


def test_direction_mismatch_blocks_support():
    result = check_entailment(
        "Statins increase new-onset diabetes risk.",
        "Statins decrease new-onset diabetes risk.",
    )
    assert result["status"] == "mismatch"
    assert "direction" in result["mismatches"]


def test_modality_mismatch_blocks_certain_claim():
    result = check_entailment(
        "Metformin definitely prevents hypoglycemia.",
        "Metformin may reduce the risk of hypoglycemia.",
    )
    assert result["status"] == "mismatch"
    assert "modality" in result["mismatches"]


def test_population_mismatch_blocks_transfer():
    result = check_entailment(
        "The treatment reduces mortality in children.",
        "The treatment reduces mortality in adults.",
    )
    assert result["status"] == "mismatch"
    assert "population" in result["mismatches"]


def test_dose_unit_normalization_accepts_equivalent_units():
    result = check_entailment(
        "The dose is 1 g daily.",
        "Participants received 1000 mg daily.",
    )
    assert result["status"] == "compatible"
    assert result["axes"]["dose_unit"]["matched"] is True


def test_dose_mismatch_blocks_support():
    result = check_entailment(
        "The dose is 1 g daily.",
        "Participants received 500 mg daily.",
    )
    assert result["status"] == "mismatch"
    assert "dose_unit" in result["mismatches"]


def test_causal_claim_does_not_entail_association():
    result = check_entailment(
        "Statins cause diabetes.",
        "Statin use is associated with a modest increase in diabetes risk.",
    )
    assert result["status"] == "mismatch"
    assert "causal_meaning" in result["mismatches"]


def test_association_can_remain_compatible_with_association():
    result = check_entailment(
        "Statins are associated with increased diabetes risk.",
        "Statin use is associated with a modest increase in diabetes risk.",
    )
    assert result["status"] == "compatible"


def test_time_window_mismatch_blocks_support():
    result = check_entailment(
        "The effect occurs within 24 hours.",
        "The effect occurs after 6 months.",
    )
    assert result["status"] == "mismatch"
    assert "time_window" in result["mismatches"]
