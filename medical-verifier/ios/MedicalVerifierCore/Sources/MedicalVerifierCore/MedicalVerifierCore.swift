import Foundation
import CryptoKit

public enum VerificationMode: String, Codable, Sendable {
    case currentMedical = "current_medical"
    case curriculumFaithful = "curriculum_faithful"
    case curriculumUpdateAware = "curriculum_update_aware"
}

public enum CurriculumStatus: String, Codable, Sendable {
    case validated = "VALIDATED"
    case sourceUncertain = "SOURCE_UNCERTAIN"
    case sourceUnsupported = "SOURCE_UNSUPPORTED"
    case sourceUnavailable = "SOURCE_UNAVAILABLE"
    case safetyEscalation = "SAFETY_ESCALATION"
}

public enum RiskLevel: String, Codable, Sendable {
    case low
    case moderate
    case high
    case critical
}

public struct SourceSnapshot: Codable, Sendable, Hashable {
    public let snapshotID: String
    public let title: String
    public let fileSHA256: String
    public let passageSHA256: String
    public let passage: String
    public let locator: String?
    public let version: String?

    public init(
        snapshotID: String,
        title: String,
        fileSHA256: String,
        passageSHA256: String,
        passage: String,
        locator: String? = nil,
        version: String? = nil
    ) {
        self.snapshotID = snapshotID
        self.title = title
        self.fileSHA256 = fileSHA256
        self.passageSHA256 = passageSHA256
        self.passage = passage
        self.locator = locator
        self.version = version
    }
}

public struct QuestionArtifact: Codable, Sendable {
    public let questionID: String
    public let prompt: String
    public let answer: String
    public let curriculumSnapshotID: String
    public let sourceEvidenceIDs: [String]
    public let sourceLocators: [String]
    public let sourceVersions: [String]
    public let sourceFileHashes: [String: String]
    public let sourcePassageHashes: [String: String]
    public let validationStatus: CurriculumStatus
    public let warnings: [String]
    public let requiresHumanReview: Bool
    public let verifierVersion: String
}

public struct CurriculumVerificationResult: Codable, Sendable {
    public let status: CurriculumStatus
    public let riskLevel: RiskLevel
    public let supportingSourceIDs: [String]
    public let warnings: [String]
    public let requiresHumanReview: Bool
    public let snapshotIDs: [String]
}

public enum SourceHasher {
    public static func sha256Hex(_ data: Data) -> String {
        SHA256.hash(data: data)
            .map { String(format: "%02x", $0) }
            .joined()
    }

    public static func normalizedText(_ text: String) -> String {
        text
            .split { $0.isWhitespace }
            .joined(separator: " ")
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    public static func normalizedTextSHA256(_ text: String) -> String {
        sha256Hex(Data(normalizedText(text).utf8))
    }
}

private enum SafetyClassifier {
    static let medicationTerms = [
        "medication", "medicine", "drug", "dose", "insulin",
        "anticoagulant", "metformin", "warfarin", "heparin",
        "aspirin", "ibuprofen", "acetaminophen", "amoxicillin",
        "prednisone", "levothyroxine", "lisinopril"
    ]

    static let actionTerms = [
        "stop", "start", "change", "double", "halve", "take",
        "skip", "replace", "increase", "decrease", "reduce"
    ]

    static func risk(for text: String) -> RiskLevel {
        let lower = text.lowercased()

        if containsActionRequest(lower) {
            return .critical
        }

        let highTerms = [
            "pregnancy", "pregnant", "overdose", "stroke",
            "chest pain", "insulin", "chemotherapy",
            "anticoagulant", "suicide", "bleeding"
        ]

        if highTerms.contains(where: { lower.contains($0) }) {
            return .high
        }

        if lower.contains("dose")
            || lower.contains("interaction")
            || lower.contains("diagnosis")
            || lower.contains("kidney")
            || lower.contains("liver")
            || lower.contains("allergy") {
            return .moderate
        }

        return .low
    }

    static func containsActionRequest(_ lower: String) -> Bool {
        actionTerms.contains { action in
            guard let range = lower.range(of: action) else {
                return false
            }

            let suffix = lower[range.upperBound...]
            let window = suffix.prefix(100)
            return medicationTerms.contains {
                window.contains($0)
            }
        }
    }
}

private enum SemanticGuard {
    static let relationTerms = [
        "causes", "caused", "leads to", "results in",
        "increases", "raises", "higher", "reduces",
        "lowers", "decreases", "associated with",
        "correlated with", "linked to", "safe", "effective"
    ]

    static func validate(
        claim: String,
        source: String
    ) -> [String] {
        let claimNumbers = numbers(in: claim)
        let sourceNumbers = numbers(in: source)

        var warnings: [String] = []

        if !claimNumbers.isEmpty && !claimNumbers.isSubset(of: sourceNumbers) {
            warnings.append("numeric_values_not_found_in_curriculum")
        }

        let claimPopulations = populations(in: claim)
        let sourcePopulations = populations(in: source)

        if !claimPopulations.isSubset(of: sourcePopulations) {
            warnings.append("population_not_supported_by_curriculum")
        }

        let claimRelation = relation(in: claim)
        let sourceRelation = relation(in: source)

        if claimRelation != nil &&
            sourceRelation != nil &&
            claimRelation != sourceRelation {
            warnings.append("relation_class_mismatch")
        }

        if polarity(of: claim) != polarity(of: source) {
            warnings.append("claim_source_polarity_mismatch")
        }

        if claim.contains("%") && !source.contains("%") {
            warnings.append("percentage_measurement_not_supported")
        }

        return warnings
    }

    private static func normalizedTokens(_ text: String) -> Set<String> {
        Set(
            text
                .lowercased()
                .split {
                    !$0.isLetter && !$0.isNumber && $0 != "'"
                }
                .map(String.init)
                .filter { $0.count >= 4 }
        )
    }

    static func overlap(claim: String, source: String) -> Double {
        let claimTokens = normalizedTokens(claim)
        guard !claimTokens.isEmpty else { return 0 }

        let sourceTokens = normalizedTokens(source)
        return Double(claimTokens.intersection(sourceTokens).count)
            / Double(claimTokens.count)
    }

    private static func numbers(in text: String) -> Set<String> {
        let pattern = try? NSRegularExpression(
            pattern: #"\b\d+(?:\.\d+)?\b"#
        )

        let range = NSRange(
            text.startIndex..<text.endIndex,
            in: text
        )

        guard let pattern else { return [] }

        return Set(
            pattern.matches(
                in: text,
                range: range
            ).compactMap {
                Range($0.range, in: text)
            }.map {
                String(text[$0])
            }
        )
    }

    private static func populations(in text: String) -> Set<String> {
        let lower = text.lowercased()
        return Set([
            "adult", "adults", "child", "children",
            "pediatric", "elderly", "pregnancy",
            "pregnant", "renal", "kidney",
            "hepatic", "liver"
        ].filter { lower.contains($0) })
    }

    private static func relation(in text: String) -> String? {
        let lower = text.lowercased()

        if lower.contains("associated with")
            || lower.contains("correlated with")
            || lower.contains("linked to") {
            return "association"
        }

        if lower.contains("causes")
            || lower.contains("caused")
            || lower.contains("leads to")
            || lower.contains("results in") {
            return "causal"
        }

        if lower.contains("increases")
            || lower.contains("raises")
            || lower.contains("higher") {
            return "risk_increase"
        }

        if lower.contains("reduces")
            || lower.contains("lowers")
            || lower.contains("decreases") {
            return "risk_decrease"
        }

        if lower.contains("safe")
            || lower.contains("effective") {
            return "property"
        }

        return nil
    }

    private static func polarity(of text: String) -> Bool {
        let lower = text.lowercased()
        let negatives = [
            " not ", "never ", "no ", "without ",
            "doesn't", "does not", "cannot", "can't"
        ]

        return !negatives.contains {
            lower.contains($0)
        }
    }
}

public struct CurriculumVerifier: Sendable {
    public let version: String

    public init(version: String = "ios-core-0.1") {
        self.version = version
    }

    public func verify(
        prompt: String,
        answer: String,
        sources: [SourceSnapshot]
    ) -> CurriculumVerificationResult {
        let risk = SafetyClassifier.risk(for: prompt)

        if risk == .critical {
            return CurriculumVerificationResult(
                status: .safetyEscalation,
                riskLevel: risk,
                supportingSourceIDs: [],
                warnings: ["clinical_action_request_requires_human_review"],
                requiresHumanReview: true,
                snapshotIDs: sources.map(.snapshotID)
            )
        }

        guard !sources.isEmpty else {
            return CurriculumVerificationResult(
                status: .sourceUnavailable,
                riskLevel: risk,
                supportingSourceIDs: [],
                warnings: ["no_curriculum_sources"],
                requiresHumanReview: true,
                snapshotIDs: []
            )
        }

        var supported: [String] = []
        var warnings: [String] = []

        for source in sources {
            let sourceText = "(source.title) (source.passage)"
            let overlap = SemanticGuard.overlap(
                claim: answer,
                source: sourceText
            )

            guard overlap >= 0.50 else {
                continue
            }

            let semanticWarnings = SemanticGuard.validate(
                claim: answer,
                source: source.passage
            )

            if semanticWarnings.isEmpty {
                supported.append(source.snapshotID)
            } else {
                warnings.append(contentsOf: semanticWarnings.map {
                    "($0):(source.snapshotID)"
                })
            }
        }

        let promptOverlap = sources.contains {
            SemanticGuard.overlap(
                claim: prompt,
                source: $0.passage
            ) >= 0.25
        }

        if !promptOverlap {
            warnings.append("question_prompt_not_aligned_to_curriculum")
        }

        if !supported.isEmpty && promptOverlap {
            return CurriculumVerificationResult(
                status: .validated,
                riskLevel: risk,
                supportingSourceIDs: supported,
                warnings: Array(Set(warnings)).sorted(),
                requiresHumanReview: risk != .low || !warnings.isEmpty,
                snapshotIDs: sources.map(.snapshotID)
            )
        }

        if !supported.isEmpty {
            return CurriculumVerificationResult(
                status: .sourceUncertain,
                riskLevel: risk,
                supportingSourceIDs: supported,
                warnings: Array(Set(warnings)).sorted(),
                requiresHumanReview: true,
                snapshotIDs: sources.map(.snapshotID)
            )
        }

        return CurriculumVerificationResult(
            status: .sourceUnsupported,
            riskLevel: risk,
            supportingSourceIDs: [],
            warnings: Array(Set(
                warnings + ["answer_not_supported_by_curriculum"]
            )).sorted(),
            requiresHumanReview: true,
            snapshotIDs: sources.map(.snapshotID)
        )
    }
}

public struct SourceIntegrityChecker: Sendable {
    public init() {}

    public func verify(_ source: SourceSnapshot) -> Bool {
        SourceHasher.normalizedTextSHA256(source.passage)
            == source.passageSHA256
    }

    public func verify(
        sourceFileData: Data,
        against source: SourceSnapshot
    ) -> Bool {
        SourceHasher.sha256Hex(sourceFileData)
            == source.fileSHA256
    }
}

public struct QuestionArtifactFactory: Sendable {
    public let verifierVersion: String

    public init(verifierVersion: String = "ios-core-0.1") {
        self.verifierVersion = verifierVersion
    }

    public func make(
        prompt: String,
        answer: String,
        source: SourceSnapshot,
        validation: CurriculumVerificationResult
    ) -> QuestionArtifact {
        QuestionArtifact(
            questionID: UUID().uuidString,
            prompt: prompt,
            answer: answer,
            curriculumSnapshotID: source.snapshotID,
            sourceEvidenceIDs: validation.supportingSourceIDs,
            sourceLocators: source.locator.map { [$0] } ?? [],
            sourceVersions: source.version.map { [$0] } ?? [],
            sourceFileHashes: [
                source.snapshotID: source.fileSHA256
            ],
            sourcePassageHashes: [
                source.snapshotID: source.passageSHA256
            ],
            validationStatus: validation.status,
            warnings: validation.warnings,
            requiresHumanReview: validation.requiresHumanReview,
            verifierVersion: verifierVersion
        )
    }
}
