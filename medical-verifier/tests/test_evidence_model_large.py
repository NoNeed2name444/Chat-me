from app.verification.evidence_model import check_entailment

def test_large_adversarial_matrix():
    cases = [
        ("alpha increases beta","alpha decreases beta","contradiction","direction"),
        ("alpha reduces beta","alpha increases beta","contradiction","direction"),
        ("alpha must prevent beta","alpha may prevent beta","neutral","modality"),
        ("alpha definitely reduces beta","alpha probably reduces beta","neutral","modality"),
        ("alpha reduces beta in children","alpha reduces beta in adults","neutral","population"),
        ("alpha reduces beta in pregnant patients","alpha reduces beta in adults","neutral","population"),
        ("dose is 1000 mg daily","dose is 1 g daily","entailment",None),
        ("dose is 1000 mcg daily","dose is 1 mg daily","entailment",None),
        ("dose is 100 mg daily","dose is 200 mg daily","neutral","dose_unit"),
        ("effect occurs within 24 hours","effect occurs within 48 hours","neutral","time_window"),
        ("effect occurs after 1 week","effect occurs after 6 months","neutral","time_window"),
        ("alpha reduces beta","alpha reduces gamma","neutral","outcome"),
        ("alpha causes beta","alpha is associated with beta","neutral","causal_meaning"),
        ("alpha is associated with beta","alpha causes beta","entailment",None),
        ("alpha reduces beta","delta reduces gamma","insufficient",None),
        ("alpha does not increase beta","alpha increases beta","contradiction","polarity"),
    ]
    for claim,evidence,expected,axis in cases:
        result=check_entailment(claim,evidence)
        assert result["relation"]==expected,result
        if axis:
            assert axis in result["mismatches"],result
