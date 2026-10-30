import SwiftUI

/// Choose folder: where an idea goes. To sort first (no folder), then every
/// folder nested in the store's order, then New folder, which names a
/// folder, makes it and moves the idea into it. The row the idea is in now
/// is marked. Cancel leaves the idea where it is.
///
/// Stethoscore's move dialog (reference/stethoscore-aec2af1c/ios/RedPen/
/// Features/Notes/IdeasView.swift:97-99, its choices :380-392) as a sheet,
/// written as Sorta's map writes its folder sheet (ios/Sorta/Maps/
/// GraphMapPanels.swift:314-346, which stays frozen for S05b to point here),
/// with New folder and the naming sheet (NameSheet, unchanged) added.
struct FolderChooser: View {
    /// The folder the idea is in now (nil: To sort).
    let current: UUID?
    /// The idea goes to this folder (nil: To sort).
    let choose: (UUID?) -> Void
    /// A folder of this name is made and the idea goes into it.
    let makeFolder: (String) -> Void
    @EnvironmentObject private var notes: NoteStore
    @Environment(\.dismiss) private var dismiss
    @State private var naming = false
    /// A folder was made from the naming sheet, so this sheet closes too.
    @State private var made = false

    private var rows: [IdeasDesk.ChooserRow] {
        IdeasDesk.chooserRows(notes.folderOutline().map { entry in
            IdeasDesk.Place(id: entry.folder.id, name: entry.folder.name, depth: entry.depth,
                            parentId: entry.folder.parentId)
        })
    }

    var body: some View {
        NavigationStack {
            List {
                ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                    rowView(row)
                        .wardRowBackground()
                }
            }
            .wardForm()
            .wardFont(.body)
            .navigationTitle(IdeasWords.chooserTitle)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(IdeasWords.cancel) { dismiss() }
                        .buttonStyle(.wardQuiet)
                        .accessibilityIdentifier("chooser-cancel")
                }
                .sharedBackgroundVisibility(.hidden)
            }
            .sheet(isPresented: $naming, onDismiss: { if made { dismiss() } }) {
                NameSheet(title: IdeasWords.newFolder, prompt: IdeasWords.folderName, initial: "",
                          confirm: IdeasWords.create) { name in
                    made = true
                    makeFolder(name)
                }
            }
        }
        // full height: at .medium, iOS draws a sheet's content at 0.96 of
        // its size, which put the bar's 44-point Cancel at 42.25 (Chat-me
        // run 40987053659, measured at rest); the editor, at .large, keeps 44
        .presentationDetents([.large])
    }

    @ViewBuilder
    private func rowView(_ row: IdeasDesk.ChooserRow) -> some View {
        switch row {
        case .none:
            choice(name: IdeasWords.toSort, depth: 0, chosen: current == nil) { choose(nil); dismiss() }
                .accessibilityLabel(IdeasWords.toSortSpokenRow)
                .accessibilityIdentifier("chooser-to-sort")
        case let .folder(id, name, depth, inside):
            choice(name: name, depth: depth, chosen: current == id) { choose(id); dismiss() }
                .accessibilityLabel(name)
                .accessibilityValue(inside.map(IdeasWords.inside) ?? "")
                .accessibilityIdentifier("chooser-folder")
        case .newFolder:
            Button {
                naming = true
            } label: {
                Label(IdeasWords.newFolder, systemImage: "folder.badge.plus")
                    .foregroundStyle(Color.wardPrimaryInk)
                    .frame(maxWidth: .infinity, minHeight: WardControlSize.target, alignment: .leading)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .hoverEffect(.highlight)
            .accessibilityHint(IdeasWords.newFolderHint)
            .accessibilityIdentifier("chooser-new-folder")
        }
    }

    private func choice(name: String, depth: Int, chosen: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: WardSpace.m) {
                Text(IdeaIndent.name(name, depth: depth))
                    .foregroundStyle(Color.wardInk)
                Spacer(minLength: WardSpace.s)
                if chosen {
                    Image(systemName: "checkmark")
                        .wardFont(.body.weight(.semibold))
                        .foregroundStyle(Color.wardPrimaryInk)
                        .accessibilityHidden(true)
                }
            }
            .frame(minHeight: WardControlSize.target)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .hoverEffect(.highlight)
        .accessibilityAddTraits(chosen ? .isSelected : [])
    }
}
