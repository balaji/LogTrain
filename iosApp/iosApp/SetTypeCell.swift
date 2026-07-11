import SwiftUI
import SharedUI

/// The leading cell of a set row: a working-set number, or a marker for the
/// warm-up and drop sets that don't take one. Tapping it opens the set-type
/// menu — the only way to change a set's type, in both the routine editor and
/// a live session. Mirrors the Compose `SetTypeCell`.
struct SetTypeCell: View {
    let setType: SetType
    let workingSetNumber: Int
    let width: CGFloat
    let onSetTypeChange: (SetType) -> Void

    /// Listed explicitly rather than read off the Kotlin enum, which doesn't
    /// bridge its entries to Swift; a new SetType has to be added here too.
    private static let options: [SetType] = [.normal, .warmup, .drop]

    var body: some View {
        Menu {
            ForEach(Self.options, id: \.name) { option in
                Button {
                    onSetTypeChange(option)
                } label: {
                    if option == setType {
                        Label(L10n.setTypeLabel(option), systemImage: "checkmark")
                    } else {
                        Text(L10n.setTypeLabel(option))
                    }
                }
            }
        } label: {
            Text(L10n.setTypeMarker(setType) ?? "\(workingSetNumber)")
                .foregroundStyle(Self.tint(setType))
                .frame(width: width)
        }
        .buttonStyle(.borderless)
        .accessibilityLabel(L10n.setType)
    }

    private static func tint(_ setType: SetType) -> Color {
        switch setType {
        case .warmup: return .orange
        case .drop: return .purple
        default: return .secondary
        }
    }
}
