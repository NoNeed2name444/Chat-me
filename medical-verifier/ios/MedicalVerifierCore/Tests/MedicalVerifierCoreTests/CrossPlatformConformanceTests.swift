import XCTest
@testable import MedicalVerifierCore

private struct ConformanceCorpus: Codable {
    let version: String
    let cases: [ConformanceCase]
}

private struct ConformanceCase: Codable {
    let id: String
    let prompt: String
    let answer: String
    let sourceTitle: String
    let sourcePassage: String
    let sourceFileSeed: String
    let expectedStatus: String
    let requiresReview: Bool
    let sourcePresent: Bool?
    let tamperedPassage: String?
    let extractionQuality: Double?
    let extractionWarnings: [String]?

    enum CodingKeys: String, CodingKey {
        case id
        case prompt
        case answer
        case sourceTitle = "source_title"
        case sourcePassage = "source_passage"
        case sourceFileSeed = "source_file_seed"
        case expectedStatus = "expected_status"
        case requiresReview = "requires_review"
        case sourcePresent = "source_present"
        case tamperedPassage = "tampered_passage"
        case extractionQuality = "extraction_quality"
        case extractionWarnings = "extraction_warnings"
    }
}

final class CrossPlatformConformanceTests: XCTestCase {
    private func loadCorpus() throws -> ConformanceCorpus {
        let url = try XCTUnwrap(
            Bundle.module.url(
                forResource: "conformance_vectors",
                withExtension: "json",
                subdirectory: "Resources"
            )
        )

        let data = try Data(contentsOf: url)
        return try JSONDecoder().decode(
            ConformanceCorpus.self,
            from: data
        )
    }

    private func makeSource(
        _ vector: ConformanceCase
    ) -> [SourceSnapshot] {
        guard vector.sourcePresent != false else {
            return []
        }

        let passage = vector.tamperedPassage ?? vector.sourcePassage

        return [
            SourceSnapshot(
                snapshotID: vector.id,
                title: vector.sourceTitle,
                fileSHA256: SourceHasher.sha256Hex(
                    Data(vector.sourceFileSeed.utf8)
                ),
                passageSHA256: SourceHasher.normalizedTextSHA256(
                    vector.sourcePassage
                ),
                passage: passage,
                extractionQuality: vector.extractionQuality ?? 1.0,
                extractionWarnings: vector.extractionWarnings ?? [],
                locator: "page:1",
                version: "2022"
            )
        ]
    }

    func testSharedConformanceVectors() throws {
        let corpus = try loadCorpus()

        XCTAssertEqual(corpus.version, "1.1")

        let verifier = CurriculumVerifier()

        for vector in corpus.cases {
            let result = verifier.verify(
                prompt: vector.prompt,
                answer: vector.answer,
                sources: makeSource(vector)
            )

            XCTAssertEqual(
                result.status.rawValue,
                vector.expectedStatus,
                vector.id
            )

            XCTAssertEqual(
                result.requiresHumanReview,
                vector.requiresReview,
                vector.id
            )
        }
    }
}
