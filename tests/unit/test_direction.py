from agents.specialists.verification_agent.direction import (
    direction_entailed,
    directions,
)
from agents.specialists.verification_agent.independent_entailment import verify


def test_turned_around_change_is_not_entailed():
    assert direction_entailed(
        "Statin therapy is associated with a reduced risk of new-onset diabetes.",
        "Statin therapy is associated with a modestly increased risk of new-onset diabetes.",
    ) == (False, "atomic_direction_mismatch")


def test_turned_around_amount_is_not_entailed():
    assert direction_entailed(
        "Statins have a high incidence of clinically apparent liver injury.",
        "Clinically apparent liver injury attributed to statins is rare.",
    ) == (False, "atomic_direction_mismatch")


def test_way_the_evidence_never_states_is_not_entailed():
    assert direction_entailed(
        "Vitamin K has a stronger anticoagulant effect on warfarin.",
        "Vitamin K interacts with warfarin, whose anticoagulant activity depends on vitamin K.",
    ) == (False, "atomic_direction_not_entailed")


def test_same_way_in_other_words_is_entailed():
    assert direction_entailed(
        "Statins lower LDL cholesterol.",
        "Statins are effective in lowering LDL cholesterol.",
    ) == (True, None)
    assert direction_entailed(
        "Statin-associated myopathy is uncommon.",
        "Myopathy is listed as a rare adverse effect of statins.",
    ) == (True, None)
    assert direction_entailed(
        "Metformin lowers blood glucose.",
        "Metformin is a glucose-lowering drug.",
    ) == (True, None)


def test_negated_statement_is_left_to_the_negation_checks():
    assert directions("Metformin does not increase lactate.") is None
    assert direction_entailed(
        "Metformin does not increase lactate.",
        "Metformin increases lactate.",
    ) == (True, None)


def test_parts_and_names_say_no_way():
    for text in (
        "Lower limb ischaemia needs urgent review.",
        "Greater trochanter pain syndrome.",
        "Minimal change disease in children.",
        "Stones in the common bile duct.",
        "As described above, the drug is given daily.",
        "Low-dose aspirin after the stent.",
        "High-density lipoprotein carries cholesterol.",
    ):
        assert directions(text) == (None, None), text


def test_cut_off_asks_nothing_of_the_evidence_words():
    assert direction_entailed(
        "Metformin is contraindicated when eGFR is below 30 mL/min.",
        "Metformin is contraindicated when eGFR is < 30 mL/min.",
    ) == (True, None)
    assert direction_entailed(
        "The dose is halved when eGFR is below 45 mL/min.",
        "The dose is halved when eGFR is above 45 mL/min.",
    ) == (False, "atomic_direction_mismatch")


def test_swapped_comparison_reads_the_same_way():
    assert direction_entailed(
        "Warfarin has a higher risk of intracranial hemorrhage than DOACs.",
        "DOACs have a lower risk of intracranial hemorrhage than warfarin.",
    ) == (True, None)
    assert direction_entailed(
        "DOACs have a higher rate of recurrent intracranial hemorrhage than warfarin.",
        "DOACs had a lower risk of recurrent intracranial hemorrhage than warfarin.",
    ) == (False, "atomic_direction_mismatch")


def test_three_letter_side_is_read():
    assert direction_entailed(
        "Gout is less common in women than in men.",
        "Gout is more common in men than in women.",
    ) == (True, None)
    assert direction_entailed(
        "Gout is more common in women than in men.",
        "Gout is more common in men than in women.",
    ) == (False, "atomic_direction_mismatch")
    # "the" names no side: the elderly are not the young, so this is no swap
    assert direction_entailed(
        "Risk is higher in women than in the elderly.",
        "Risk is lower in the young than in women.",
    ) == (False, "atomic_direction_mismatch")


def test_turned_around_claim_abstains_rather_than_contradicts():
    # a wrong-way reading is an abstention, not a contradiction: one
    # structured provider's read never decides the verdict alone
    result = verify(
        "SGLT2 inhibitors have a lower risk of genital infection.",
        "SGLT2 inhibitors have a higher risk of genital infection.",
    )
    assert result.label == "UNKNOWN"
    assert result.reasons == ("atomic_direction_mismatch",)


def test_english_past_tense_survives_normalising():
    result = verify(
        "Statin therapy is associated with a reduced risk of new-onset diabetes.",
        "Statin therapy is associated with a modestly increased risk of new-onset diabetes.",
    )
    assert result.label == "UNKNOWN"
    assert result.reasons == ("atomic_direction_mismatch",)
