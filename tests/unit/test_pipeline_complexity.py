from api.schemas.claim import ClaimRequest
from orchestration.graph import verify

def test_compound_claim_does_not_inherit_support(monkeypatch):
    class FakePubMed:
        def search(self, claim, limit=6):
            from api.schemas.evidence import EvidenceItem
            return [EvidenceItem(
                id="paper:" + str(abs(hash(claim))),
                title="Evidence",
                source_type="literature",
                publisher="Independent",
                url="https://example.test",
                passage=claim,
                source_family="primary_literature",
                independence_group="paper:" + str(abs(hash(claim))),
                source_authority=0.80,
                supports=True,
            )]

    import orchestration.graph as pipeline
    monkeypatch.setattr(pipeline, "PubMedProvider", FakePubMed)

    result = verify(ClaimRequest(
        claim="Drug X increases risk and Drug X causes no harm.",
        sources=["pubmed"],
        requested_evidence_level="any",
    ))

    assert result.verdict in {
        "MIXED_EVIDENCE",
        "INSUFFICIENT_EVIDENCE",
        "SUPPORTED",
    }

def test_direct_treatment_request_is_reviewed():
    result = verify(ClaimRequest(
        claim="Should I change my dose?",
        context={
            "age": 40,
            "current_medications": ["example"],
        },
        sources=["local"],
        requested_evidence_level="any",
    ))
    assert result.requires_human_review is True
