import SwiftUI

/// The filings and deletes both modes make, and the notice that offers Undo
/// for each (S05b checklist 7): S05's, moved here from IdeasView so Map mode
/// files and deletes with the same words and the same Undo. One for the
/// window (IdeasRoot), in the environment; the notice shows above the switch
/// in either mode (IdeasFoot).
@MainActor @Observable
final class IdeasFiling {
    var notice: IdeasNotice?
    /// A filing made in the editor, offered with Undo when it closes.
    private var editorFiling: EditorFiling?

    /// Moves an idea into a folder (nil: To sort) and offers Undo.
    func file(_ id: UUID, in folder: UUID?, notes: NoteStore) {
        guard let idea = notes.note(id), idea.folderId != folder else { return }
        let from: UUID? = idea.folderId
        notes.move(id, to: folder)
        let text: String = notes.folder(folder).map { IdeasWords.movedTo($0.name) } ?? IdeasWords.backInToSort
        notice = IdeasNotice(text: text, undo: .moved(idea: id, from: from))
    }

    /// A filing from the editor's folder chip: the idea moves now, and Undo
    /// is offered when the editor closes, since the editor covers the
    /// notice. Filed again in the same visit, Undo still goes back to where
    /// the idea was before the first move; moved back there, nothing is
    /// offered.
    func fileFromEditor(_ id: UUID, in folder: UUID?, notes: NoteStore) {
        guard let idea = notes.note(id), idea.folderId != folder else { return }
        let from: UUID? = editorFiling?.idea == id ? editorFiling?.from : idea.folderId
        notes.move(id, to: folder)
        editorFiling = folder == from ? nil : EditorFiling(idea: id, from: from)
    }

    func offerEditorUndo(notes: NoteStore) {
        guard let filing = editorFiling else { return }
        editorFiling = nil
        // deleted in the editor: its own notice, with its own Undo, stays
        guard let idea = notes.note(filing.idea), idea.folderId != filing.from else { return }
        let text: String = notes.folder(idea.folderId).map { IdeasWords.movedTo($0.name) } ?? IdeasWords.backInToSort
        notice = IdeasNotice(text: text, undo: .moved(idea: filing.idea, from: filing.from))
    }

    func deleteIdea(_ id: UUID, notes: NoteStore) {
        guard let removal = notes.remove(id) else { return }
        notice = IdeasNotice(text: IdeasWords.ideaDeleted, undo: .deleted(removal))
    }

    func undo(_ step: IdeasNotice.Undo, notes: NoteStore) {
        switch step {
        case let .moved(idea, from):
            // the folder it came from may have gone since; then To sort
            notes.move(idea, to: notes.folder(from)?.id)
        case let .deleted(removal):
            notes.undo(removal)
        }
    }
}

/// The foot of both modes: the notice, then the Sort / Map switch, bottom
/// centre, WardSpace.s above the home indicator, in the same place in both
/// (S05b checklist 2). The switch is folded while the person types, whatever
/// was stored, and opening it then lets go of the caret, so the strip has
/// room: Stethoscore's Ideas bar (IdeasBottomBar.swift:31, 69-80).
/// `notices` is false while a sheet with its own notice covers the foot
/// (Map mode's list sheet), and in the map's pane beside the Sort screen,
/// so a notice shows, and is announced, once; `switches` is false on two
/// panes, where both modes show.
struct IdeasFoot: View {
    @Binding var mode: SortaMode
    var typing: Bool = false
    var letGo: () -> Void = {}
    var notices: Bool = true
    var switches: Bool = true
    @Environment(IdeasFiling.self) private var filing
    @EnvironmentObject private var notes: NoteStore
    @AppStorage(SortaMode.collapsedKey) private var collapsed = false

    private var collapsedBinding: Binding<Bool> {
        let stored = $collapsed
        let held: Bool = typing
        let letGo = letGo
        return Binding(
            get: { stored.wrappedValue || held },
            set: { now in
                if !now && held { letGo() }
                stored.wrappedValue = now
            }
        )
    }

    var body: some View {
        @Bindable var filing = filing
        VStack(spacing: 0) {
            if notices {
                IdeasNoticeHost(notice: $filing.notice, undo: { filing.undo($0, notes: notes) })
                    .frame(maxWidth: IdeasLayout.column)
            }
            if switches {
                FloatingSwitcher(items: SortaMode.items, selection: $mode, collapsed: collapsedBinding,
                                 toggleIdentifier: SortaMode.toggleIdentifier)
                    .padding(.horizontal, WardSpace.gutter)
                    .padding(.bottom, WardSpace.s)
            }
        }
    }
}
