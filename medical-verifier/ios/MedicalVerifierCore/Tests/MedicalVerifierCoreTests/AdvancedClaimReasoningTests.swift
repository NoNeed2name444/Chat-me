import XCTest
@testable import MedicalVerifierCore

final class AdvancedClaimReasoningTests: XCTestCase {
    private func source(_ passage: String) -> SourceSnapshot {
        SourceSnapshot(
            snapshotID: "reasoning",
            title: "Course",
            fileSHA256: SourceHasher.sha256Hex(
                Data("pdf-bytes".utf8)
            ),
            passageSHA256: SourceHasher.normalizedTextSHA256(
                passage
            ),
            passage: passage,
            locator: "page:1",
            version: "2025"
        )
    }

    func testFrankenClaimCannotMixSubjects() {
        let result = CurriculumVerifier().verify(
            prompt: "What do the medicines do?",
            answer: "Drug A increases bleeding. Drug A reduces blood pressure.",
            sources: [
                source(
                    "Drug A increases bleeding. Drug B reduces blood pressure."
                )
            ]
        )

        XCTAssertEqual(result.status, .sourceUnsupported)
        XCTAssertTrue(
            result.warnings.contains {
                $0.contains("atomic_subject_mismatch")
            }
        )
    }

    func testCausalClaimCannotUseAssociationalEvidence() {
        let result = CurriculumVerifier().verify(
            prompt: "What causes bleeding?",
            answer: "Drug A causes bleeding.",
            sources: [
                source(
                    "Drug A is associated with bleeding."
                )
            ]
        )

        XCTAssertEqual(result.status, .sourceUnsupported)
        XCTAssertTrue(
            result.warnings.contains {
                $0.contains("causal_claim_requires_causal_evidence")
            }
        )
    }

    func testTemporalScopeMustBePreserved() {
        let result = CurriculumVerifier().verify(
            prompt: "What happened previously?",
            answer: "Drug A previously increased bleeding.",
            sources: [
                source(
                    "Drug A increases bleeding."
                )
            ]
        )

        XCTAssertEqual(result.status, .sourceUnsupported)
        XCTAssertTrue(
            result.warnings.contains {
                $0.contains("temporal_scope_missing")
            }
        )
    }

    func testInteractionClaimRequiresInteractionEvidence() {
        let result = CurriculumVerifier().verify(
            prompt: "Are these medicines compatible?",
            answer: "Drug A interacts with Drug B.",
            sources: [
                source(
                    "Drug A is associated with Drug B."
                )
            ]
        )

        XCTAssertEqual(result.status, .sourceUnsupported)
        XCTAssertTrue(
            result.warnings.contains {
                $0.contains(
                    "interaction_claim_requires_interaction_evidence"
                )
            }
        )
    }

    func testContraindicationClaimRequiresContraindicationEvidence() {
        let result = CurriculumVerifier().verify(
            prompt: "Can this drug be used in pregnancy?",
            answer: "Drug A is contraindicated in pregnancy.",
            sources: [
                source(
                    "Drug A is associated with pregnancy."
                )
            ]
        )

        XCTAssertEqual(result.status, .sourceUnsupported)
        XCTAssertTrue(
            result.warnings.contains {
                $0.contains(
                    "contraindication_claim_requires_contraindication_evidence"
                )
            }
        )
    }

    func testMatchingInteractionIsValidated() {
        let result = CurriculumVerifier().verify(
            prompt: "Are these medicines compatible?",
            answer: "Drug A interacts with Drug B.",
            sources: [
                source(
                    "Drug A has an interaction with Drug B."
                )
            ]
        )

        XCTAssertEqual(result.status, .validated)
    }
}
