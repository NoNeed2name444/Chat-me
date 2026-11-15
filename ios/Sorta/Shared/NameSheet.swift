import SwiftUI

// Copied from Stethoscore (Features/Library/LibraryChrome.swift), the naming
// sheet alone.

/// A one-field naming sheet used for new folders, combined sets and renames -
/// the native stand-in for the web app's inline save forms.
///
/// One field on the base rather than a one-row Form: the field is the only
/// thing to touch, pressed into the base. Return saves; Cancel and the
/// confirm button sit in the system's own places at the top.
struct NameSheet: View {
    let title: String
    let prompt: String
    let initial: String
    let confirm: String
    let onConfirm: (String) -> Void
    /// A line under the field, from what is typed (S06b's folder words: what
    /// is kept); nil for none.
    var footnote: ((String) -> String)? = nil
    /// Whether a blank field may be confirmed (clearing a folder's words).
    var allowsBlank: Bool = false
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @FocusState private var focused: Bool

    init(title: String, prompt: String, initial: String, confirm: String,
         footnote: ((String) -> String)? = nil, allowsBlank: Bool = false,
         onConfirm: @escaping (String) -> Void) {
        self.title = title
        self.prompt = prompt
        self.initial = initial
        self.confirm = confirm
        self.footnote = footnote
        self.allowsBlank = allowsBlank
        self.onConfirm = onConfirm
    }

    /// Taller with a footnote, so the line shows above the keyboard.
    private static let plain: Set<PresentationDetent> = [.height(220), .medium]
    private static let noted: Set<PresentationDetent> = [.height(300), .medium]

    private var blank: Bool {
        name.trimmingCharacters(in: .whitespaces).isEmpty
    }

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: WardSpace.s) {
                field
                if let footnote {
                    Text(footnote(name))
                        .wardFont(.footnote)
                        .foregroundStyle(Color.wardInkSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilityIdentifier("nameSheetFootnote")
                }
                Spacer(minLength: 0)
            }
            .padding(WardSpace.gutter)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .wardScreen()
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() }.buttonStyle(.wardQuiet) }.sharedBackgroundVisibility(.hidden)
                ToolbarItem(placement: .confirmationAction) {
                    Button(confirm, action: submit)
                    .buttonStyle(.wardCompact)
                        .disabled(blank && !allowsBlank)
                }
                .sharedBackgroundVisibility(.hidden)
            }
            .onAppear { name = initial; focused = true }
        }
        .presentationDetents(footnote == nil ? Self.plain : Self.noted)
    }

    private var field: some View {
        TextField(prompt, text: $name)
            .wardFont(.body)
            .focused($focused)
            .submitLabel(.done)
            .onSubmit(submit)
            .frame(minHeight: 44)
            .wardField()
            // the sheet's own field: the page under the sheet keeps its
            // search field in the tree, so a test names this one
            .accessibilityIdentifier("nameSheetField")
    }

    private func submit() {
        let trimmed = name.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty || allowsBlank else { return }
        onConfirm(trimmed)
        dismiss()
    }
}
