import SwiftUI

/// An idea opened from the map, to read. Step 4 brings the editor; until
/// then this shows the idea's title and words, and Done closes it.
struct IdeaReadView: View {
    let noteID: UUID
    @EnvironmentObject private var notes: NoteStore
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: WardSpace.m) {
                    if let note = notes.note(noteID) {
                        Text(note.title)
                            .font(.title2.weight(.semibold))
                            .foregroundStyle(Color.wardInk)
                            .accessibilityAddTraits(.isHeader)
                        if !note.body.isEmpty {
                            Text(note.body)
                                .font(.body)
                                .foregroundStyle(Color.wardInk)
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(WardSpace.gutter)
            }
            .wardScreen()
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                        .buttonStyle(.wardCompact)
                }
                .sharedBackgroundVisibility(.hidden)
            }
        }
    }
}
