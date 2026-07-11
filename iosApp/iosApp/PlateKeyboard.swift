import SwiftUI
import UIKit
import SharedUI

/// SwiftUI panel rendered inside the plate keyboard's input view: bar picker,
/// running total, removable per-side plate chips, and plate buttons. All
/// behavior lives in the KMP-shared `BarbellKeyboardState`; this mirrors the
/// Compose `BarbellPlateKeypad`.
struct PlateKeypadView: View {
    let keyboard: BarbellKeyboardState

    @State private var state = BarbellKeyboardUiState(
        visible: false,
        numericMode: false,
        unit: .metric,
        barWeight: 20,
        platesPerSide: [],
        plateOptions: [],
        barOptions: [],
        exerciseIndex: -1,
        setIndex: -1
    )

    private var unitSuffix: String { state.unit == .imperial ? L10n.unitLb : L10n.unitKg }

    /// Blue ramp, lightest to strongest, one step per plate denomination
    /// (there are always exactly 7). Assigned by rank within the full plate
    /// set, not by absolute value, so a denomination's color is stable
    /// across kg and lb. Mirrors Compose's `PlateChipPalette`.
    private static let chipPalette: [Color] = [
        Color(rgb: 0xE3F2FD),
        Color(rgb: 0xBBDEFB),
        Color(rgb: 0x90CAF9),
        Color(rgb: 0x64B5F6),
        Color(rgb: 0x42A5F5),
        Color(rgb: 0x1E88E5),
        Color(rgb: 0x0D47A1),
    ]

    private func chipColor(for value: Double) -> Color {
        let index = state.plateOptions.firstIndex { $0.value == value } ?? 0
        return Self.chipPalette[min(max(index, 0), Self.chipPalette.count - 1)]
    }

    private func contentColor(for background: Color) -> Color {
        background.isLight ? .black : .white
    }

    private enum GridCell {
        case plate(PlateOption)
        case backspace
        case toggleNumeric
    }

    // The toggle sits just before the last plate value so the final row
    // reads [123, <largest plate>, backspace] — toggle on the left,
    // backspace on the right, matching the system decimal pad.
    private var gridRows: [[GridCell]] {
        let cells: [GridCell]
        if state.plateOptions.isEmpty {
            cells = [.toggleNumeric, .backspace]
        } else {
            cells = state.plateOptions.dropLast().map { GridCell.plate($0) }
                + [.toggleNumeric, .plate(state.plateOptions.last!), .backspace]
        }
        return stride(from: 0, to: cells.count, by: 3).map {
            Array(cells[$0..<min($0 + 3, cells.count)])
        }
    }

    @ViewBuilder
    private func gridButton(for cell: GridCell) -> some View {
        switch cell {
        case .plate(let plate):
            Button {
                keyboard.addPlate(value: plate.value)
            } label: {
                Text(plate.label)
                    .font(.body.weight(.medium))
                    .minimumScaleFactor(0.6)
                    .lineLimit(1)
                    .frame(maxWidth: .infinity, minHeight: 48)
                    .background(Color(.tertiarySystemBackground))
                    .clipShape(RoundedRectangle(cornerRadius: 8))
            }
            .buttonStyle(.plain)
        case .backspace:
            // Unfilled, like the system decimal pad's own backspace key —
            // visually a secondary action, not a value to tap.
            Button {
                keyboard.removeLastPlate()
            } label: {
                Image(systemName: "delete.left")
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, minHeight: 48)
            }
            .buttonStyle(.plain)
            .disabled(state.platesPerSide.isEmpty)
        case .toggleNumeric:
            Button {
                keyboard.setNumericMode(numeric: true)
            } label: {
                Text(L10n.plateUseNumeric)
                    .font(.body.weight(.medium))
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, minHeight: 48)
            }
            .buttonStyle(.plain)
        }
    }

    var body: some View {
        VStack(spacing: 10) {
            HStack {
                Menu {
                    ForEach(state.barOptions, id: \.value) { bar in
                        Button("\(bar.label) \(unitSuffix)") {
                            keyboard.selectBarWeight(value: bar.value)
                        }
                    }
                } label: {
                    HStack(spacing: 4) {
                        Text("\(L10n.plateBar) \(PlateMath.shared.formatWeight(value: state.barWeight)) \(unitSuffix)")
                        Image(systemName: "chevron.up.chevron.down")
                            .font(.caption2)
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(Color(.tertiarySystemBackground))
                    .clipShape(Capsule())
                }
                Spacer()
                Text("\(state.totalLabel) \(unitSuffix)")
                    .font(.headline)
                    .foregroundStyle(Color.accentColor)
            }

            // Grouped by denomination, largest first; each chip's count is
            // both sides combined (one tap = one plate per side).
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 4) {
                    if state.plateBreakdown.isEmpty {
                        Text(L10n.platePerSide)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(Array(state.plateBreakdown.enumerated()), id: \.offset) { _, entry in
                            // No remove icon: the whole chip is already the
                            // tap target, and the "x" read as another
                            // multiplication sign next to the count.
                            let bg = chipColor(for: entry.option.value)
                            Button {
                                keyboard.removePlate(value: entry.option.value)
                            } label: {
                                Text("\(entry.count) × \(entry.option.label)")
                                    .foregroundStyle(contentColor(for: bg))
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 5)
                                    .background(bg)
                                    .clipShape(Capsule())
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
            .frame(height: 34)

            // Plate denominations plus backspace and the numeric-mode
            // toggle, three per row; the two utility keys fill out
            // whatever's left of the final row.
            VStack(spacing: 6) {
                ForEach(Array(gridRows.enumerated()), id: \.offset) { _, row in
                    HStack(spacing: 6) {
                        ForEach(Array(row.enumerated()), id: \.offset) { _, cell in
                            gridButton(for: cell)
                        }
                    }
                }
            }
        }
        .padding(.horizontal, 12)
        // Extra top clearance: the accessory toolbar's floating Done button
        // renders past its own bounds and would otherwise sit over the total
        // label. Extra bottom clearance matches the system decimal pad's own
        // bottom margin (this custom input view doesn't get the safe-area
        // inset the system keyboard does).
        .padding(.top, 22)
        .padding(.bottom, 22)
        .task {
            for await value in keyboard.uiState {
                state = value
            }
        }
    }
}

/// Weight field for barbell exercises: a `UITextField` whose `inputView` is
/// the plate keypad above, with an accessory toolbar that toggles to the real
/// system decimal pad ("123") and back ("Plates"), plus Done. UIKit because
/// SwiftUI's `TextField` cannot swap a custom input view.
struct PlateWeightField: UIViewRepresentable {
    let placeholder: String
    let text: String
    let numericMode: Bool
    let keyboard: BarbellKeyboardState
    /// Called when the field becomes first responder; opens the shared state
    /// for this cell.
    let onOpen: () -> Void
    let onTextChange: (String) -> Void

    func makeCoordinator() -> Coordinator { Coordinator(parent: self) }

    func makeUIView(context: Context) -> UITextField {
        let field = UITextField()
        field.textAlignment = .center
        field.font = .preferredFont(forTextStyle: .body)
        field.keyboardType = .decimalPad
        field.delegate = context.coordinator
        field.addTarget(
            context.coordinator,
            action: #selector(Coordinator.editingChanged(_:)),
            for: .editingChanged
        )
        field.inputAccessoryView = context.coordinator.makeAccessory()
        field.setContentHuggingPriority(.defaultLow, for: .horizontal)
        field.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        context.coordinator.field = field
        return field
    }

    func updateUIView(_ field: UITextField, context: Context) {
        let coordinator = context.coordinator
        coordinator.parent = self
        field.placeholder = placeholder
        if field.text != text {
            field.text = text
        }
        coordinator.applyMode(numericMode, to: field)
    }

    final class Coordinator: NSObject, UITextFieldDelegate {
        var parent: PlateWeightField
        weak var field: UITextField?

        // The hosting controller must be retained for the inputView's
        // lifetime; UIInputView only holds its view.
        private var hosting: UIHostingController<PlateKeypadView>?
        private var plateInputView: UIInputView?
        private var appliedNumericMode: Bool?
        private var toolbar: UIToolbar?
        private var toggleButton: UIBarButtonItem?
        private var doneButton: UIBarButtonItem?

        private static let keypadHeight: CGFloat = 290

        init(parent: PlateWeightField) {
            self.parent = parent
        }

        func makeAccessory() -> UIToolbar {
            let toolbar = UIToolbar(frame: CGRect(x: 0, y: 0, width: 320, height: 44))
            let toggle = UIBarButtonItem(
                title: L10n.plateUsePlates,
                style: .plain,
                target: self,
                action: #selector(toggleMode)
            )
            let done = UIBarButtonItem(
                barButtonSystemItem: .done,
                target: self,
                action: #selector(donePressed)
            )
            toolbar.items = [.flexibleSpace(), done]
            toolbar.sizeToFit()
            self.toolbar = toolbar
            toggleButton = toggle
            doneButton = done
            return toolbar
        }

        /// Swaps between the plate panel and the system decimal pad. UIKit
        /// input views don't self-size a hosted SwiftUI view, hence the fixed
        /// frame height. The toolbar's "Plates" button only appears in
        /// numeric mode — switching the other way happens from the plate
        /// grid's own "123" key.
        func applyMode(_ numericMode: Bool, to field: UITextField) {
            guard appliedNumericMode != numericMode else { return }
            appliedNumericMode = numericMode
            if let toggleButton, let doneButton {
                toolbar?.items = numericMode ? [toggleButton, .flexibleSpace(), doneButton] : [.flexibleSpace(), doneButton]
            }
            if numericMode {
                field.inputView = nil
            } else {
                if plateInputView == nil {
                    plateInputView = makePlateInputView()
                }
                field.inputView = plateInputView
            }
            if field.isFirstResponder {
                field.reloadInputViews()
            }
        }

        private func makePlateInputView() -> UIInputView {
            let host = UIHostingController(rootView: PlateKeypadView(keyboard: parent.keyboard))
            hosting = host
            let width = field?.window?.bounds.width ?? UIScreen.main.bounds.width
            let input = UIInputView(
                frame: CGRect(x: 0, y: 0, width: width, height: Self.keypadHeight),
                inputViewStyle: .keyboard
            )
            host.view.backgroundColor = .clear
            host.view.translatesAutoresizingMaskIntoConstraints = false
            input.addSubview(host.view)
            NSLayoutConstraint.activate([
                host.view.leadingAnchor.constraint(equalTo: input.leadingAnchor),
                host.view.trailingAnchor.constraint(equalTo: input.trailingAnchor),
                host.view.topAnchor.constraint(equalTo: input.topAnchor),
                host.view.bottomAnchor.constraint(equalTo: input.bottomAnchor),
            ])
            return input
        }

        @objc private func toggleMode() {
            // Only shown while in numeric mode; going the other way happens
            // from the plate grid's "123" key instead.
            parent.keyboard.setNumericMode(numeric: false)
        }

        @objc private func donePressed() {
            field?.resignFirstResponder()
        }

        @objc func editingChanged(_ field: UITextField) {
            let value = field.text ?? ""
            parent.onTextChange(value)
            // Keeps the shared state's text mirror current so toggling back
            // to plate mode re-decomposes what was actually typed.
            parent.keyboard.syncFieldText(value: value)
        }

        func textFieldDidBeginEditing(_ textField: UITextField) {
            parent.onOpen()
        }

        func textFieldDidEndEditing(_ textField: UITextField) {
            parent.keyboard.close()
        }

        func textField(
            _ textField: UITextField,
            shouldChangeCharactersIn range: NSRange,
            replacementString string: String
        ) -> Bool {
            // Plate mode: the shared state drives the text, so manual edits
            // (e.g. from a hardware keyboard) are blocked.
            appliedNumericMode == true
        }
    }
}

private extension Color {
    init(rgb: UInt32) {
        self.init(
            red: Double((rgb >> 16) & 0xFF) / 255,
            green: Double((rgb >> 8) & 0xFF) / 255,
            blue: Double(rgb & 0xFF) / 255
        )
    }

    /// Relative luminance against a black/white threshold, used to pick a
    /// readable text color for an arbitrary fixed background.
    var isLight: Bool {
        guard let components = UIColor(self).cgColor.components, components.count >= 3 else { return true }
        let luminance = 0.299 * components[0] + 0.587 * components[1] + 0.114 * components[2]
        return luminance > 0.5
    }
}
