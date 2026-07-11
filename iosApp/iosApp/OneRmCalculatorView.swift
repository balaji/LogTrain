import SwiftUI
import SharedUI

/// Native mirror of Compose's `OneRmCalculatorSheet`: enter a weight and rep
/// count to compare every 1RM formula's estimate; tapping a row saves that
/// formula as the app-wide default used by all insights. The math and number
/// formatting come from shared Kotlin so the platforms can never disagree.
struct OneRmCalculatorView: View {
    private enum Field {
        case weight, reps
    }

    let settings: UserSettings
    @State private var weightText = ""
    @State private var repsText = ""
    @State private var results: [OneRmFormula: Double]? = nil
    @State private var infoFormula: OneRmFormula? = nil
    @FocusState private var focusedField: Field?

    private let formulas: [OneRmFormula] = [.epley, .brzycki, .lombardi, .oconner]
    private var weight: Double? { Double(weightText).flatMap { $0 > 0 ? $0 : nil } }
    private var reps: Int32? { Int32(repsText).flatMap { $0 > 0 ? $0 : nil } }

    var body: some View {
        Form {
            Section {
                TextField("\(L10n.weight) (\(unitLabel))", text: $weightText)
                    .keyboardType(.decimalPad)
                    .focused($focusedField, equals: .weight)
                TextField(L10n.reps, text: $repsText)
                    .keyboardType(.numberPad)
                    .focused($focusedField, equals: .reps)
                Button(L10n.calculate) {
                    focusedField = nil
                    guard let weight, let reps else { return }
                    results = Dictionary(uniqueKeysWithValues: formulas.map {
                        ($0, $0.estimate(weight: weight, reps: reps))
                    })
                }
                .disabled(weight == nil || reps == nil)
            } footer: {
                if let reps, reps > 15 {
                    Text(L10n.oneRmHighRepsHint)
                        .foregroundStyle(.orange)
                }
            }
            Section {
                ForEach(formulas, id: \.self) { formula in
                    formulaRow(formula)
                }
            } footer: {
                Text(L10n.oneRmSelectedFormulaHint)
            }
        }
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button(L10n.done) {
                    focusedField = nil
                }
            }
        }
        .sheet(item: $infoFormula) { formula in
            formulaInfoSheet(formula)
        }
    }

    private func formulaRow(_ formula: OneRmFormula) -> some View {
        HStack {
            Image(systemName: "checkmark")
                .foregroundStyle(.tint)
                .opacity(formula == settings.oneRmFormula ? 1 : 0)
            Text(formulaName(formula))
            Spacer()
            Text(results?[formula].map { "\(InsightMath.shared.formatNumber(value: $0, maxDecimals: 1)) \(unitLabel)" } ?? "—")
                .foregroundStyle(.secondary)
            // Borderless so the info tap doesn't also trigger row selection.
            Button {
                infoFormula = formula
            } label: {
                Image(systemName: "info.circle")
            }
            .buttonStyle(.borderless)
        }
        .contentShape(Rectangle())
        .onTapGesture {
            Task { try? await UserSettingsRepository.shared.setOneRmFormula(formula: formula) }
        }
    }

    private func formulaInfoSheet(_ formula: OneRmFormula) -> some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text(formulaInfo(formula))
                    Link(L10n.findOutMore, destination: URL(
                        string: "https://google.com/search?q="
                            + L10n.oneRmSearchQuery(formulaName(formula))
                                .replacingOccurrences(of: " ", with: "%20")
                    )!)
                }
                .padding()
            }
            .navigationTitle(formulaName(formula))
            .navigationBarTitleDisplayMode(.inline)
        }
        .presentationDetents([.medium, .large])
    }

    private var unitLabel: String { settings.weightUnit == .imperial ? L10n.unitLb : L10n.unitKg }
}

extension OneRmFormula: @retroactive Identifiable {
    public var id: String { value }
}

/// Display names mirror Compose's `oneRmFormulaLabel`.
func formulaName(_ formula: OneRmFormula) -> String {
    switch formula {
    case .epley: return L10n.oneRmFormulaEpley
    case .brzycki: return L10n.oneRmFormulaBrzycki
    case .lombardi: return L10n.oneRmFormulaLombardi
    case .oconner: return L10n.oneRmFormulaOconner
    default: return formula.value.capitalized
    }
}

/// Same ~200-word summaries as Compose's strings.xml `one_rm_info_*` keys.
private func formulaInfo(_ formula: OneRmFormula) -> String {
    switch formula {
    case .epley: return L10n.oneRmInfoEpley
    case .brzycki: return L10n.oneRmInfoBrzycki
    case .lombardi: return L10n.oneRmInfoLombardi
    case .oconner: return L10n.oneRmInfoOconner
    default: return ""
    }
}
