import SwiftUI

/// Long-press drag-to-reorder machinery shared by the routine detail form and
/// the workout session screen. A long press on an exercise header hands
/// control to a full-screen overlay of condensed rows; the pressed row floats
/// under the finger and the drop slot is committed on release.
///
/// Owned as `@State` by the hosting screen. The screen shows
/// `ExerciseReorderOverlay` while `activeIndex` is non-nil, attaches
/// `.reorderHandle(...)` to each exercise header, and commits the move via
/// `activeIndex`/`targetIndex(count:)` when the gesture ends.
struct ExerciseReorderModel {
    var activeIndex: Int?
    fileprivate var fingerY: CGFloat = 0
    fileprivate var startFingerY: CGFloat = 0
    fileprivate var listTop: CGFloat = 0
    fileprivate var bottom: CGFloat = 0
    /// Keeps the overlay's row column anchored in place while the release
    /// animation runs after `activeIndex` is cleared.
    fileprivate var lastActiveIndex = 0

    static let rowHeight: CGFloat = 56

    mutating func begin(index: Int, fingerY y: CGFloat) {
        fingerY = y
        startFingerY = y
        lastActiveIndex = index
        activeIndex = index
    }

    mutating func update(fingerY y: CGFloat) {
        fingerY = y
    }

    /// Top of the overlay's row column, chosen so the pressed row appears
    /// under the finger where the press started, clamped to keep the whole
    /// column on screen.
    fileprivate func anchoredListTop(count: Int) -> CGFloat {
        let active = activeIndex ?? lastActiveIndex
        let maxTop = max(listTop, bottom - CGFloat(count) * Self.rowHeight)
        let ideal = startFingerY - (CGFloat(active) + 0.5) * Self.rowHeight
        return min(max(ideal, listTop), maxTop)
    }

    /// Slot the floating row would be dropped into right now.
    func targetIndex(count: Int) -> Int? {
        guard activeIndex != nil, count > 0 else { return nil }
        let slot = Int(((fingerY - anchoredListTop(count: count)) / Self.rowHeight).rounded(.down))
        return min(max(slot, 0), count - 1)
    }
}

struct ExerciseReorderOverlay: View {
    @Binding var model: ExerciseReorderModel
    let names: [String]

    var body: some View {
        let anchoredTop = model.anchoredListTop(count: names.count)
        let target = model.targetIndex(count: names.count)
        let columnShift = anchoredTop - model.listTop

        VStack(alignment: .leading, spacing: 0) {
            Text(L10n.reorderExercises)
                .font(.headline)
                .padding(.horizontal, 16)
                .padding(.vertical, 12)

            ZStack(alignment: .top) {
                VStack(spacing: 0) {
                    ForEach(Array(names.enumerated()), id: \.offset) { index, name in
                        row(name)
                            .opacity(index == model.activeIndex ? 0 : 1)
                            .offset(y: shift(for: index, target: target))
                            .animation(.default, value: target)
                    }
                }
                .offset(y: columnShift)

                if let active = model.activeIndex {
                    let maxY = CGFloat(names.count - 1) * ExerciseReorderModel.rowHeight
                    let floatY = min(max(model.fingerY - anchoredTop - ExerciseReorderModel.rowHeight / 2, 0), maxY)
                    row(names[active])
                        .background(Color(.secondarySystemBackground))
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                        .shadow(radius: 8)
                        .offset(y: floatY + columnShift)
                }
            }
            .onGeometryChange(for: CGFloat.self) { proxy in
                proxy.frame(in: .global).minY
            } action: { top in
                model.listTop = top
            }

            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(.systemBackground))
        .onGeometryChange(for: CGFloat.self) { proxy in
            proxy.frame(in: .global).maxY
        } action: { bottom in
            model.bottom = bottom
        }
    }

    private func shift(for index: Int, target: Int?) -> CGFloat {
        guard let active = model.activeIndex,
              let target,
              index != active else { return 0 }
        if active < index && index <= target { return -ExerciseReorderModel.rowHeight }
        if target <= index && index < active { return ExerciseReorderModel.rowHeight }
        return 0
    }

    private func row(_ name: String) -> some View {
        HStack {
            Text(name)
                .foregroundStyle(Color.accentColor)
            Spacer()
            Image(systemName: "line.3.horizontal")
                .foregroundStyle(.secondary)
        }
        .padding(.horizontal, 16)
        .frame(maxWidth: .infinity)
        .frame(height: ExerciseReorderModel.rowHeight)
    }
}

extension View {
    /// Long-press-then-drag reorder gesture for an exercise header row.
    /// Callbacks receive the finger's global Y position.
    func reorderHandle(
        onStart: @escaping (CGFloat) -> Void,
        onChanged: @escaping (CGFloat) -> Void,
        onEnded: @escaping () -> Void
    ) -> some View {
        modifier(ReorderHandleModifier(onStart: onStart, onChanged: onChanged, onEnded: onEnded))
    }
}

private struct ReorderHandleModifier: ViewModifier {
    let onStart: (CGFloat) -> Void
    let onChanged: (CGFloat) -> Void
    let onEnded: () -> Void

    @State private var midY: CGFloat = 0
    @State private var started = false

    func body(content: Content) -> some View {
        content
            .onGeometryChange(for: CGFloat.self) { proxy in
                proxy.frame(in: .global).midY
            } action: { y in
                midY = y
            }
            .gesture(reorderGesture)
    }

    private var reorderGesture: some Gesture {
        LongPressGesture(minimumDuration: 0.35)
            .sequenced(before: DragGesture(minimumDistance: 0, coordinateSpace: .global))
            .onChanged { value in
                switch value {
                case .second(true, let drag):
                    if !started {
                        started = true
                        onStart(drag?.location.y ?? midY)
                    }
                    if let drag {
                        onChanged(drag.location.y)
                    }
                default:
                    break
                }
            }
            .onEnded { _ in
                if started {
                    started = false
                    onEnded()
                }
            }
    }
}
