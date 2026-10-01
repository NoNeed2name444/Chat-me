import Foundation

public struct OnlineVerificationResult: Sendable {
    public let curriculum: CurriculumVerificationResult
    public let currentMedical: VerificationAPIResponse?
    public let networkError: String?
    public let authoritativeSource: String

    public init(
        curriculum: CurriculumVerificationResult,
        currentMedical: VerificationAPIResponse?,
        networkError: String?,
        authoritativeSource: String
    ) {
        self.curriculum = curriculum
        self.currentMedical = currentMedical
        self.networkError = networkError
        self.authoritativeSource = authoritativeSource
    }
}

public struct OnlineFirstVerifier: Sendable {
    public let curriculumVerifier: CurriculumVerifier
    public let apiClient: MedicalVerifierAPIClient

    public init(
        curriculumVerifier: CurriculumVerifier = CurriculumVerifier(),
        apiClient: MedicalVerifierAPIClient
    ) {
        self.curriculumVerifier = curriculumVerifier
        self.apiClient = apiClient
    }

    public func verify(
        prompt: String,
        answer: String,
        sources: [SourceSnapshot],
        context: [String: JSONValue] = [:],
        verificationMode: VerificationMode = .curriculumUpdateAware
    ) async -> OnlineVerificationResult {
        let localResult = curriculumVerifier.verify(
            prompt: prompt,
            answer: answer,
            sources: sources
        )

        if localResult.status == .sourceIntegrityFailed
            || localResult.status == .safetyEscalation {
            return OnlineVerificationResult(
                curriculum: localResult,
                currentMedical: nil,
                networkError: nil,
                authoritativeSource: "local-safety-gate"
            )
        }

        let request = VerificationAPIRequest(
            claim: answer,
            questionContext: prompt,
            context: context,
            verificationMode: verificationMode,
            curriculumSourceIDs: sources.map { $0.snapshotID },
            curriculumSnapshot: sources.first?.snapshotID,
            verificationContractVersion: apiClient.contractVersion
        )

        do {
            let current = try await apiClient.verify(request)

            return OnlineVerificationResult(
                curriculum: localResult,
                currentMedical: current,
                networkError: nil,
                authoritativeSource: "server-verifier"
            )
        } catch {
            return OnlineVerificationResult(
                curriculum: localResult,
                currentMedical: nil,
                networkError: String(describing: error),
                authoritativeSource: "curriculum-only-due-to-network-error"
            )
        }
    }
}
