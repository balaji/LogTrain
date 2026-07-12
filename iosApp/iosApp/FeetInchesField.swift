import SwiftUI

/// Height entry in feet/inches for imperial mode: a labeled row showing the
/// current value whose tap toggles a pair of ft/in wheel pickers beneath it.
/// The wheels start hidden; owners collapse them (via `expanded`) when another
/// field takes focus. Used by the profile form and the BMI calculator; the
/// Compose twin is `FeetInchesField` in WheelPicker.kt.
struct FeetInchesField: View {
    static let feetRange = 3...8
    static let inchRange = 0...11

    let label: String
    @Binding var feet: Int
    @Binding var inches: Int
    @Binding var expanded: Bool

    var body: some View {
        Group {
            LabeledContent(label) {
                Text(L10n.heightFtInValue("\(feet)", "\(inches)"))
            }
            .contentShape(Rectangle())
            .onTapGesture {
                withAnimation { expanded.toggle() }
            }
            if expanded {
                HStack(spacing: 0) {
                    Picker(L10n.unitFt, selection: $feet) {
                        ForEach(Self.feetRange, id: \.self) { Text("\($0) \(L10n.unitFt)").tag($0) }
                    }
                    .pickerStyle(.wheel)
                    Picker(L10n.unitIn, selection: $inches) {
                        ForEach(Self.inchRange, id: \.self) { Text("\($0) \(L10n.unitIn)").tag($0) }
                    }
                    .pickerStyle(.wheel)
                }
                .frame(height: 120)
            }
        }
    }
}
