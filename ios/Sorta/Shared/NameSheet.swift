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
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @FocusState private var focused: Bool

    init(title: String, prompt: String, initial: String, confirm: String,
         onConfirm: @escaping (String) -> Void) {
        self.title = title
        self.prompt = prompt
        self.initial = initial
        self.confirm = confirm
        self.onConfirm = onConfirm
    }

    private var blank: Bool {
        name.trimmingCharacters(in: .whitespaces).isEmpty
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                field
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
                        .disabled(blank)
                }
                .sharedBackgroundVisibility(.hidden)
            }
            .onAppear { name = initial; focused = true }
        }
        .presentationDetents([.height(220), .medium])
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
        guard !trimmed.isEmpty else { return }
        onConfirm(trimmed)
        dismiss()
    }
}
