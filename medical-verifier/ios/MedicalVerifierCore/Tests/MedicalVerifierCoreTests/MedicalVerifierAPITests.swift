import XCTest
@testable import MedicalVerifierCore

final class MedicalVerifierAPITests: XCTestCase {
    func testRejectsPlainHTTP() {
        XCTAssertThrowsError(
            try MedicalVerifierAPIClient(
                baseURL: URL(string: "http://example.com")!
            )
        ) { error in
            XCTAssertEqual(
                error as? APIClientError,
                .insecureTransport
            )
        }
    }

    func testBuildsContractVersionedRequest() throws {
        let request = VerificationAPIRequest(
            claim: "Insulin lowers blood glucose.",
            verificationMode: .curriculumUpdateAware,
            curriculumSnapshot: "snapshot-1",
            questionContext: "What does insulin do?",
            verificationContractVersion: "1.1"
        )

        let data = try JSONEncoder().encode(request)
        let json = try XCTUnwrap(
            JSONSerialization.jsonObject(with: data)
                as? [String: Any]
        )

        XCTAssertEqual(
            json["verification_contract_version"] as? String,
            "1.1"
        )
        XCTAssertEqual(
            json["verification_mode"] as? String,
            "curriculum_update_aware"
        )
        XCTAssertEqual(
            json["question_context"] as? String,
            "What does insulin do?"
        )
    }
}
