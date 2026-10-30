import SwiftUI
import UIKit

// The Sort screen's shared pieces (S05 plan, task 1): the column, the
// capture well with Sort it, and the notice host. The well is Stethoscore's
// IdeaCaptureField (reference/stethoscore-aec2af1c/ios/RedPen/Features/
// Notes/IdeasBottomBar.swift:116-137: the field pressed into a capsule,
// `.submitLabel(.done)` and `.onSubmit`), moved to the top of the screen,
// with its lightbulb, its literal padding and height (14, 48), and the
// Send/Add swap (:103-114, 139-183) cut, and Sort it added.

/// The Sort screen's measures that the kit has no token for.
enum IdeasLayout {
    /// The readable column: at most this wide, centred, on an iPad, a
    /// tablet-sized window and an open fold (the kit's readable column, as
    /// Android's WardDeck has it, and WindowSpan's `broad` threshold).
    static let column: CGFloat = 700
    /// The editor's body, at its shortest.
    static let bodyMinHeight: CGFloat = 140
    /// How long a notice with Undo stays, unless VoiceOver is on.
    static let noticeSeconds: Double = 6
    /// A list row of words on the page, not a tile (the title, "All
    /// sorted." and the Folders label): its words start at the well's edge,
    /// a gutter in from the column (the list stands WardSpace.s in, the row
    /// another WardSpace.s), with the tiles' room above and below.
    static let lineInsets = EdgeInsets(top: WardRowTile.insets.top, leading: WardSpace.s,
                                       bottom: WardRowTile.insets.bottom, trailing: WardSpace.s)
}

/// The capture well: the field and Sort it in one capsule pressed into the
/// base. Return saves and keeps the field ready (`save`); Sort it saves and
/// opens the folder chooser (`sortIt`). Both are off while the field is
/// blank. At the accessibility text sizes it stacks: the field full width,
/// wrapping, and Sort it under it at full width.
struct CaptureWell: View {
    @Binding var draft: String
    let capturing: FocusState<Bool>.Binding
    let save: () -> Void
    let sortIt: () -> Void
    @Environment(\.dynamicTypeSize) private var typeSize

    private var blank: Bool { IdeasDesk.captured(draft) == nil }

    var body: some View {
        Group {
            if typeSize.isAccessibilitySize {
                stacked
            } else {
                inline
            }
        }
        // the well holds the field and Sort it; LaunchTests reads its frame
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("capture")
    }

    private var inline: some View {
        HStack(spacing: WardSpace.xs) {
            // the well's leading room is the field's too, inside the area
            // a tap focuses: everything before Sort it puts the caret in
            field(wraps: false)
                .padding(.leading, WardSpace.m)
                .frame(maxWidth: .infinity, minHeight: WardControlSize.button, alignment: .leading)
                .background { focusArea }
            sortButton(wide: false)
        }
        .padding(.trailing, WardSpace.xs)
        .frame(minHeight: WardControlSize.button)
        .wardInset(in: Capsule(), control: true)
    }

    private var stacked: some View {
        VStack(spacing: WardSpace.s) {
            field(wraps: true)
                .padding(.horizontal, WardSpace.m)
                .padding(.vertical, WardSpace.m)
                .frame(maxWidth: .infinity, minHeight: WardControlSize.button, alignment: .leading)
                .background { focusArea }
                .wardInset(in: RoundedRectangle(cornerRadius: WardRadius.field, style: .continuous), control: true)
            sortButton(wide: true)
        }
    }

    /// The whole well up to Sort it is the field's to tap: one tap anywhere
    /// there puts the caret in it. Behind the field, so a tap on the field
    /// itself is the field's.
    private var focusArea: some View {
        Color.clear
            .contentShape(Rectangle())
            .onTapGesture { capturing.wrappedValue = true }
            .accessibilityHidden(true)
    }

    @ViewBuilder
    private func field(wraps: Bool) -> some View {
        if wraps {
            // a field's own prompt keeps to one line and is cut short, so
            // at the largest sizes the prompt is words of the well's own,
            // behind the field, that wrap; the field's label still names it
            ZStack(alignment: .topLeading) {
                if draft.isEmpty {
                    Text(IdeasWords.placeholder)
                        .wardFont(.body)
                        .foregroundStyle(Color.wardDisabledInk)
                        .fixedSize(horizontal: false, vertical: true)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .allowsHitTesting(false)
                        .accessibilityHidden(true)
                }
                // a field that wraps takes Return as a new line, so a new
                // line is read as Return: the idea is saved, and Done still
                // saves at the largest sizes
                textField(TextField(text: $draft, prompt: Text(verbatim: ""), axis: .vertical) {
                    Text(IdeasWords.placeholder)
                })
                // the well keeps its whole height (IdeasView's strip), so a
                // long draft scrolls inside the field past two lines, the
                // prompt's own height, rather than pushing Sort it under the
                // keyboard: four lines would on a 667-point iPhone SE at AX5
                .lineLimit(1...2)
                .onChange(of: draft) { _, now in
                    guard now.contains("\n") else { return }
                    draft = now.replacingOccurrences(of: "\n", with: " ")
                    save()
                }
            }
        } else {
            textField(TextField(text: $draft,
                                prompt: Text(IdeasWords.placeholder).foregroundStyle(Color.wardDisabledInk)) {
                Text(IdeasWords.placeholder)
            })
        }
    }

    private func textField(_ field: TextField<Text>) -> some View {
        field
            .wardFont(.body)
            .foregroundStyle(Color.wardInk)
            .tint(Color.wardPrimaryInk)
            .focused(capturing)
            .submitLabel(.done)
            .onSubmit(save)
            .accessibilityIdentifier("idea-capture")
    }

    private func sortButton(wide: Bool) -> some View {
        Button(IdeasWords.sortIt, action: sortIt)
            .buttonStyle(SortItButtonStyle(wide: wide))
            .disabled(blank)
            .accessibilityHint(IdeasWords.sortItHint)
            .accessibilityIdentifier("sort-it")
    }
}

/// Sort it: the screen's one filled control, a coral capsule (`primary`)
/// with its words in `onPrimary` (4.5:1 in light and dark, WardPalette),
/// raised low off the well, on the kit's own face (WardPressFace). Off, it
/// looks as every disabled control does: the lowest step, its words in
/// Biro Grey, no fill. `wide`: the face takes the whole width it is given,
/// as under the field at the largest text sizes; the width is the face's,
/// so the capsule, its fill and the tap all span it.
struct SortItButtonStyle: ButtonStyle {
    var wide = false

    func makeBody(configuration: Configuration) -> some View {
        SortItFace(label: configuration.label, pressed: configuration.isPressed, wide: wide)
    }
}

private struct SortItFace: View {
    let label: ButtonStyleConfiguration.Label
    let pressed: Bool
    let wide: Bool
    @Environment(\.isEnabled) private var enabled
    @Environment(\.dynamicTypeSize) private var typeSize
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        let down: Bool = enabled && pressed
        label
            .wardFont(.buttonPrimary)
            .foregroundStyle(enabled ? Color.wardOnPrimary : Color.wardDisabledInk)
            // one line at the standard sizes; at the largest it may wrap,
            // since it then has the well's whole width
            .lineLimit(typeSize.isAccessibilitySize ? nil : 1)
            .fixedSize(horizontal: !typeSize.isAccessibilitySize, vertical: false)
            .multilineTextAlignment(.center)
            .padding(.horizontal, WardSpace.m)
            .padding(.vertical, WardSpace.s)
            .frame(minWidth: WardControlSize.buttonSmall, maxWidth: wide ? .infinity : nil,
                   minHeight: WardControlSize.buttonSmall)
            .background {
                WardPressFace(shape: Capsule(), lift: .low, pressed: down,
                              fill: enabled ? Color.wardPrimary : Color.wardSurface)
            }
            .contentShape(Capsule())
            .animation(reduceMotion ? nil : .wardShade(down: down), value: down)
            .contentShape(.hoverEffect, Capsule())
            .hoverEffect(.highlight)
            .capPop(down)
    }
}

/// The kit's compact button (`.wardCompact`) whose words wrap: the kit's
/// small capsule always takes its words' one-line width, so at the largest
/// text sizes a long label ran past the screen's edges. Same face, ink,
/// type and measures (WardButtonFace's compact case), in the width offered.
struct WrappingCompactButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        WrappingCompactFace(label: configuration.label, pressed: configuration.isPressed)
    }
}

private struct WrappingCompactFace: View {
    let label: ButtonStyleConfiguration.Label
    let pressed: Bool
    @Environment(\.isEnabled) private var enabled
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        let down: Bool = enabled && pressed
        let shape = RoundedRectangle(cornerRadius: WardControlSize.buttonSmall / 2, style: .continuous)
        label
            .wardFont(.buttonSmall)
            .multilineTextAlignment(.center)
            .foregroundStyle(enabled ? Color.wardPrimaryInk : Color.wardDisabledInk)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.horizontal, WardSpace.m)
            .padding(.vertical, WardSpace.s)
            .frame(minWidth: WardControlSize.buttonSmall, minHeight: WardControlSize.buttonSmall)
            .background {
                WardPressFace(shape: shape, lift: .low, pressed: down, fill: Color.wardSurface)
            }
            .contentShape(shape)
            .animation(reduceMotion ? nil : .wardShade(down: down), value: down)
            .contentShape(.hoverEffect, shape)
            .hoverEffect(.highlight)
            .capPop(down)
    }
}

/// A line of news at the foot of the Sort screen: Undo after a filing or a
/// delete, or the store's one notice at launch.
struct IdeasNotice: Identifiable {
    enum Undo {
        /// An idea was moved out of `from` (nil: To sort).
        case moved(idea: UUID, from: UUID?)
        /// An idea was deleted.
        case deleted(NoteStore.Removal)
    }

    let id = UUID()
    let text: String
    /// What Undo puts back; nil for the store's notice, which has Close.
    let undo: Undo?
}

/// The notice over the bottom of the Sort screen, above the home indicator,
/// in the kit's banner. A notice with Undo goes after six seconds, unless
/// VoiceOver is on (or the tests ask), when it stays until the next one;
/// either way it is announced. A new notice replaces the one on show.
struct IdeasNoticeHost: View {
    @Binding var notice: IdeasNotice?
    let undo: (IdeasNotice.Undo) -> Void

    private static let stays: Bool = {
        #if DEBUG
        return IdeasDesk.noticesStay(arguments: ProcessInfo.processInfo.arguments)
        #else
        return false
        #endif
    }()

    var body: some View {
        // a stack, not a Group, so the timer below has one view to hang on
        VStack(spacing: 0) {
            if let notice {
                WardBanner(text: notice.text,
                           actionTitle: notice.undo == nil ? IdeasWords.close : IdeasWords.undo,
                           action: {
                               if let step = notice.undo { undo(step) }
                               self.notice = nil
                           })
                .accessibilityIdentifier("notice")
                .padding(.horizontal, WardSpace.gutter)
                .padding(.vertical, WardSpace.s)
                .background(Color.wardBackground)
                .transition(.identity)
            }
        }
        .task(id: notice?.id) {
            guard let notice else { return }
            AccessibilityNotification.Announcement(notice.text).post()
            guard notice.undo != nil, !Self.stays, !UIAccessibility.isVoiceOverRunning else { return }
            try? await Task.sleep(for: .seconds(IdeasLayout.noticeSeconds))
            guard !Task.isCancelled, self.notice?.id == notice.id else { return }
            self.notice = nil
        }
    }
}
