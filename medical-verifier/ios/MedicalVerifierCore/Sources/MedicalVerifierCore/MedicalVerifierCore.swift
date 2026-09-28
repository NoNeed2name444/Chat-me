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
    case sourceIntegrityFailed = "SOURCE_INTEGRITY_FAILED"
    case sourceExtractionUncertain = "SOURCE_EXTRACTION_UNCERTAIN"
    case conflictingSources = "CONFLICTING_CURRICULUM_SOURCES"
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
    public let extractionQuality: Double
    public let extractionWarnings: [String]
    public let locator: String?
    public let version: String?

    public init(
        snapshotID: String,
        title: String,
        fileSHA256: String,
        passageSHA256: String,
        passage: String,
        extractionQuality: Double = 1.0,
        extractionWarnings: [String] = [],
        locator: String? = nil,
        version: String? = nil
    ) {
        self.snapshotID = snapshotID
        self.title = title
        self.fileSHA256 = fileSHA256
        self.passageSHA256 = passageSHA256
        self.passage = passage
        self.extractionQuality = extractionQuality
        self.extractionWarnings = extractionWarnings
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
        for medication in medicationTerms {
            guard let medicationRange = lower.range(of: medication) else {
                continue
            }

            let start = lower.index(
                medicationRange.lowerBound,
                offsetBy: -100,
                limitedBy: lower.startIndex
            ) ?? lower.startIndex

            let end = lower.index(
                medicationRange.upperBound,
                offsetBy: 100,
                limitedBy: lower.endIndex
            ) ?? lower.endIndex

            let window = lower[start..<end]

            if actionTerms.contains(where: { window.contains($0) }) {
                return true
            }
        }

        return false
    }
}

private enum SemanticGuard {
    static func validate(
        claim: String,
        source: String
    ) -> [String] {
        let normalizedClaim = normalizeDoubleNegation(claim)
        let normalizedSource = normalizeDoubleNegation(source)
        var warnings: [String] = []

        let dailyEquivalent = dailyMassDose(in: normalizedClaim) != nil &&
            dailyMassDose(in: normalizedSource) != nil &&
            abs(
                dailyMassDose(in: normalizedClaim)! -
                dailyMassDose(in: normalizedSource)!
            ) < 1e-9

        let claimNumbers = numbers(in: normalizedClaim)
        let sourceNumbers = numbers(in: normalizedSource)

        if !claimNumbers.isEmpty &&
            !claimNumbers.isSubset(of: sourceNumbers) &&
            !dailyEquivalent {
            warnings.append("numeric_values_not_found_in_curriculum")
        }

        let claimMeasurements = measurements(in: normalizedClaim)
        let sourceMeasurements = measurements(in: normalizedSource)

        if !claimMeasurements.isEmpty &&
            !claimMeasurements.isSubset(of: sourceMeasurements) &&
            !dailyEquivalent {
            warnings.append("measurement_units_or_values_not_supported")
        }

        let claimFrequency = frequencyMultiplier(in: normalizedClaim)
        let sourceFrequency = frequencyMultiplier(in: normalizedSource)

        if claimFrequency != nil &&
            sourceFrequency != nil &&
            abs(claimFrequency! - sourceFrequency!) > 1e-9 &&
            !dailyEquivalent {
            warnings.append("dose_frequency_mismatch")
        }

        let claimPopulations = populations(in: normalizedClaim)
        let sourcePopulations = populations(in: normalizedSource)

        if !claimPopulations.isSubset(of: sourcePopulations) {
            warnings.append("population_not_supported_by_curriculum")
        }

        let claimRelation = relation(in: normalizedClaim)
        let sourceRelation = relation(in: normalizedSource)

        if claimRelation != nil &&
            sourceRelation != nil &&
            claimRelation != sourceRelation {
            warnings.append("relation_class_mismatch")
        }

        if polarity(of: normalizedClaim) != polarity(of: normalizedSource) {
            warnings.append("claim_source_polarity_mismatch")
        }

        let claimCertainty = certaintyEscalators(in: normalizedClaim)
        let sourceCertainty = certaintyEscalators(in: normalizedSource)

        if !claimCertainty.isSubset(of: sourceCertainty) {
            warnings.append("certainty_strength_not_supported")
        }

        let claimScope = scopeStrength(in: normalizedClaim)
        let sourceScope = scopeStrength(in: normalizedSource)

        if claimScope.universal && !sourceScope.universal {
            warnings.append("universal_scope_not_supported")
        }

        if claimScope.exclusive && !sourceScope.exclusive {
            warnings.append("exclusive_scope_not_supported")
        }

        return warnings
    }

    private struct ScopeStrength {
        let universal: Bool
        let exclusive: Bool
    }

    private static func scopeStrength(in text: String) -> ScopeStrength {
        let lower = text.lowercased()

        return ScopeStrength(
            universal: [
                "all patients",
                "all people",
                "everyone",
                "every patient",
                "always",
                "never",
                "regardless of"
            ].contains { lower.contains($0) },
            exclusive: [
                "only",
                "exclusively",
                "only if"
            ].contains { lower.contains($0) }
        )
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
        let normalizedClaim = normalizeDoubleNegation(claim)
        let normalizedSource = normalizeDoubleNegation(source)
        let claimTokens = normalizedTokens(normalizedClaim)

        guard !claimTokens.isEmpty else { return 0 }

        let sourceTokens = normalizedTokens(normalizedSource)

        return Double(
            claimTokens.intersection(sourceTokens).count
        ) / Double(claimTokens.count)
    }

    private static func normalizeDoubleNegation(_ text: String) -> String {
        text
            .lowercased()
            .replacingOccurrences(of: "not uncommon", with: "common")
            .replacingOccurrences(of: "not unlikely", with: "likely")
            .replacingOccurrences(of: "not impossible", with: "possible")
    }

    private static func certaintyEscalators(in text: String) -> Set<String> {
        let lower = text.lowercased()

        return Set([
            "always", "never", "all", "none",
            "only", "guaranteed", "certain", "definitively"
        ].filter { lower.contains($0) })
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
            pattern.matches(in: text, range: range).compactMap {
                Range($0.range, in: text)
            }.map {
                String(text[$0])
            }
        )
    }

    private struct Measurement: Hashable {
        let value: Double
        let unit: String
    }

    private static func normalizeMeasurement(
        value: Double,
        unit: String
    ) -> Measurement {
        switch unit.lowercased() {
        case "mcg", "ug":
            return Measurement(value: value * 0.001, unit: "mg")
        case "g":
            return Measurement(value: value * 1000.0, unit: "mg")
        case "kg":
            return Measurement(value: value * 1_000_000.0, unit: "mg")
        case "l":
            return Measurement(value: value * 1000.0, unit: "ml")
        case "%", "percent":
            return Measurement(value: value, unit: "percent")
        default:
            return Measurement(value: value, unit: unit.lowercased())
        }
    }

    private static func measurements(in text: String) -> Set<Measurement> {
        let pattern = try? NSRegularExpression(
            pattern: #"\b(\d+(?:\.\d+)?)\s*(mg|g|mcg|ug|kg|ml|l|mmol|mmhg|%|percent)\b"#,
            options: [.caseInsensitive]
        )

        let range = NSRange(
            text.startIndex..<text.endIndex,
            in: text
        )

        guard let pattern else { return [] }

        return Set(
            pattern.matches(in: text, range: range).compactMap { match in
                guard
                    let valueRange = Range(match.range(at: 1), in: text),
                    let unitRange = Range(match.range(at: 2), in: text),
                    let value = Double(text[valueRange])
                else {
                    return nil
                }

                return normalizeMeasurement(
                    value: value,
                    unit: String(text[unitRange])
                )
            }
        )
    }

    private static func frequencyMultiplier(in text: String) -> Double? {
        let lower = text.lowercased()

        if lower.range(of: #"\b(twice|2\s+times)(?:\s+a)?\s+(?:day|daily)\b|\bbid\b"#, options: .regularExpression) != nil {
            return 2
        }

        if lower.range(of: #"\bthree\s+times(?:\s+a)?\s+(?:day|daily)\b|\btid\b"#, options: .regularExpression) != nil {
            return 3
        }

        if lower.range(of: #"\bfour\s+times(?:\s+a)?\s+(?:day|daily)\b|\bqid\b"#, options: .regularExpression) != nil {
            return 4
        }

        if lower.range(of: #"\bonce(?:\s+a)?\s+(?:day|daily)\b|\bdaily\b|\bqd\b"#, options: .regularExpression) != nil {
            return 1
        }

        if let pattern = try? NSRegularExpression(
            pattern: #"\bevery\s+(\d+)\s*(?:hours?|h)\b|\bq(\d+)h\b"#,
            options: [.caseInsensitive]
        ) {
            let range = NSRange(
                lower.startIndex..<lower.endIndex,
                in: lower
            )

            if let match = pattern.firstMatch(
                in: lower,
                options: [],
                range: range
            ) {
                for index in 1..<match.numberOfRanges {
                    if let valueRange = Range(
                        match.range(at: index),
                        in: lower
                    ),
                    let value = Int(lower[valueRange]) {
                        return 24.0 / Double(value)
                    }
                }
            }
        }

        if lower.range(
            of: #"\b(?:once\s+a\s+week|weekly)\b"#,
            options: .regularExpression
        ) != nil {
            return 1.0 / 7.0
        }

        if lower.range(
            of: #"\btwice\s+(?:a\s+)?week\b"#,
            options: .regularExpression
        ) != nil {
            return 2.0 / 7.0
        }

        return nil
    }

    private static func dailyMassDose(in text: String) -> Double? {
        let pattern = try? NSRegularExpression(
            pattern: #"\b(\d+(?:\.\d+)?)\s*(mg|g|mcg|ug|kg)\b"#,
            options: [.caseInsensitive]
        )

        guard let pattern else { return nil }

        let matches = pattern.matches(
            in: text,
            range: NSRange(text.startIndex..<text.endIndex, in: text)
        )

        guard matches.count == 1,
              let match = matches.first,
              let valueRange = Range(match.range(at: 1), in: text),
              let unitRange = Range(match.range(at: 2), in: text),
              let value = Double(text[valueRange]),
              let multiplier = frequencyMultiplier(in: text)
        else {
            return nil
        }

        let normalized = normalizeMeasurement(
            value: value,
            unit: String(text[unitRange])
        )

        return normalized.value * multiplier
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

        if lower.contains("safe") {
            return "safety"
        }

        if lower.contains("effective")
            || lower.contains("effectiveness")
            || lower.contains("efficacy") {
            return "effectiveness"
        }

        return nil
    }

    private static func polarity(of text: String) -> Bool {
        let lower = " " + text.lowercased() + " "

        let negatives = [
            " not ", "never ", " no ", " without ",
            "doesn't", "does not", "cannot", "can't"
        ]

        return !negatives.contains {
            lower.contains($0)
        }
    }
}

private func maxRisk(_ lhs: RiskLevel, _ rhs: RiskLevel) -> RiskLevel {
    let order: [RiskLevel: Int] = [
        .low: 0,
        .moderate: 1,
        .high: 2,
        .critical: 3
    ]

    return (order[lhs] ?? 0) >= (order[rhs] ?? 0)
        ? lhs
        : rhs
}

public struct CurriculumVerifier: Sendable {
    public let version: String

    public init(version: String = "ios-core-0.3") {
        self.version = version
    }

    public func verify(
        prompt: String,
        answer: String,
        sources: [SourceSnapshot]
    ) -> CurriculumVerificationResult {
        let risk = maxRisk(
            SafetyClassifier.risk(for: prompt),
            SafetyClassifier.risk(for: answer)
        )

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

        let integrityChecker = SourceIntegrityChecker()
        let integrityFailures = sources.filter {
            !integrityChecker.verify($0)
        }

        if !integrityFailures.isEmpty {
            return CurriculumVerificationResult(
                status: .sourceIntegrityFailed,
                riskLevel: risk,
                supportingSourceIDs: [],
                warnings: integrityFailures.map {
                    "source_integrity_failed:\($0.snapshotID)"
                },
                requiresHumanReview: true,
                snapshotIDs: sources.map { $0.snapshotID }
            )
        }

        let extractionFailures = sources.filter {
            $0.extractionQuality < 0.85
            || !$0.extractionWarnings.isEmpty
        }

        if !extractionFailures.isEmpty {
            return CurriculumVerificationResult(
                status: .sourceExtractionUncertain,
                riskLevel: risk,
                supportingSourceIDs: [],
                warnings: extractionFailures.flatMap { source in
                    [
                        "source_extraction_uncertain:\(source.snapshotID)"
                    ] + source.extractionWarnings.map {
                        "\($0):\(source.snapshotID)"
                    }
                },
                requiresHumanReview: true,
                snapshotIDs: sources.map { $0.snapshotID }
            )
        }

        if risk == .critical {
            return CurriculumVerificationResult(
                status: .safetyEscalation,
                riskLevel: risk,
                supportingSourceIDs: [],
                warnings: ["clinical_action_request_requires_human_review"],
                requiresHumanReview: true,
                snapshotIDs: sources.map { $0.snapshotID }
            )
        }

        var supported: [String] = []
        var contradicted: [String] = []
        var warnings: [String] = []

        for source in sources {
            let sourceText = source.title + " " + source.passage
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
                    $0 + ":" + source.snapshotID
                })

                if semanticWarnings.contains(
                    "claim_source_polarity_mismatch"
                ) {
                    contradicted.append(source.snapshotID)
                }
            }
        }

        if !supported.isEmpty && !contradicted.isEmpty {
            return CurriculumVerificationResult(
                status: .conflictingSources,
                riskLevel: risk,
                supportingSourceIDs: supported,
                warnings: Array(
                    Set(
                        warnings
                        + ["conflicting_curriculum_sources"]
                    )
                ).sorted(),
                requiresHumanReview: true,
                snapshotIDs: sources.map { $0.snapshotID }
            )
        }

        let promptOverlap = sources.contains {
            SemanticGuard.overlap(
                claim: prompt,
                source: $0.passage
            ) >= 0.25
        }

        if !promptOverlap {
            warnings.append(
                "question_prompt_not_aligned_to_curriculum"
            )
        }

        if !supported.isEmpty && promptOverlap {
            return CurriculumVerificationResult(
                status: .validated,
                riskLevel: risk,
                supportingSourceIDs: supported,
                warnings: Array(Set(warnings)).sorted(),
                requiresHumanReview: risk != .low || !warnings.isEmpty,
                snapshotIDs: sources.map { $0.snapshotID }
            )
        }

        if !supported.isEmpty {
            return CurriculumVerificationResult(
                status: .sourceUncertain,
                riskLevel: risk,
                supportingSourceIDs: supported,
                warnings: Array(Set(warnings)).sorted(),
                requiresHumanReview: true,
                snapshotIDs: sources.map { $0.snapshotID }
            )
        }

        return CurriculumVerificationResult(
            status: .sourceUnsupported,
            riskLevel: risk,
            supportingSourceIDs: [],
            warnings: Array(
                Set(
                    warnings
                    + ["answer_not_supported_by_curriculum"]
                )
            ).sorted(),
            requiresHumanReview: true,
            snapshotIDs: sources.map { $0.snapshotID }
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

    public init(verifierVersion: String = "ios-core-0.2") {
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
