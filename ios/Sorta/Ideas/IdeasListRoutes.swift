import SwiftUI

/// Where the list's actions lead: the editor, the folder chooser, the naming
/// sheet and the folder's delete dialog. Each screen that holds the list (the
/// Sort screen, Map mode's list sheet) keeps one, and attaches them with
/// `ideasListSheets`, so both open the same ones (S05b checklist 5). Moved
/// here from IdeasView, unchanged.
@MainActor @Observable
final class IdeasListRoutes {
    /// The idea open in the editor.
    var opening: NoteRef?
    /// Opened from "You were saying…" (S06a): the editor puts the caret where the person stopped.
    var resuming: UUID?
    /// The idea whose folder is being chosen.
    var choosing: NoteRef?
    var naming: FolderPrompt?
    var deletingFolder: NoteFolder?

    /// The naming sheet, for a new folder or a renamed one.
    enum FolderPrompt: Identifiable {
        case new(parent: UUID?)
        case rename(NoteFolder)

        var id: String {
            switch self {
            case .new(let parent): return "new-\(parent?.uuidString ?? "top")"
            case .rename(let folder): return "rename-\(folder.id.uuidString)"
            }
        }
    }

    /// The list's actions, leading here; an idea's delete goes at once, with
    /// Undo (IdeasFiling).
    func actions(filing: IdeasFiling, notes: NoteStore) -> IdeasListActions {
        IdeasListActions(open: { self.opening = NoteRef(id: $0) },
                         chooseFolder: { self.choosing = NoteRef(id: $0) },
                         delete: { filing.deleteIdea($0, notes: notes) },
                         renameFolder: { self.naming = .rename($0) },
                         newFolderInside: { self.naming = .new(parent: $0.id) },
                         deleteFolder: { self.deletingFolder = $0 })
    }
}

extension View {
    /// The sheets and dialog `routes` opens, over the list browsing
    /// `folderId`; a folder made in the chooser goes in it, and when the
    /// folder is deleted the list goes back to the top.
    func ideasListSheets(_ routes: IdeasListRoutes, folderId: Binding<UUID?>) -> some View {
        modifier(IdeasListSheets(routes: routes, folderId: folderId))
    }
}

private struct IdeasListSheets: ViewModifier {
    @Bindable var routes: IdeasListRoutes
    @Binding var folderId: UUID?
    @EnvironmentObject private var notes: NoteStore
    @Environment(IdeasFiling.self) private var filing

    func body(content: Content) -> some View {
        content
            .sheet(item: $routes.opening, onDismiss: {
                routes.resuming = nil
                filing.offerEditorUndo(notes: notes)
            }) { ref in
                NoteEditorView(noteID: ref.id, file: { filing.fileFromEditor($0, in: $1, notes: notes) },
                               delete: { filing.deleteIdea($0, notes: notes) }, resume: routes.resuming == ref.id)
            }
            .sheet(item: $routes.choosing) { ref in
                FolderChooser(current: notes.note(ref.id)?.folderId,
                              choose: { filing.file(ref.id, in: $0, notes: notes) },
                              makeFolder: { name in
                                  let folder = notes.createFolder(name: name, parentId: folderId)
                                  filing.file(ref.id, in: folder.id, notes: notes)
                              })
            }
            .sheet(item: $routes.naming) { prompt in
                nameSheet(prompt)
            }
            .confirmationDialog(routes.deletingFolder.map { IdeasWords.deleteFolderTitle($0.name) } ?? "",
                                isPresented: deletingShown, titleVisibility: .visible,
                                presenting: routes.deletingFolder) { folder in
                Button(IdeasWords.deleteFolder, role: .destructive) { notes.deleteFolder(folder.id) }
                Button(IdeasWords.cancel, role: .cancel) {}
            } message: { folder in
                Text(IdeasWords.deleteFolderBody(notes.folder(folder.parentId)?.name ?? IdeasWords.toSort))
            }
            // the folder being browsed was deleted: back to the top
            .onChange(of: notes.folders) { _, _ in
                if folderId != nil, notes.folder(folderId) == nil { folderId = nil }
            }
    }

    private var deletingShown: Binding<Bool> {
        Binding(get: { routes.deletingFolder != nil }, set: { if !$0 { routes.deletingFolder = nil } })
    }

    @ViewBuilder
    private func nameSheet(_ prompt: IdeasListRoutes.FolderPrompt) -> some View {
        switch prompt {
        case .new(let parent):
            NameSheet(title: IdeasWords.newFolder, prompt: IdeasWords.folderName, initial: "",
                      confirm: IdeasWords.create) { name in
                _ = notes.createFolder(name: name, parentId: parent)
            }
        case .rename(let folder):
            NameSheet(title: IdeasWords.renameFolder, prompt: IdeasWords.folderName, initial: folder.name,
                      confirm: IdeasWords.rename) { name in
                notes.renameFolder(folder.id, to: name)
            }
        }
    }
}
