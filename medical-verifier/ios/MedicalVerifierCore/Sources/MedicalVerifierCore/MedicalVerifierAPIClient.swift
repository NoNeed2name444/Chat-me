import Foundation

public enum APIClientError: Error, Equatable {
    case insecureTransport
    case invalidResponse
    case contractMismatch(expected: String, received: String)
    case server(String)
}

public struct VerificationAPIRequest: Codable, Sendable {
    public let claim: String
    public let context: [String: String]
    public let sources: [String]
    public let requestedEvidenceLevel: String
    public let verificationMode: VerificationMode
    public let curriculumSourceIDs: [String]
    public let curriculumSnapshot: String?
    public let verificationContractVersion: String

    public init(
        claim: String,
        context: [String: String] = [:],
        sources: [String] = ["pubmed", "openfda", "local"],
        requestedEvidenceLevel: String = "authoritative",
        verificationMode: VerificationMode = .currentMedical,
        curriculumSourceIDs: [String] = [],
        curriculumSnapshot: String? = nil,
        verificationContractVersion: String = "1.0"
    ) {
        self.claim = claim
        self.context = context
        self.sources = sources
        self.requestedEvidenceLevel = requestedEvidenceLevel
        self.verificationMode = verificationMode
        self.curriculumSourceIDs = curriculumSourceIDs
        self.curriculumSnapshot = curriculumSnapshot
        self.verificationContractVersion = verificationContractVersion
    }

    enum CodingKeys: String, CodingKey {
        case claim
        case context
        case sources
        case requestedEvidenceLevel = "requested_evidence_level"
        case verificationMode = "verification_mode"
        case curriculumSourceIDs = "curriculum_source_ids"
        case curriculumSnapshot = "curriculum_snapshot"
        case verificationContractVersion = "verification_contract_version"
    }
}

public struct VerificationAPIResponse: Codable, Sendable {
    public let verdict: String
    public let confidence: Double
    public let confidenceSemantics: String
    public let verificationMode: VerificationMode
    public let knowledgeDivergence: String
    public let studyHint: String?
    public let requiresHumanReview: Bool
    public let verifierVersion: String
    public let verificationContractVersion: String
    public let knowledgeSnapshot: String
    public let verificationID: String

    enum CodingKeys: String, CodingKey {
        case verdict
        case confidence
        case confidenceSemantics = "confidence_semantics"
        case verificationMode = "verification_mode"
        case knowledgeDivergence = "knowledge_divergence"
        case studyHint = "study_hint"
        case requiresHumanReview = "requires_human_review"
        case verifierVersion = "verifier_version"
        case verificationContractVersion = "verification_contract_version"
        case knowledgeSnapshot = "knowledge_snapshot"
        case verificationID = "verification_id"
    }
}

public final class MedicalVerifierAPIClient: Sendable {
    public let baseURL: URL
    public let bearerToken: String?
    public let contractVersion: String
    private let session: URLSession

    public init(
        baseURL: URL,
        bearerToken: String? = nil,
        contractVersion: String = "1.0",
        session: URLSession = .shared
    ) throws {
        guard baseURL.scheme?.lowercased() == "https" else {
            throw APIClientError.insecureTransport
        }

        self.baseURL = baseURL
        self.bearerToken = bearerToken
        self.contractVersion = contractVersion
        self.session = session
    }

    public func verify(
        _ request: VerificationAPIRequest
    ) async throws -> VerificationAPIResponse {
        var endpoint = baseURL
        endpoint.append(path: "v1/verify")

        var urlRequest = URLRequest(url: endpoint)
        urlRequest.httpMethod = "POST"
        urlRequest.setValue(
            "application/json",
            forHTTPHeaderField: "Content-Type"
        )
        urlRequest.setValue(
            "application/json",
            forHTTPHeaderField: "Accept"
        )

        if let bearerToken, !bearerToken.isEmpty {
            urlRequest.setValue(
                "Bearer \(bearerToken)",
                forHTTPHeaderField: "Authorization"
            )
        }

        let encoder = JSONEncoder()
        urlRequest.httpBody = try encoder.encode(
            request
        )

        let (data, response) = try await session.data(
            for: urlRequest
        )

        guard let httpResponse = response as? HTTPURLResponse else {
            throw APIClientError.invalidResponse
        }

        guard (200..<300).contains(httpResponse.statusCode) else {
            let serverMessage = String(
                data: data,
                encoding: .utf8
            ) ?? "server_error"

            throw APIClientError.server(serverMessage)
        }

        let decoded = try JSONDecoder().decode(
            VerificationAPIResponse.self,
            from: data
        )

        guard decoded.verificationContractVersion == contractVersion else {
            throw APIClientError.contractMismatch(
                expected: contractVersion,
                received: decoded.verificationContractVersion
            )
        }

        return decoded
    }
}
