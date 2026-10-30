import Foundation

/// The Sort screen's rules that need no screen: what capture keeps, the line
/// a row shows, the chooser's rows and their order, and where the launch
/// arguments the UI tests pass point the store. Foundation only, so it is
/// tested on Linux (tools/swift_suites.txt, suite ideasdesk).
///
/// `captured` is Stethoscore's capture guard (reference/stethoscore-aec2af1c/
/// ios/RedPen/Features/Notes/IdeasView.swift:129-130); the chooser's order is
/// its move dialog's (:380-388), with New folder added at its end.
enum IdeasDesk {

    /// What capture saves from the field: the words trimmed, or nil when
    /// there are none, so an empty field saves nothing.
    static func captured(_ draft: String) -> String? {
        let text = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        return text.isEmpty ? nil : text
    }

    /// The line a row shows: the title's first line, else the body's first
    /// line with words in it; nil when the idea has no words at all (the row
    /// then says Untitled).
    static func firstLine(title: String, body: String) -> String? {
        for text in [title, body] {
            for line in text.split(whereSeparator: \.isNewline) {
                let trimmed = line.trimmingCharacters(in: .whitespaces)
                if !trimmed.isEmpty { return trimmed }
            }
        }
        return nil
    }

    /// One folder as the chooser lists it: the store's outline order
    /// (NoteStore.folderOutline), its depth, and the folder it sits in.
    struct Place: Equatable {
        var id: UUID
        var name: String
        var depth: Int
        var parentId: UUID?
    }

    /// A row of the folder chooser.
    enum ChooserRow: Equatable {
        /// None: back to To sort.
        case none
        /// A folder, with the name of the folder it sits in (nil at the top).
        case folder(id: UUID, name: String, depth: Int, inside: String?)
        /// New folder, which names a folder, makes it and files the idea.
        case newFolder
    }

    /// The chooser's rows: None first, then every folder nested in the
    /// store's order, then New folder.
    static func chooserRows(_ outline: [Place]) -> [ChooserRow] {
        var names: [UUID: String] = [:]
        for place in outline { names[place.id] = place.name }
        let folders: [ChooserRow] = outline.map { place in
            .folder(id: place.id, name: place.name, depth: place.depth,
                    inside: place.parentId.flatMap { names[$0] })
        }
        return [.none] + folders + [.newFolder]
    }

    /// The screen opens on its empty state: a store with no ideas and no
    /// folders. With a folder there, the folders are listed instead, so they
    /// can still be reached.
    static func isEmpty(ideas: Int, folders: Int) -> Bool {
        ideas == 0 && folders == 0
    }

    // MARK: the launch arguments the UI tests pass

    /// `-SortaStore <name>`: the store is the file `vignette-notes.json` in
    /// the folder `<name>` under the app's temporary folder, new for a new
    /// name, so each UI test starts on a fresh store; the same name again
    /// reopens the same file. Nil without the argument (the real store).
    static func storeURL(arguments: [String], temporary: URL) -> URL? {
        guard let name = value(after: "-SortaStore", in: arguments), isPlainName(name) else { return nil }
        return temporary
            .appendingPathComponent("sorta-ui-stores", isDirectory: true)
            .appendingPathComponent(name, isDirectory: true)
            .appendingPathComponent("vignette-notes.json")
    }

    /// `-SortaImportFile <base64>`: the bytes of a file for Import ideas… to
    /// open in place of the system's picker, handed to the same call the
    /// picker's result makes. Nil without the argument, or when it isn't
    /// base64.
    static func importFile(arguments: [String]) -> Data? {
        guard let text = value(after: "-SortaImportFile", in: arguments) else { return nil }
        return Data(base64Encoded: text)
    }

    /// `-SortaNoticesStay`: a notice stays until the next one, as it does
    /// under VoiceOver, so a slow simulator can still reach its Undo.
    static func noticesStay(arguments: [String]) -> Bool {
        arguments.contains("-SortaNoticesStay")
    }

    private static func value(after flag: String, in arguments: [String]) -> String? {
        guard let at = arguments.firstIndex(of: flag), at + 1 < arguments.count else { return nil }
        return arguments[at + 1]
    }

    /// A folder name of letters, digits, dashes and underscores only, so a
    /// test's name can never reach outside the temporary folder.
    static func isPlainName(_ name: String) -> Bool {
        !name.isEmpty && name.count <= 64 && name.unicodeScalars.allSatisfy { scalar in
            (scalar.isASCII && CharacterSet.alphanumerics.contains(scalar)) || scalar == "-" || scalar == "_"
        }
    }
}
