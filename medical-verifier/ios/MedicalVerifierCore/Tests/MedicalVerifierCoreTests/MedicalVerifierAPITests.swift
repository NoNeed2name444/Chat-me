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
            verificationContractVersion: "1.0"
        )

        let data = try JSONEncoder().encode(request)
        let json = try XCTUnwrap(
            JSONSerialization.jsonObject(with: data)
                as? [String: Any]
        )

        XCTAssertEqual(
            json["verification_contract_version"] as? String,
            "1.0"
        )
        XCTAssertEqual(
            json["verification_mode"] as? String,
            "curriculum_update_aware"
        )
    }
}
