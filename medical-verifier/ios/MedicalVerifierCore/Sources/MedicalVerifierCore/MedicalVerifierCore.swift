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
    public let pageNumber: Int?
    public let section: String?
    public let blockType: String
    public let blockIndex: Int?
    public let relatedBlockIDs: [String]
    public let language: String
    public let precedenceGroup: String?
    public let precedenceRank: Int
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
        pageNumber: Int? = nil,
        section: String? = nil,
        blockType: String = "text",
        blockIndex: Int? = nil,
        relatedBlockIDs: [String] = [],
        language: String = "auto",
        precedenceGroup: String? = nil,
        precedenceRank: Int = 0,
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
        self.pageNumber = pageNumber
        self.section = section
        self.blockType = blockType
        self.blockIndex = blockIndex
        self.relatedBlockIDs = relatedBlockIDs
        self.language = language
        self.precedenceGroup = precedenceGroup
        self.precedenceRank = precedenceRank
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
    public let sourcePages: [String: Int]
    public let sourceSections: [String: String]
    public let sourceBlockTypes: [String: String]
    public let sourceRelatedBlockIDs: [String: [String]]
    public let sourceLanguages: [String: String]
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
public struct SourceManifestEntry: Codable, Sendable, Hashable {
    public let evidenceID: String
    public let sourceSnapshotSHA256: String?
    public let passageSHA256: String?
    public let locator: String?
    public let pageNumber: Int?
    public let section: String?
    public let blockType: String
    public let blockIndex: Int?
    public let relatedBlockIDs: [String]
    public let language: String
    public let version: String?
    public let precedenceGroup: String?
    public let precedenceRank: Int
}

public struct SourceManifest: Codable, Sendable, Hashable {
    public let manifestID: String
    public let manifestVersion: String
    public let parentManifestSHA256: String?
    public let entries: [SourceManifestEntry]
    public let manifestSHA256: String

    public static func build(
        manifestID: String,
        sources: [SourceSnapshot],
        parentManifestSHA256: String? = nil
    ) -> SourceManifest {
        let entries = sources
            .map {
                SourceManifestEntry(
                    evidenceID: $0.snapshotID,
                    sourceSnapshotSHA256: $0.fileSHA256,
                    passageSHA256: $0.passageSHA256,
                    locator: $0.locator,
                    pageNumber: $0.pageNumber,
                    section: $0.section,
                    blockType: $0.blockType,
                    blockIndex: $0.blockIndex,
                    relatedBlockIDs: $0.relatedBlockIDs.sorted(),
                    language: $0.language,
                    version: $0.version,
                    precedenceGroup: $0.precedenceGroup,
                    precedenceRank: $0.precedenceRank
                )
            }
            .sorted {
                $0.evidenceID < $1.evidenceID
            }

        let provisional = SourceManifest(
            manifestID: manifestID,
            manifestVersion: "1",
            parentManifestSHA256: parentManifestSHA256,
            entries: entries,
            manifestSHA256: ""
        )

        return SourceManifest(
            manifestID: manifestID,
            manifestVersion: provisional.manifestVersion,
            parentManifestSHA256: parentManifestSHA256,
            entries: entries,
            manifestSHA256: provisional.digest()
        )
    }

    public func verify() -> Bool {
        digest() == manifestSHA256
    }

    public func verifiesParent(_ parent: SourceManifest?) -> Bool {
        guard let parentManifestSHA256 else {
            return parent == nil
        }

        guard let parent else {
            return false
        }

        return parent.verify() &&
            parent.manifestSHA256 == parentManifestSHA256
    }

    private func digest() -> String {
        var encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        let provisional = SourceManifest(
            manifestID: manifestID,
            manifestVersion: manifestVersion,
            parentManifestSHA256: parentManifestSHA256,
            entries: entries,
            manifestSHA256: ""
        )

        guard let data = try? encoder.encode(provisional) else {
            return ""
        }

        return SourceHasher.sha256Hex(data)
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
        let normalizedClaim = normalizeDoubleNegation(
            normalizeMultilingualTerms(claim)
        )
        let normalizedSource = normalizeDoubleNegation(
            normalizeMultilingualTerms(source)
        )
        var warnings: [String] = []

        let dailyClaim = dailyDoseEquivalent(in: normalizedClaim)
        let dailySource = dailyDoseEquivalent(in: normalizedSource)
        let dailyEquivalent = dailyClaim != nil &&
            dailySource != nil &&
            abs(dailyClaim! - dailySource!) < 1e-9

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

        if let conditionReason = conditionSupported(
            claim: normalizedClaim,
            source: normalizedSource
        ) {
            warnings.append(conditionReason)
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
        let normalizedClaim = normalizeDoubleNegation(
            normalizeMultilingualTerms(claim)
        )
        let normalizedSource = normalizeDoubleNegation(
            normalizeMultilingualTerms(source)
        )
        let claimTokens = normalizedTokens(normalizedClaim)

        guard !claimTokens.isEmpty else {
            return 0
        }

        let sourceTokens = normalizedTokens(normalizedSource)

        return Double(
            claimTokens.intersection(sourceTokens).count
        ) / Double(claimTokens.count)
    }

    private static func normalizeMultilingualTerms(
        _ text: String
    ) -> String {
        var lower = text.lowercased()

        let replacements: [String: String] = [
            "no es": "does not",
            "no": "no",
            "non": "not",
            "ne": "not",
            "pas": "not",
            "causa": "causes",
            "causas": "causes",
            "causé": "caused",
            "causado": "caused",
            "asociado con": "associated with",
            "associé à": "associated with",
            "associé a": "associated with",
            "aumenta": "increases",
            "augmente": "increases",
            "reduce": "reduces",
            "réduit": "reduces",
            "seguro": "safe",
            "sûr": "safe",
            "efectivo": "effective",
            "efficace": "effective",
            "pacientes": "patients",
            "patients": "patients",
            "niños": "children",
            "enfants": "children",
            "adultos": "adults",
            "adultes": "adults"
        ]

        for (source, replacement) in replacements {
            lower = lower.replacingOccurrences(
                of: source,
                with: replacement
            )
        }

        return lower
    }

    private static func normalizeDoubleNegation(
        _ text: String
    ) -> String {
        text
            .lowercased()
            .replacingOccurrences(of: "not uncommon", with: "common")
            .replacingOccurrences(of: "not unlikely", with: "likely")
            .replacingOccurrences(of: "not impossible", with: "possible")
    }

    private static func certaintyEscalators(
        in text: String
    ) -> Set<String> {
        let lower = text.lowercased()

        return Set([
            "always", "never", "all", "none",
            "only", "guaranteed", "certain", "definitively"
        ].filter { lower.contains($0) })
    }

    private static func numbers(
        in text: String
    ) -> Set<String> {
        let pattern = try? NSRegularExpression(
            pattern: #"\b\d+(?:\.\d+)?\b"#
        )

        let range = NSRange(
            text.startIndex..<text.endIndex,
            in: text
        )

        guard let pattern else {
            return []
        }

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
            return Measurement(
                value: value * 0.001,
                unit: "mg"
            )
        case "g":
            return Measurement(
                value: value * 1000.0,
                unit: "mg"
            )
        case "kg":
            return Measurement(
                value: value * 1_000_000.0,
                unit: "mg"
            )
        case "l":
            return Measurement(
                value: value * 1000.0,
                unit: "ml"
            )
        case "%", "percent":
            return Measurement(
                value: value,
                unit: "percent"
            )
        default:
            return Measurement(
                value: value,
                unit: unit.lowercased()
            )
        }
    }

    private static func measurements(
        in text: String
    ) -> Set<Measurement> {
        let pattern = try? NSRegularExpression(
            pattern: #"\b(\d+(?:\.\d+)?)\s*(mg|g|mcg|ug|kg|ml|l|mmol|mmhg|%|percent)\b"#,
            options: [.caseInsensitive]
        )

        let range = NSRange(
            text.startIndex..<text.endIndex,
            in: text
        )

        guard let pattern else {
            return []
        }

        return Set(
            pattern.matches(
                in: text,
                range: range
            ).compactMap { match in
                guard
                    let valueRange = Range(
                        match.range(at: 1),
                        in: text
                    ),
                    let unitRange = Range(
                        match.range(at: 2),
                        in: text
                    ),
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

    private static func frequencyMultiplier(
        in text: String
    ) -> Double? {
        let lower = text.lowercased()

        if lower.range(
            of: #"\b(twice|2\s+times)(?:\s+a)?\s+(?:day|daily)\b|\bbid\b"#,
            options: .regularExpression
        ) != nil {
            return 2
        }

        if lower.range(
            of: #"\bthree\s+times(?:\s+a)?\s+(?:day|daily)\b|\btid\b"#,
            options: .regularExpression
        ) != nil {
            return 3
        }

        if lower.range(
            of: #"\bfour\s+times(?:\s+a)?\s+(?:day|daily)\b|\bqid\b"#,
            options: .regularExpression
        ) != nil {
            return 4
        }

        if lower.range(
            of: #"\bonce(?:\s+a)?\s+(?:day|daily)\b|\bdaily\b|\bqd\b"#,
            options: .regularExpression
        ) != nil {
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

    private static func dailyMassDose(
        in text: String
    ) -> Double? {
        if text.range(
            of: #"\b\d+(?:\.\d+)?\s*(?:mg|g|mcg|ug)\s*(?:/|per)\s*(?:ml|l)\b"#,
            options: .regularExpression
        ) != nil {
            return nil
        }

        if text.range(
            of: #"\b\d+(?:\.\d+)?\s*(?:mg|g|mcg|ug)\s*/\s*kg\b"#,
            options: .regularExpression
        ) != nil {
            return nil
        }

        let pattern = try? NSRegularExpression(
            pattern: #"\b(\d+(?:\.\d+)?)\s*(mg|g|mcg|ug)\b"#,
            options: [.caseInsensitive]
        )

        guard let pattern else {
            return nil
        }

        let matches = pattern.matches(
            in: text,
            range: NSRange(
                text.startIndex..<text.endIndex,
                in: text
            )
        )

        guard
            matches.count == 1,
            let match = matches.first,
            let valueRange = Range(
                match.range(at: 1),
                in: text
            ),
            let unitRange = Range(
                match.range(at: 2),
                in: text
            ),
            let value = Double(text[valueRange]),
            let multiplier = frequencyMultiplier(in: text)
        else {
            return nil
        }

        let normalized = normalizeMeasurement(
            value: value,
            unit: String(text[unitRange])
        )

        guard normalized.unit == "mg" else {
            return nil
        }

        return normalized.value * multiplier
    }

    private static func concentrationDailyDose(
        in text: String
    ) -> Double? {
        let concentrationPattern = try? NSRegularExpression(
            pattern: #"\b(\d+(?:\.\d+)?)\s*(mg|g|mcg|ug)\s*(?:/|per)\s*(ml|l)\b"#,
            options: [.caseInsensitive]
        )

        let volumePattern = try? NSRegularExpression(
            pattern: #"\b(\d+(?:\.\d+)?)\s*(ml|l)\b"#,
            options: [.caseInsensitive]
        )

        guard
            let concentrationPattern,
            let volumePattern
        else {
            return nil
        }

        let range = NSRange(
            text.startIndex..<text.endIndex,
            in: text
        )

        let concentrations = concentrationPattern.matches(
            in: text,
            range: range
        )
        let volumes = volumePattern.matches(
            in: text,
            range: range
        )

        guard
            concentrations.count == 1,
            volumes.count == 1,
            let multiplier = frequencyMultiplier(in: text),
            let massRange = Range(
                concentrations[0].range(at: 1),
                in: text
            ),
            let massUnitRange = Range(
                concentrations[0].range(at: 2),
                in: text
            ),
            let volumeRange = Range(
                volumes[0].range(at: 1),
                in: text
            ),
            let volumeUnitRange = Range(
                volumes[0].range(at: 2),
                in: text
            ),
            let mass = Double(text[massRange]),
            let volume = Double(text[volumeRange])
        else {
            return nil
        }

        let massNormalized = normalizeMeasurement(
            value: mass,
            unit: String(text[massUnitRange])
        )
        let volumeNormalized = normalizeMeasurement(
            value: volume,
            unit: String(text[volumeUnitRange])
        )

        guard
            massNormalized.unit == "mg",
            volumeNormalized.unit == "ml"
        else {
            return nil
        }

        return massNormalized.value *
            volumeNormalized.value *
            multiplier
    }

    private static func weightBasedDailyDose(
        in text: String
    ) -> Double? {
        let dosePattern = try? NSRegularExpression(
            pattern: #"\b(\d+(?:\.\d+)?)\s*(mg|g|mcg|ug)\s*/\s*kg(\s*/\s*day)?\b"#,
            options: [.caseInsensitive]
        )

        let weightPattern = try? NSRegularExpression(
            pattern: #"\b(\d+(?:\.\d+)?)\s*kg\b"#,
            options: [.caseInsensitive]
        )

        guard
            let dosePattern,
            let weightPattern
        else {
            return nil
        }

        let range = NSRange(
            text.startIndex..<text.endIndex,
            in: text
        )

        let doses = dosePattern.matches(
            in: text,
            range: range
        )
        let weights = weightPattern.matches(
            in: text,
            range: range
        )

        guard
            doses.count == 1,
            weights.count == 1,
            let valueRange = Range(
                doses[0].range(at: 1),
                in: text
            ),
            let unitRange = Range(
                doses[0].range(at: 2),
                in: text
            ),
            let weightValueRange = Range(
                weights[0].range(at: 1),
                in: text
            ),
            let value = Double(text[valueRange]),
            let weight = Double(text[weightValueRange])
        else {
            return nil
        }

        let normalized = normalizeMeasurement(
            value: value,
            unit: String(text[unitRange])
        )

        guard normalized.unit == "mg" else {
            return nil
        }

        let multiplier: Double

        if doses[0].range(at: 3).location != NSNotFound {
            multiplier = 1.0
        } else if let frequency = frequencyMultiplier(in: text) {
            multiplier = frequency
        } else {
            return nil
        }

        return normalized.value * weight * multiplier
    }

    private static func dailyDoseEquivalent(
        in text: String
    ) -> Double? {
        for calculator in [
            dailyMassDose,
            concentrationDailyDose,
            weightBasedDailyDose
        ] {
            if let value = calculator(in: text) {
                return value
            }
        }

        return nil
    }

    private static func conditionSignatures(
        in text: String
    ) -> [Set<String>] {
        let lower = " " + text.lowercased() + " "
        let patterns = [
            #"\bif\s+([^,.;:]+)"#,
            #"\bonly if\s+([^,.;:]+)"#,
            #"\bunless\s+([^,.;:]+)"#,
            #"\bwhen\s+([^,.;:]+)"#,
            #"\bprovided that\s+([^,.;:]+)"#,
            #"\bin patients with\s+([^,.;:]+)"#,
            #"\bfor patients with\s+([^,.;:]+)"#
        ]

        var signatures: [Set<String>] = []

        for patternText in patterns {
            guard let pattern = try? NSRegularExpression(
                pattern: patternText,
                options: [.caseInsensitive]
            ) else {
                continue
            }

            let range = NSRange(
                lower.startIndex..<lower.endIndex,
                in: lower
            )

            for match in pattern.matches(
                in: lower,
                range: range
            ) {
                guard let captured = Range(
                    match.range(at: 1),
                    in: lower
                ) else {
                    continue
                }

                let tokens = lower[captured]
                    .split {
                        !$0.isLetter && !$0.isNumber && $0 != "'"
                    }
                    .map(String.init)
                    .filter { $0.count >= 4 }

                if !tokens.isEmpty {
                    signatures.append(Set(tokens))
                }
            }
        }

        return signatures
    }

    private static func conditionSupported(
        claim: String,
        source: String
    ) -> String? {
        let sourceConditions = conditionSignatures(in: source)

        guard !sourceConditions.isEmpty else {
            return nil
        }

        let claimConditions = conditionSignatures(in: claim)

        guard !claimConditions.isEmpty else {
            return "conditional_scope_missing"
        }

        for sourceCondition in sourceConditions {
            let best = claimConditions.map { claimCondition in
                Double(
                    sourceCondition.intersection(
                        claimCondition
                    ).count
                ) / Double(
                    max(1, sourceCondition.count)
                )
            }.max() ?? 0

            if best < 0.70 {
                return "condition_not_entrailed"
            }
        }

        return nil
    }

    private static func populations(
        in text: String
    ) -> Set<String> {
        let lower = text.lowercased()

        return Set([
            "adult", "adults", "child", "children",
            "pediatric", "elderly", "pregnancy",
            "pregnant", "renal", "kidney",
            "hepatic", "liver"
        ].filter { lower.contains($0) })
    }

    private static func relation(
        in text: String
    ) -> String? {
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

    private static func polarity(
        of text: String
    ) -> Bool {
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

    public init(version: String = "ios-core-0.6") {
        self.version = version
    }

    private func applyExplicitPrecedence(
        to sources: [SourceSnapshot]
    ) -> ([SourceSnapshot], [String]) {
        var selected = sources.filter {
            $0.precedenceGroup == nil
        }
        var warnings: [String] = []

        let grouped = Dictionary(
            grouping: sources.filter {
                $0.precedenceGroup != nil
            },
            by: {
                $0.precedenceGroup!
            }
        )

        for group in grouped.keys.sorted() {
            guard let items = grouped[group] else {
                continue
            }

            let highestRank = items.map {
                $0.precedenceRank
            }.max() ?? 0

            let winners = items.filter {
                $0.precedenceRank == highestRank
            }

            selected.append(contentsOf: winners)

            let excluded = items.filter {
                $0.precedenceRank < highestRank
            }

            if !excluded.isEmpty {
                warnings.append(
                    "explicit_precedence_excluded:" +
                    excluded.map { $0.snapshotID }.sorted().joined(separator: ",")
                )
            }
        }

        return (selected, warnings)
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

        let (selectedSources, precedenceWarnings) =
            applyExplicitPrecedence(to: sources)

        let integrityChecker = SourceIntegrityChecker()
        let integrityFailures = selectedSources.filter {
            !integrityChecker.verify($0)
        }

        if !integrityFailures.isEmpty {
            return CurriculumVerificationResult(
                status: .sourceIntegrityFailed,
                riskLevel: risk,
                supportingSourceIDs: [],
                warnings: integrityFailures.map {
                    "source_integrity_failed:\($0.snapshotID)"
                } + precedenceWarnings,
                requiresHumanReview: true,
                snapshotIDs: sources.map { $0.snapshotID }
            )
        }

        let extractionFailures = selectedSources.filter {
            $0.extractionQuality < 0.85
            || !$0.extractionWarnings.isEmpty
        }

        if !extractionFailures.isEmpty {
            return CurriculumVerificationResult(
                status: .sourceExtractionUncertain,
                riskLevel: risk,
                supportingSourceIDs: [],
                warnings: precedenceWarnings + extractionFailures.flatMap { source in
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
                warnings: precedenceWarnings + ["clinical_action_request_requires_human_review"],
                requiresHumanReview: true,
                snapshotIDs: sources.map { $0.snapshotID }
            )
        }

        var supported: [String] = []
        var contradicted: [String] = []
        var warnings: [String] = precedenceWarnings

        for source in selectedSources {
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

        let promptOverlap = selectedSources.contains {
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

    public init(verifierVersion: String = "ios-core-0.6") {
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
            sourcePages: source.pageNumber.map {
                [source.snapshotID: $0]
            } ?? [:],
            sourceSections: source.section.map {
                [source.snapshotID: $0]
            } ?? [:],
            sourceBlockTypes: [
                source.snapshotID: source.blockType
            ],
            sourceRelatedBlockIDs: [
                source.snapshotID: source.relatedBlockIDs
            ],
            sourceLanguages: [
                source.snapshotID: source.language
            ],
            validationStatus: validation.status,
            warnings: validation.warnings,
            requiresHumanReview: validation.requiresHumanReview,
            verifierVersion: verifierVersion
        )
    }
}
