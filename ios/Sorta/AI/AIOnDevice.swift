import Foundation
#if canImport(FoundationModels)
import FoundationModels
#endif

// MARK: - Apple's on-device model (S08 A1)
//
// Free, on the phone, and nothing leaves it. Where the phone lacks Apple
// Intelligence (as CI's simulators do) every call reports
// AIError.provider(.modelUnavailable) and nothing else happens. Answers are
// asked for with guided generation: the operation's JSON schema becomes a
// DynamicGenerationSchema.

final class AIAppleOnDevice: AIProvider {
    let id = AIProviderID.apple
    let sendsSchema = true
    /// Input and output together (section 5): ask sends at most 6 ideas.
    static let window = 4096

    init() {}

    var isAvailable: Bool {
        #if canImport(FoundationModels)
        if #available(iOS 26.0, macOS 26.0, *) {
            if case .available = SystemLanguageModel.default.availability { return true }
        }
        #endif
        return false
    }

    private final class Box: @unchecked Sendable {
        var result: Result<String, Error> = .failure(AIError.provider(.modelUnavailable))
    }

    func complete(_ r: AIRequest) throws -> AIResponse {
        try AIThread.requireBackground()
        guard isAvailable else { throw AIError.provider(.modelUnavailable) }
        #if canImport(FoundationModels)
        if #available(iOS 26.0, macOS 26.0, *) {
            let box = Box()
            let done = DispatchSemaphore(value: 0)
            let schema = r.schema.flatMap(AIJSON.parse).flatMap { Self.dynamic($0, name: r.schemaName ?? "answer") }
            Task.detached {
                do {
                    let session = LanguageModelSession(instructions: r.instructions)
                    let options = GenerationOptions(maximumResponseTokens: r.maxOutput)
                    if let schema {
                        let generation = try GenerationSchema(root: schema, dependencies: [])
                        let answer = try await session.respond(to: r.input, schema: generation, options: options)
                        box.result = .success(answer.content.jsonString)
                    } else {
                        let answer = try await session.respond(to: r.input, options: options)
                        box.result = .success(answer.content)
                    }
                } catch {
                    box.result = .failure(AIError.provider(.tryLater))
                }
                done.signal()
            }
            done.wait()
            return AIResponse(text: try box.result.get(), usage: AIUsage(inputTokens: 0, outputTokens: 0, costPico: 0))
        }
        #endif
        throw AIError.provider(.modelUnavailable)
    }

    #if canImport(FoundationModels)
    /// The operations' JSON schemas (ai/prompts/*.schema.json) as guided
    /// generation's: objects, arrays, strings, whole numbers, booleans, and
    /// a string that may be null.
    @available(iOS 26.0, macOS 26.0, *)
    static func dynamic(_ json: AIJSON, name: String) -> DynamicGenerationSchema? {
        let types: [String] = json["type"]?.string.map { [$0] } ?? json["type"]?.array?.compactMap(\.string) ?? []
        let kind = types.first(where: { $0 != "null" })
        switch kind {
        case "object":
            guard case let .object(props, keys)? = json["properties"] else { return nil }
            let required = Set(json["required"]?.array?.compactMap(\.string) ?? [])
            var properties: [DynamicGenerationSchema.Property] = []
            for key in keys {
                guard let sub = props[key], let schema = dynamic(sub, name: name + "_" + key) else { return nil }
                let nullable = (sub["type"]?.array?.contains(.string("null")) ?? false) || !required.contains(key)
                properties.append(DynamicGenerationSchema.Property(name: key, schema: schema, isOptional: nullable))
            }
            return DynamicGenerationSchema(name: name, properties: properties)
        case "array":
            guard let items = json["items"], let item = dynamic(items, name: name + "_item") else { return nil }
            return DynamicGenerationSchema(arrayOf: item)
        case "string": return DynamicGenerationSchema(type: String.self)
        case "integer": return DynamicGenerationSchema(type: Int.self)
        case "boolean": return DynamicGenerationSchema(type: Bool.self)
        default: return nil
        }
    }
    #endif
}
