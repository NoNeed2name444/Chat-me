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
        let action =
            #"\b(?:stop|start|change|double|halve|take|skip|replace|increase|decrease|reduce)\b"#

        let medication =
            #"\b(?:medication|medicine|drug|dose|insulin|anticoagulant|metformin|warfarin|heparin|aspirin|ibuprofen|acetaminophen|amoxicillin|prednisone|levothyroxine|lisinopril)\b"#

        let interrogative =
            #"\b(?:should\s+i|should\s+we|can\s+i|may\s+i|what\s+should\s+i|what\s+do\s+i\s+do|how\s+should\s+i|do\s+i)\b"#

        let direct =
            #"^\s*(?:stop|start|change|double|halve|take|skip|replace|increase|decrease|reduce)\b.{0,100}"#
            + medication

        let contextual =
            interrogative + #".{0,100}"# + action
            + #".{0,100}"# + medication

        let directInstruction =
            #"(?:^|[.!?]\s+)(?:stop|start|change|double|halve|skip|replace|take)\b.{0,100}"#
            + medication

        let contextualInstruction =
            #"\b(?:you\s+should|you\s+need\s+to|you\s+must)\b.{0,100}"#
            + medication

        let urgent =
            #"\b(?:overdose|poisoning|poisoned|severe\s+bleeding|chest\s+pain|difficulty\s+breathing|anaphylaxis)\b.{0,120}"#
            + interrogative

        return lower.range(
            of: direct,
            options: .regularExpression
        ) != nil ||
        lower.range(
            of: directInstruction,
            options: .regularExpression
        ) != nil ||
        lower.range(
            of: contextualInstruction,
            options: .regularExpression
        ) != nil ||
        lower.range(
            of: contextual,
            options: .regularExpression
        ) != nil ||
        lower.range(
            of: urgent,
            options: .regularExpression
        ) != nil
    }
}

private struct AtomicClaim {
    let text: String
    let relation: String?
    let polarity: Bool
    let temporal: Set<String>
    let safety: String?
    let subjectAnchor: String
    let objectAnchor: String
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
        let dailyClaim = dailyDoseEquivalent(in: normalizedClaim)
        let dailySource = dailyDoseEquivalent(in: normalizedSource)
        let dailyEquivalent = dailyClaim != nil &&
            dailySource != nil &&
            abs(dailyClaim! - dailySource!) < 1e-9 &&
            doseRewordingKeepsTerms(
                claim: normalizedClaim,
                source: normalizedSource
            )

        let rewordedDoseReasons: Set<String> = [
            "atomic_claim_not_entailed",
            "atomic_object_mismatch",
            "atomic_relation_mismatch"
        ]
        var warnings: [String] =
            atomicReasoningWarnings(
                claim: normalizedClaim,
                source: normalizedSource
            )
            .filter {
                !(dailyEquivalent && rewordedDoseReasons.contains($0))
            }

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

        if atomicTermSubstituted(
            claim: normalizedClaim,
            source: normalizedSource
        ) {
            warnings.append("atomic_term_substituted")
        }

        return warnings
    }

    private static func atomicReasoningWarnings(
        claim: String,
        source: String
    ) -> [String] {
        let claimAtoms = atomicClaims(in: claim)
        let sourceAtoms = atomicClaims(in: source)

        guard !claimAtoms.isEmpty else {
            return []
        }

        var warnings: [String] = []

        for claimAtom in claimAtoms {
            var matched = false
            var failureReason = "atomic_claim_not_entailed"

            for sourceAtom in sourceAtoms {
                let check = atomPairCheck(claimAtom, sourceAtom)

                if check.carries {
                    matched = true
                    break
                }

                if let reason = check.reason {
                    failureReason = reason
                }
            }

            if !matched {
                warnings.append(failureReason)
            }
        }

        return Array(Set(warnings)).sorted()
    }

    // whether a source sentence carries a claim sentence and, when it does
    // not, why (no reason when the two share too few words to compare)
    private static func atomPairCheck(
        _ claimAtom: AtomicClaim,
        _ sourceAtom: AtomicClaim
    ) -> (carries: Bool, reason: String?) {
        let overlapValue = tokenOverlap(
            claimAtom.text,
            sourceAtom.text
        )

        guard overlapValue >= 0.45 else {
            return (false, nil)
        }

        if !claimAtom.subjectAnchor.isEmpty &&
            !sourceAtom.subjectAnchor.isEmpty &&
            !anchorsEquivalent(
                claimAtom.subjectAnchor,
                sourceAtom.subjectAnchor
            ) {
            return (false, "atomic_subject_mismatch")
        }

        if !claimAtom.objectAnchor.isEmpty &&
            !sourceAtom.objectAnchor.isEmpty &&
            claimAtom.objectAnchor != sourceAtom.objectAnchor {
            return (false, "atomic_object_mismatch")
        }

        if claimAtom.relation == "causal" &&
            sourceAtom.relation != "causal" {
            return (false, "causal_claim_requires_causal_evidence")
        }

        if claimAtom.relation == "interaction" &&
            sourceAtom.relation != "interaction" {
            return (false, "interaction_claim_requires_interaction_evidence")
        }

        if claimAtom.relation == "contraindication" &&
            sourceAtom.relation != "contraindication" {
            return (
                false,
                "contraindication_claim_requires_contraindication_evidence"
            )
        }

        if let relation = claimAtom.relation,
            relation != "mixed",
            relation != "unclassified",
            sourceAtom.relation != relation &&
            sourceAtom.relation != "mixed" {
            return (false, "atomic_relation_mismatch")
        }

        if !claimAtom.temporal.isEmpty &&
            claimAtom.temporal != sourceAtom.temporal {
            return (
                false,
                sourceAtom.temporal.isEmpty
                    ? "temporal_scope_missing"
                    : "temporal_scope_mismatch"
            )
        }

        if claimAtom.safety != nil &&
            claimAtom.safety != sourceAtom.safety {
            return (false, "safety_relation_mismatch")
        }

        if claimAtom.polarity != sourceAtom.polarity {
            return (false, "atomic_polarity_mismatch")
        }

        return (true, nil)
    }

    // the same lists as the Python verifier's (claim_reasoning._STOPWORDS,
    // FUNCTION_WORDS and ARTICLES); anchor(in:relation:before:) keeps its own
    private static let claimStopwords: Set<String> = [
        "a", "an", "the", "and", "or", "but", "for", "with",
        "in", "on", "to", "of", "is", "are", "was", "were",
        "that", "this", "these", "those", "patients", "patient",
        "all", "selected", "every", "everyone", "regardless", "only",
        "exclusively", "previously", "previous", "prior", "currently",
        "current", "now", "at", "present", "will", "planned", "plan",
        "expected", "future",
        "no", "not", "never", "without", "does", "doesn't",
        "cannot", "can't", "has", "have", "had"
    ]

    // words that join or qualify a phrase rather than name a drug, a
    // condition or an outcome ("after", "before", "more", "less" and the
    // like carry meaning)
    private static let functionWords: Set<String> = [
        "about", "across", "along", "also", "although", "among",
        "because", "been", "being", "between", "could", "from",
        "however", "into", "might", "onto", "shall", "such", "than",
        "their", "them", "then", "there", "therefore", "they", "though",
        "through", "throughout", "thus", "toward", "towards", "upon",
        "very", "what", "when", "where", "whereas", "whether", "which",
        "while", "whom", "whose", "would"
    ]

    private static let articles: Set<String> = ["a", "an", "the"]

    // words of three letters or fewer that name no drug, condition or
    // outcome. Other short words do: LDL for HDL, HIV for HBV, MI for PE, IV
    // for IM, men for women, vitamin K for vitamin D. The short ways of
    // writing a dose (mg, bd, tds) stay out, so a dose written another way
    // lines up as before.
    private static let shortNonTerms: Set<String> = [
        "am", "as", "at", "be", "by", "do", "eg", "ie", "if", "in", "is",
        "it", "no", "of", "on", "or", "so", "to", "up", "us", "vs", "we",
        "all", "and", "any", "are", "but", "can", "did", "due", "etc",
        "few", "for", "get", "got", "had", "has", "her", "him", "his",
        "how", "its", "let", "may", "nor", "not", "now", "off", "our",
        "out", "own", "per", "put", "say", "see", "she", "the", "too",
        "try", "use", "via", "was", "way", "who", "why", "yet", "you",
        "mg", "kg", "ml", "dl", "ug", "iu", "mcg", "mol", "hr", "hrs",
        "min", "od", "bd", "bid", "tid", "tds", "qds", "qid", "prn", "day",
        "one", "two", "six", "ten"
    ]

    // the short and long names of one route, so writing it the other way
    // is no swap ("500 mg PO" for "500 mg orally"), while two different
    // routes are one, whatever words stand around them ("given IV" for "IM
    // is given")
    private static let routeNames: [String: String] = [
        "po": "oral", "oral": "oral", "orally": "oral",
        "iv": "intravenous", "intravenous": "intravenous",
        "intravenously": "intravenous",
        "im": "intramuscular", "intramuscular": "intramuscular",
        "intramuscularly": "intramuscular",
        "sc": "subcutaneous", "sq": "subcutaneous",
        "subcut": "subcutaneous", "subcutaneous": "subcutaneous",
        "subcutaneously": "subcutaneous",
        "intrathecal": "intrathecal", "intrathecally": "intrathecal",
        "sublingual": "sublingual", "sublingually": "sublingual",
        "rectal": "rectal", "rectally": "rectal",
        "topical": "topical", "topically": "topical",
        "inhaled": "inhaled", "nebulised": "inhaled", "nebulized": "inhaled",
        "intranasal": "intranasal", "intranasally": "intranasal",
        "intradermal": "intradermal", "intradermally": "intradermal"
    ]

    // endings cut so another form of the same word still lines up; there is
    // no "-ate" or "-ic" rule, which would make nitrate and nitrite one word
    private static let stemRules: [(suffix: String, replacement: String)] = [
        ("isations", ""), ("izations", ""), ("isation", ""),
        ("ization", ""), ("ising", ""), ("izing", ""), ("ised", ""),
        ("ized", ""), ("ises", ""), ("izes", ""), ("ise", ""),
        ("ize", ""), ("ations", "at"), ("ation", "at"), ("ites", ""),
        ("ite", ""), ("isms", ""), ("ism", ""), ("ies", "y"),
        ("ied", "y"), ("ing", ""), ("eed", "eed"), ("ed", ""),
        ("sses", "ss"), ("ss", "ss"), ("us", "us"), ("is", "is"),
        ("es", ""), ("s", ""), ("e", "")
    ]

    private static func stem(_ word: String) -> String {
        var word = word

        if word.hasSuffix("'s") {
            word.removeLast(2)
        }

        word = word
            .replacingOccurrences(of: "ae", with: "e")
            .replacingOccurrences(of: "oe", with: "e")

        for rule in stemRules where word.hasSuffix(rule.suffix) &&
            word.count - rule.suffix.count >= 3 {
            return String(word.dropLast(rule.suffix.count)) +
                rule.replacement
        }

        return word
    }

    private static func contentWord(_ word: String) -> Bool {
        if word.count < 4 {
            return word.allSatisfy { $0.isLetter } &&
                !shortNonTerms.contains(word) &&
                !claimStopwords.contains(word)
        }

        return !claimStopwords.contains(word) &&
            !functionWords.contains(word) &&
            !doseWords.contains(word) &&
            !word.contains { $0.isNumber }
    }

    // ASCII letters and digits with apostrophes and hyphens, as the Python
    // verifier reads a sentence's words
    private static func termWords(in text: String) -> [String] {
        text
            .lowercased()
            .split {
                !($0.isASCII && ($0.isLetter || $0.isNumber)) &&
                    $0 != "'" &&
                    $0 != "-"
            }
            .map(String.init)
    }

    // the stretches of two word lists left over by their longest common
    // subsequence; at least one side of each has words
    private static func unmatchedRuns(
        _ left: [String],
        _ right: [String]
    ) -> [(Range<Int>, Range<Int>)] {
        var table = Array(
            repeating: Array(repeating: 0, count: right.count + 1),
            count: left.count + 1
        )

        for i in stride(from: left.count - 1, through: 0, by: -1) {
            for j in stride(from: right.count - 1, through: 0, by: -1) {
                table[i][j] = left[i] == right[j]
                    ? table[i + 1][j + 1] + 1
                    : max(table[i + 1][j], table[i][j + 1])
            }
        }

        var runs: [(Range<Int>, Range<Int>)] = []
        var i = 0
        var j = 0
        var startI = 0
        var startJ = 0

        while i < left.count && j < right.count {
            if left[i] == right[j] {
                if (startI, startJ) != (i, j) {
                    runs.append((startI..<i, startJ..<j))
                }
                i += 1
                j += 1
                startI = i
                startJ = j
            } else if table[i + 1][j] >= table[i][j + 1] {
                i += 1
            } else {
                j += 1
            }
        }

        if (startI, startJ) != (left.count, right.count) {
            runs.append((startI..<left.count, startJ..<right.count))
        }

        return runs
    }

    // a known alias, one route's two names, a short name spelled by the
    // initials of the other side's two words (AF, atrial fibrillation), or a
    // clotting factor and its activated form (X, Xa)
    private static func sameTerm(_ left: String, _ right: String) -> Bool {
        if anchorsEquivalent(left, right) {
            return true
        }

        if let route = routeNames[left], route == routeNames[right] {
            return true
        }

        for (short, long) in [(left, right), (right, left)] {
            let words = long.split(separator: " ")

            if short.count < 4, words.count == 2,
                short == String(words.compactMap(\.first)) {
                return true
            }

            if !short.isEmpty, short.allSatisfy({ "ivx".contains($0) }),
                long == short + "a" {
                return true
            }
        }

        return false
    }

    // one or two words on each side, every one a term, none said elsewhere
    // in the other sentence and no pair the same term (paracetamol,
    // acetaminophen)
    private static func isSwap(
        _ claimGap: [String],
        _ sourceGap: [String],
        claimStems: Set<String>,
        sourceStems: Set<String>
    ) -> Bool {
        guard (1...2).contains(claimGap.count),
            (1...2).contains(sourceGap.count),
            (claimGap + sourceGap).allSatisfy(contentWord),
            !claimGap.contains(where: { sourceStems.contains(stem($0)) }),
            !sourceGap.contains(where: { claimStems.contains(stem($0)) })
        else {
            return false
        }

        var pairs = [(
            claimGap.joined(separator: " "),
            sourceGap.joined(separator: " ")
        )]

        for left in claimGap {
            for right in sourceGap {
                pairs.append((left, right))
            }
        }

        return !pairs.contains { sameTerm($0.0, $0.1) }
    }

    private static func swapsATerm(
        claim: String,
        source: String
    ) -> Bool {
        let claimWords = termWords(in: claim)
        let sourceWords = termWords(in: source)
        let claimStems = claimWords.map(stem)
        let sourceStems = sourceWords.map(stem)

        for (claimRun, sourceRun) in unmatchedRuns(claimStems, sourceStems) {
            let claimGap = claimWords[claimRun].filter {
                !articles.contains($0)
            }
            let sourceGap = sourceWords[sourceRun].filter {
                !articles.contains($0)
            }

            if isSwap(
                claimGap,
                sourceGap,
                claimStems: Set(claimStems),
                sourceStems: Set(sourceStems)
            ) {
                return true
            }
        }

        let claimRoutes = Set(claimWords.compactMap { routeNames[$0] })
        let sourceRoutes = Set(sourceWords.compactMap { routeNames[$0] })

        return !claimRoutes.isEmpty && !sourceRoutes.isEmpty &&
            claimRoutes.isDisjoint(with: sourceRoutes)
    }

    // true when a claim sentence lines up with source sentences only by
    // putting another drug, condition or outcome in one place
    private static func atomicTermSubstituted(
        claim: String,
        source: String
    ) -> Bool {
        let sourceAtoms = atomicClaims(in: source)

        for claimAtom in atomicClaims(in: claim) {
            let carriers = sourceAtoms.filter {
                atomPairCheck(claimAtom, $0).carries
            }

            if !carriers.isEmpty && carriers.allSatisfy({
                swapsATerm(claim: claimAtom.text, source: $0.text)
            }) {
                return true
            }
        }

        return false
    }

    private static func atomicClaims(
        in text: String
    ) -> [AtomicClaim] {
        let fragments = text
            .split { character in
                character == "." ||
                character == ";" ||
                character == "!" ||
                character == "?"
            }
            .map(String.init)
            .map {
                $0.trimmingCharacters(in: .whitespacesAndNewlines)
            }
            .filter { !$0.isEmpty }

        return fragments.map { fragment in
            let lower = fragment.lowercased()
            let relationValue = relation(in: fragment)
            let relationName = relationValue ?? "unclassified"

            return AtomicClaim(
                text: fragment,
                relation: relationValue,
                polarity: polarity(of: fragment),
                temporal: temporalSignature(in: fragment),
                safety: safetyRelation(in: fragment),
                subjectAnchor: anchor(
                    in: fragment,
                    relation: relationName,
                    before: true
                ),
                objectAnchor: anchor(
                    in: fragment,
                    relation: relationName,
                    before: false
                )
            )
        }
    }

    private static func temporalSignature(
        in text: String
    ) -> Set<String> {
        let lower = text.lowercased()
        var result: Set<String> = []

        if [
            "previously", "prior", "history of",
            "in the past", "was", "were"
        ].contains(where: { lower.contains($0) }) {
            result.insert("past")
        }

        if [
            "currently", "at present", "now",
            "is taking", "are taking"
        ].contains(where: { lower.contains($0) }) {
            result.insert("current")
        }

        if [
            "will", "planned", "plan to",
            "expected to", "intends to", "future"
        ].contains(where: { lower.contains($0) }) {
            result.insert("future")
        }

        if ["before", "prior to"].contains(where: {
            lower.contains($0)
        }) {
            result.insert("before_event")
        }

        if ["after", "following"].contains(where: {
            lower.contains($0)
        }) {
            result.insert("after_event")
        }

        return result
    }

    private static func safetyRelation(
        in text: String
    ) -> String? {
        let lower = text.lowercased()

        if lower.contains("interacts with")
            || lower.contains("interaction")
            || lower.contains("do not combine")
            || lower.contains("should not be combined")
            || lower.contains("concomitant use") {
            return "interaction"
        }

        if lower.contains("contraindicated")
            || lower.contains("contraindication")
            || lower.contains("should not use")
            || lower.contains("do not use")
            || lower.contains("avoid") {
            return "contraindication"
        }

        return nil
    }

    private static func anchor(
        in text: String,
        relation: String,
        before: Bool
    ) -> String {
        let lower = text.lowercased()
        let phrases: [String]

        switch relation {
        case "causal":
            phrases = [
                "cause", "causes", "caused", "lead to",
                "leads to", "result in", "results in",
                "prevent", "prevents"
            ]
        case "risk_increase":
            phrases = [
                "increase", "increases", "increased",
                "raises", "elevates", "higher"
            ]
        case "risk_decrease":
            phrases = [
                "reduce", "reduces", "reduced",
                "lowers", "decrease", "decreases"
            ]
        case "association":
            phrases = [
                "associated with",
                "correlated with",
                "linked to"
            ]
        case "effectiveness":
            phrases = ["effective", "effectiveness", "efficacy"]
        case "interaction":
            phrases = [
                "interacts with",
                "interaction",
                "do not combine",
                "concomitant use"
            ]
        case "contraindication":
            phrases = [
                "contraindicated",
                "contraindication",
                "should not use",
                "do not use",
                "avoid"
            ]
        default:
            return ""
        }

        let matchingPhrases = phrases.filter {
            lower.range(of: $0) != nil
        }
        guard let phrase = matchingPhrases.sorted(by: {
            if $0.count != $1.count {
                return $0.count > $1.count
            }
            return $0 < $1
        }).first,
        let range = lower.range(of: phrase) else {
            return ""
        }

        let fragment = before
            ? String(lower[..<range.lowerBound])
            : String(lower[range.upperBound...])

        let stopwords: Set<String> = [
            "a", "an", "the", "and", "or", "but",
            "for", "with", "in", "on", "to", "of",
            "is", "are", "was", "were",
            "all", "selected", "every", "everyone",
            "regardless", "only", "exclusively",
            "previously", "previous", "prior", "currently",
            "current", "now", "at", "present", "will",
            "planned", "plan", "expected", "future",
            "no", "not", "never", "without", "does", "doesn't",
            "cannot", "can't", "has", "have", "had"
        ]

        let rawTokens = fragment
            .split {
                !$0.isLetter && !$0.isNumber
            }
            .map(String.init)

        let sequence = before
            ? Array(rawTokens.reversed())
            : rawTokens

        var selected: [String] = []
        var seenSubstantive = false

        for token in sequence {
            let isStopword = stopwords.contains(token) && token.count != 1

            if isStopword {
                if seenSubstantive {
                    break
                }
                continue
            }

            selected.append(token)
            seenSubstantive = true

            if selected.count >= 2 {
                break
            }
        }

        if before {
            selected.reverse()
        }

        return selected.joined(separator: " ")
    }

    private static func anchorsEquivalent(
        _ lhs: String,
        _ rhs: String
    ) -> Bool {
        let left = lhs.trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
        let right = rhs.trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()

        if left == right {
            return true
        }

        let aliases: [String: String] = [
            "paracetamol": "acetaminophen",
            "acetaminophen": "acetaminophen",
            "ibuprofen": "ibuprofen"
        ]

        return aliases[left] != nil &&
            aliases[right] != nil &&
            aliases[left] == aliases[right]
    }

    private static func tokenOverlap(
        _ lhs: String,
        _ rhs: String
    ) -> Double {
        let left = normalizedTokens(lhs)
        guard !left.isEmpty else {
            return 0
        }

        let right = normalizedTokens(rhs)
        return Double(left.intersection(right).count)
            / Double(left.count)
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
            if let value = calculator(text) {
                return value
            }
        }

        return nil
    }

    private static let doseWords: Set<String> = [
        "dose", "doses", "dosed", "dosing", "dosage", "dosages",
        "total", "amount", "divided",
        "milligram", "milligrams", "gram", "grams",
        "microgram", "micrograms",
        "millilitre", "millilitres", "milliliter", "milliliters",
        "litre", "litres", "liter", "liters", "kilogram", "kilograms",
        "daily", "once", "twice", "three", "four", "times", "every",
        "hour", "hours", "hourly", "week", "weekly",
        "take", "takes", "taken", "taking",
        "give", "gives", "given", "giving",
        "administer", "administers", "administered", "administering",
        "used", "uses", "using",
        "should", "must", "will", "with", "each", "that", "this",
        "from", "into"
    ]

    private static func doseRewordingKeepsTerms(
        claim: String,
        source: String
    ) -> Bool {
        let sourceWords = Set(asciiWords(in: source))

        return asciiWords(in: claim)
            .filter { $0.count >= 4 && !doseWords.contains($0) }
            .allSatisfy { sourceWords.contains($0) }
    }

    private static func asciiWords(in text: String) -> [String] {
        text
            .lowercased()
            .split { !($0.isASCII && $0.isLetter) }
            .map(String.init)
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

        if lower.contains("interacts with")
            || lower.contains("interaction")
            || lower.contains("do not combine") {
            return "interaction"
        }

        if lower.contains("contraindicated")
            || lower.contains("contraindication")
            || lower.contains("should not use")
            || lower.contains("do not use")
            || lower.contains("avoid") {
            return "contraindication"
        }

        if lower.contains("associated with")
            || lower.contains("correlated with")
            || lower.contains("linked to") {
            return "association"
        }

        if lower.contains("cause")
            || lower.contains("causes")
            || lower.contains("caused")
            || lower.contains("lead to")
            || lower.contains("leads to")
            || lower.contains("result in")
            || lower.contains("results in")
            || lower.contains("prevent")
            || lower.contains("prevents") {
            return "causal"
        }

        if lower.contains("increase")
            || lower.contains("increases")
            || lower.contains("raises")
            || lower.contains("higher") {
            return "risk_increase"
        }

        if lower.contains("reduce")
            || lower.contains("reduces")
            || lower.contains("lowers")
            || lower.contains("decrease")
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

    public init(version: String = "ios-core-0.9") {
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
            let sourceText = source.passage
            let overlap = SemanticGuard.overlap(
                claim: answer,
                source: sourceText
            )

            guard overlap >= 0.25 else {
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

        let promptTerms = Set(
            prompt
                .lowercased()
                .split {
                    !$0.isLetter && !$0.isNumber && $0 != "'"
                }
                .map(String.init)
                .filter { $0.count >= 4 }
        )
        let answerTerms = Set(
            answer
                .lowercased()
                .split {
                    !$0.isLetter && !$0.isNumber && $0 != "'"
                }
                .map(String.init)
                .filter { $0.count >= 4 }
        )

        let promptIntentAligned: Bool = {
            let lower = prompt.lowercased()
            let answerLower = answer.lowercased()

            if (
                (lower.contains("compatible") ||
                 lower.contains("compatibility") ||
                 lower.contains("interaction") ||
                 lower.contains("interactions"))
                && (
                    answerLower.contains("interacts with") ||
                    answerLower.contains("interaction")
                )
            ) {
                return true
            }

            if lower.contains("dose") || lower.contains("dosage") {
                return answerLower.range(
                    of: #"\b\d+(?:\.\d+)?\s*(?:mg|g|mcg|ug|ml|l|kg)\b"#,
                    options: .regularExpression
                ) != nil
            }

            return false
        }()

        let promptOverlap = selectedSources.contains {
            SemanticGuard.overlap(
                claim: prompt,
                source: $0.passage
            ) >= 0.25 ||
            !promptTerms.isDisjoint(with: answerTerms) ||
            promptIntentAligned
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
                requiresHumanReview: false,
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

    public init(verifierVersion: String = "ios-core-0.9") {
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
