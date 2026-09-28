import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

public indirect enum JSONValue: Codable, Sendable, Equatable {
    case string(String)
    case number(Double)
    case bool(Bool)
    case object([String: JSONValue])
    case array([JSONValue])
    case null

    public init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()

        if container.decodeNil() {
            self = .null
        } else if let value = try? container.decode(Bool.self) {
            self = .bool(value)
        } else if let value = try? container.decode(Double.self) {
            self = .number(value)
        } else if let value = try? container.decode(String.self) {
            self = .string(value)
        } else if let value = try? container.decode([String: JSONValue].self) {
            self = .object(value)
        } else if let value = try? container.decode([JSONValue].self) {
            self = .array(value)
        } else {
            throw DecodingError.dataCorruptedError(
                in: container,
                debugDescription: "Unsupported JSON value"
            )
        }
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()

        switch self {
        case .string(let value):
            try container.encode(value)
        case .number(let value):
            try container.encode(value)
        case .bool(let value):
            try container.encode(value)
        case .object(let value):
            try container.encode(value)
        case .array(let value):
            try container.encode(value)
        case .null:
            try container.encodeNil()
        }
    }
}

public enum APIClientError: Error, Equatable {
    case insecureTransport
    case invalidResponse
    case contractMismatch(expected: String, received: String)
    case server(String)
}

public struct VerificationAPIRequest: Codable, Sendable {
    public let claim: String
    public let questionContext: String?
    public let context: [String: JSONValue]
    public let sources: [String]
    public let requestedEvidenceLevel: String
    public let verificationMode: VerificationMode
    public let curriculumSourceIDs: [String]
    public let curriculumSnapshot: String?
    public let verificationContractVersion: String

    public init(
        claim: String,
        questionContext: String? = nil,
        context: [String: JSONValue] = [:],
        sources: [String] = ["pubmed", "openfda", "local"],
        requestedEvidenceLevel: String = "authoritative",
        verificationMode: VerificationMode = .currentMedical,
        curriculumSourceIDs: [String] = [],
        curriculumSnapshot: String? = nil,
        verificationContractVersion: String = "1.5"
    ) {
        self.claim = claim
        self.questionContext = questionContext
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
        case questionContext = "question_context"
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

public final class MedicalVerifierAPIClient: @unchecked Sendable {
    public let baseURL: URL
    public let bearerToken: String?
    public let contractVersion: String
    private let session: URLSession

    public init(
        baseURL: URL,
        bearerToken: String? = nil,
        contractVersion: String = "1.5",
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
        urlRequest.httpBody = try encoder.encode(request)

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
