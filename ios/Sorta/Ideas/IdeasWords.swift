import Foundation

/// Every word the Ideas screens show or say, in one place, each keyed in
/// Localizable.xcstrings with the words of S05's ux-writing pass (the keys
/// are that pass's). The English here is each key's default too, so a
/// missing entry still reads right. "What's on your mind?" and "Sort it"
/// are the owner's (23 Oct), exact.
enum IdeasWords {
    // MARK: capture
    static var placeholder: String { String(localized: "capture.placeholder", defaultValue: "What's on your mind?") }
    static var sortIt: String { String(localized: "capture.sort", defaultValue: "Sort it") }
    static var sortItHint: String {
        String(localized: "capture.sort.hint", defaultValue: "Saves your idea and lets you choose its folder.")
    }

    // MARK: the modes (S05b): the switch's words, drafts for ux-writing
    static var modeSort: String { String(localized: "mode.sort", defaultValue: "Sort") }
    static var modeMap: String { String(localized: "mode.map", defaultValue: "Map") }
    static var modeSortSpoken: String { String(localized: "mode.sort.spoken", defaultValue: "Sort ideas") }
    static var modeMapSpoken: String { String(localized: "mode.map.spoken", defaultValue: "Map of ideas") }

    // MARK: Map mode's look chip and its menu (S05b), drafts for ux-writing
    static var lookSpace: String { String(localized: "look.space", defaultValue: "Space") }
    static var lookNeuron: String { String(localized: "look.neuron", defaultValue: "Neuron") }
    static var look2D: String { String(localized: "look.2d", defaultValue: "2D") }
    static var lookPerformance: String { String(localized: "look.performance", defaultValue: "Performance") }
    /// The look chip, read aloud with the look: "Theme, Space".
    static func lookChipSpoken(_ look: String) -> String {
        String(localized: "look.chip.spoken %@", defaultValue: "Theme, \(look)")
    }
    static var lookChipHint: String { String(localized: "look.chip.hint", defaultValue: "Changes how the map looks.") }
    /// The map's "To sort n" chip's hint; its label is the title's spoken words.
    static var mapListHint: String { String(localized: "map.list.hint", defaultValue: "Opens the list.") }

    // MARK: the empty map (S05b), drafts for ux-writing
    static var mapEmptyTitle: String { String(localized: "map.empty.title", defaultValue: "No ideas yet") }
    static var mapEmptyLine: String {
        String(localized: "map.empty.line", defaultValue: "Add ideas in Sort, and they appear here.")
    }

    // MARK: the Sort screen
    static var toSort: String { String(localized: "sort.title", defaultValue: "To sort") }
    /// "1 idea", "3 ideas": the count the spoken titles and rows end with.
    static func ideaCount(_ n: Int) -> String {
        String(localized: "ideas.count %lld", defaultValue: "\(n) ideas")
    }
    static func toSortSpoken(_ n: Int) -> String {
        let count = ideaCount(n)
        return String(localized: "sort.title.spoken %@", defaultValue: "To sort, \(count)")
    }
    static func folderSpoken(_ name: String, _ n: Int) -> String {
        let count = ideaCount(n)
        return String(localized: "folder.title.spoken %@ %@", defaultValue: "\(name), \(count)")
    }
    static var more: String { String(localized: "sort.more", defaultValue: "More") }
    static var allSorted: String { String(localized: "sort.allSorted", defaultValue: "All sorted.") }
    static var folders: String { String(localized: "sort.folders", defaultValue: "Folders") }
    static var chooseFolder: String { String(localized: "row.chooseFolder", defaultValue: "Choose folder") }
    static func chooseFolderFor(_ idea: String) -> String {
        String(localized: "row.chooseFolder.spoken %@", defaultValue: "Choose folder for \(idea)")
    }
    static var rowHint: String { String(localized: "row.hint", defaultValue: "Opens the idea.") }
    static var delete: String { String(localized: "row.swipe.trail", defaultValue: "Delete") }
    static var untitled: String { String(localized: "idea.untitled", defaultValue: "Untitled") }
    static func folderRowSpoken(_ name: String, _ n: Int) -> String {
        let count = ideaCount(n)
        return String(localized: "folderRow.spoken %@ %@", defaultValue: "\(name), \(count)")
    }
    static var renameFolder: String { String(localized: "folderRow.menu.rename", defaultValue: "Rename folder") }
    static var newFolderInside: String {
        String(localized: "folderRow.menu.newInside", defaultValue: "New folder inside")
    }
    static var deleteFolder: String { String(localized: "folderRow.menu.delete", defaultValue: "Delete folder") }
    static func backTo(_ parent: String) -> String {
        String(localized: "upRow.spoken %@", defaultValue: "Back to \(parent)")
    }
    static var importFromStethoscore: String {
        String(localized: "more.import", defaultValue: "Import from Stethoscore\u{2026}")
    }
    static var newFolder: String { String(localized: "more.newFolder", defaultValue: "New folder") }

    // MARK: the empty state and folders
    static var emptyLine1: String {
        String(localized: "empty.line1", defaultValue: "Type an idea and tap Sort it to choose its folder.")
    }
    static var emptyLine2: String {
        String(localized: "empty.line2", defaultValue: "Ideas without a folder stay here to sort later.")
    }
    static var folderEmpty: String {
        String(localized: "folder.empty", defaultValue: "No ideas yet. Type one above to add it to this folder.")
    }

    // MARK: the folder chooser and the naming sheet
    static var chooserTitle: String { String(localized: "chooser.title", defaultValue: "Choose folder") }
    static var cancel: String { String(localized: "chooser.cancel", defaultValue: "Cancel") }
    static var toSortSpokenRow: String { String(localized: "chooser.toSort.spoken", defaultValue: "To sort, no folder") }
    static func inside(_ parent: String) -> String {
        String(localized: "chooser.folder.spoken %@", defaultValue: "inside \(parent)")
    }
    static var newFolderHint: String {
        String(localized: "chooser.newFolder.hint", defaultValue: "Makes a folder and moves your idea into it.")
    }
    static var folderName: String { String(localized: "name.field", defaultValue: "Folder name") }
    static var create: String { String(localized: "name.create", defaultValue: "Create") }
    static var rename: String { String(localized: "name.rename", defaultValue: "Rename") }

    // MARK: deleting a folder
    static func deleteFolderTitle(_ name: String) -> String {
        String(localized: "folderDelete.title %@", defaultValue: "Delete \u{201C}\(name)\u{201D}?")
    }
    static func deleteFolderBody(_ parent: String) -> String {
        String(localized: "folderDelete.body %@",
               defaultValue: "Its ideas and folders move to \(parent), so nothing in it is lost.")
    }

    // MARK: notices
    static func movedTo(_ folder: String) -> String {
        String(localized: "undo.moved %@", defaultValue: "Moved to \(folder).")
    }
    static var backInToSort: String { String(localized: "undo.toSort", defaultValue: "Back in To sort.") }
    static var ideaDeleted: String { String(localized: "undo.deleted", defaultValue: "Idea deleted.") }
    static var undo: String { String(localized: "undo.action", defaultValue: "Undo") }
    static var storeUnreadable: String {
        String(localized: "store.unreadable",
               defaultValue: "Your saved ideas didn't open. A copy is kept safe on this device, and new ideas still save.")
    }
    static var storePartlyUnreadable: String {
        String(localized: "store.partlyUnreadable",
               defaultValue: "Some saved ideas didn't open. A copy of them is kept safe on this device.")
    }
    static var close: String { String(localized: "store.notice.close", defaultValue: "Close") }

    // MARK: the editor
    static var title: String { String(localized: "editor.title", defaultValue: "Title") }
    static var details: String { String(localized: "editor.body", defaultValue: "Details") }
    static var editorHint: String {
        String(localized: "editor.hint",
               defaultValue: "To link an idea, type [[ and its title, like [[Holiday plans]]. For a heading type #, for a list -, and **bold** or *italic*; tap Read to see them.")
    }
    static var write: String { String(localized: "editor.write", defaultValue: "Write") }
    static var read: String { String(localized: "editor.read", defaultValue: "Read") }
    static var linkTo: String { String(localized: "editor.linkTo", defaultValue: "Link to an idea\u{2026}") }
    static var linkedIdeas: String { String(localized: "editor.linked", defaultValue: "Linked ideas") }
    static var linkedHere: String { String(localized: "editor.linkedHere", defaultValue: "Ideas that link here") }
    static var howLinked: String { String(localized: "editor.how.hand", defaultValue: "Linked") }
    static var howMentioned: String { String(localized: "editor.how.text.out", defaultValue: "Mentioned in the text") }
    static var howMentions: String { String(localized: "editor.how.text.in", defaultValue: "Mentions this idea") }
    static var missing: String {
        String(localized: "editor.missing", defaultValue: "No idea with this title yet. Tap to make it.")
    }
    static var missingIdea: String { String(localized: "editor.missingIdea", defaultValue: "Missing idea") }
    static var unlink: String { String(localized: "editor.unlink", defaultValue: "Unlink") }
    static func chipSpoken(_ folder: String) -> String {
        String(localized: "editor.chip.spoken %@", defaultValue: "Folder, \(folder)")
    }
    static var chipHint: String { String(localized: "editor.chip.hint", defaultValue: "Opens the folder chooser.") }
    static var deleteIdea: String { String(localized: "editor.delete", defaultValue: "Delete idea") }
    static var deleteIdeaTitle: String { String(localized: "editor.delete.title", defaultValue: "Delete this idea?") }
    static var deleteIdeaBody: String {
        String(localized: "editor.delete.body", defaultValue: "Other ideas lose their links to it too.")
    }
    static var done: String { String(localized: "editor.done", defaultValue: "Done") }
    static var pickerTitle: String { String(localized: "picker.title", defaultValue: "Link to an idea") }
    static var pickerSearch: String { String(localized: "picker.search", defaultValue: "Find an idea") }
    static func pickerNone(_ query: String) -> String {
        String(localized: "picker.none %@", defaultValue: "No ideas match \u{201C}\(query)\u{201D}.")
    }

    // MARK: the import
    static var importedTitle: String { String(localized: "import.done.title", defaultValue: "Ideas imported") }
    static func imported(ideas: Int, folders: Int, links: Int) -> String {
        let i = String(localized: "import.ideas %lld", defaultValue: "\(ideas) ideas")
        let f = String(localized: "import.folders %lld", defaultValue: "\(folders) folders")
        let l = String(localized: "import.links %lld", defaultValue: "\(links) links")
        return String(localized: "import.done.body %@ %@ %@", defaultValue: "Added \(i), \(f) and \(l).")
    }
    static func placed(_ n: Int) -> String {
        String(localized: "import.placed %lld", defaultValue: "\(n) ideas keep their place on the 2D map.")
    }
    static var nothingNewTitle: String { String(localized: "import.nothing.title", defaultValue: "Nothing new to add") }
    static var nothingNewBody: String {
        String(localized: "import.nothing.body", defaultValue: "You already have every idea in this file.")
    }
    static var importDone: String { String(localized: "import.ok", defaultValue: "Done") }
    static var refusedTitle: String { String(localized: "import.refused.title", defaultValue: "Can't import this file") }
    static var refusedNotBackup: String {
        String(localized: "import.refused.notBackup",
               defaultValue: "This file isn't a Stethoscore backup. Choose another file.")
    }
    static var refusedNewer: String {
        String(localized: "import.refused.newer",
               defaultValue: "This backup is from a newer version of Stethoscore. Check for a Sorta update, then try again.")
    }
    static var refusedUnreadable: String {
        String(localized: "import.refused.unreadable.ios",
               defaultValue: "This backup didn't open. It may be damaged, so try another copy.")
    }

    // MARK: ideas like this, collide, split and join (S06c), as Android's strings.xml
    static var echoesTitle: String { String(localized: "echoes.title", defaultValue: "Ideas like this") }
    static var echoesLinkHint: String { String(localized: "echoes.link.hint", defaultValue: "Links it to this idea.") }
    static func echoesLinked(_ title: String) -> String {
        String(localized: "echoes.linked %@", defaultValue: "Linked to \u{201C}\(title)\u{201D}.")
    }
    /// VoiceOver, on a board card like the open idea: whom a tap links it to.
    static func echoesBoardLink(_ title: String) -> String {
        String(localized: "echoes.board.link %@", defaultValue: "Links it to \u{201C}\(title)\u{201D}.")
    }
    static var collideTool: String { String(localized: "collide.tool", defaultValue: "Collide two ideas") }
    static var collideTitle: String { String(localized: "collide.title", defaultValue: "What if both?") }
    static var collideField: String { String(localized: "collide.field", defaultValue: "The idea they make") }
    static var collideSave: String { String(localized: "collide.save", defaultValue: "Save idea") }
    static var collideAnother: String { String(localized: "collide.another", defaultValue: "Try another pair") }
    static var collideSaved: String { String(localized: "collide.saved", defaultValue: "New idea in To sort.") }
    static var collideNone: String {
        String(localized: "collide.none",
               defaultValue: "No two ideas are far apart yet. Put ideas in different folders, then try again.")
    }
    static var shapeSplit: String { String(localized: "shape.split", defaultValue: "Split\u{2026}") }
    static var shapeJoin: String { String(localized: "shape.join", defaultValue: "Join with\u{2026}") }
    static var shapeCollide: String { String(localized: "shape.collide", defaultValue: "Collide with\u{2026}") }
    static var shapeJoinTitle: String { String(localized: "shape.join.title", defaultValue: "Join with") }
    static var shapeCollideTitle: String { String(localized: "shape.collide.title", defaultValue: "Collide with") }
    static var splitTitle: String { String(localized: "split.title", defaultValue: "Split in two") }
    static var splitHint: String {
        String(localized: "split.hint", defaultValue: "Tap the last sentence of the first idea.")
    }
    static var splitFirst: String { String(localized: "split.first", defaultValue: "First idea") }
    static var splitEndsHere: String { String(localized: "split.ends.here", defaultValue: "First idea ends here") }
    static var splitSecond: String { String(localized: "split.second", defaultValue: "Second idea") }
    static var splitConfirm: String { String(localized: "split.confirm", defaultValue: "Split idea") }
    static var splitDone: String { String(localized: "split.done", defaultValue: "Idea split in two.") }
    static var splitOne: String {
        String(localized: "split.one", defaultValue: "This idea has one sentence, so it can\u{2019}t be split.")
    }
    static var joinDone: String { String(localized: "join.done", defaultValue: "Ideas joined.") }
}
