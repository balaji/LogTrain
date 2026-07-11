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
                TextField("Weight (\(unitLabel))", text: $weightText)
                    .keyboardType(.decimalPad)
                    .focused($focusedField, equals: .weight)
                TextField("Reps", text: $repsText)
                    .keyboardType(.numberPad)
                    .focused($focusedField, equals: .reps)
                Button("Calculate") {
                    focusedField = nil
                    guard let weight, let reps else { return }
                    results = Dictionary(uniqueKeysWithValues: formulas.map {
                        ($0, $0.estimate(weight: weight, reps: reps))
                    })
                }
                .disabled(weight == nil || reps == nil)
            } footer: {
                if let reps, reps > 15 {
                    Text("Above 15 reps these estimates get much less accurate — managing that many reps is a good sign you can try a heavier weight.")
                        .foregroundStyle(.orange)
                }
            }
            Section {
                ForEach(formulas, id: \.self) { formula in
                    formulaRow(formula)
                }
            } footer: {
                Text("Tap a formula to make it the default for all insights.")
            }
        }
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button("Done") {
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
                    Link("Find out more", destination: URL(
                        string: "https://google.com/search?q=\(formulaName(formula))%20one%20rep%20max%20formula"
                    )!)
                }
                .padding()
            }
            .navigationTitle(formulaName(formula))
            .navigationBarTitleDisplayMode(.inline)
        }
        .presentationDetents([.medium, .large])
    }

    private var unitLabel: String { settings.weightUnit == .imperial ? "lb" : "kg" }
}

extension OneRmFormula: @retroactive Identifiable {
    public var id: String { value }
}

/// Display names mirror Compose's `oneRmFormulaLabel`.
func formulaName(_ formula: OneRmFormula) -> String {
    switch formula {
    case .epley: return "Epley"
    case .brzycki: return "Brzycki"
    case .lombardi: return "Lombardi"
    case .oconner: return "O'Conner"
    default: return formula.value.capitalized
    }
}

/// Same ~200-word summaries as Compose's strings.xml `one_rm_info_*` keys.
private func formulaInfo(_ formula: OneRmFormula) -> String {
    switch formula {
    case .epley:
        return "The Epley formula, published by strength coach Boyd Epley in 1985, is the most widely used one-rep-max estimator in training apps, spreadsheets, and gym folklore. It is a simple linear model: every rep you perform beyond the first adds one thirtieth of the bar weight to the estimate. That linearity makes it easy to reason about — double the extra reps and you double the added credit — and it behaves predictably across the whole rep range. Epley and Brzycki, the two most popular formulas, agree exactly at 10 reps; below 10 Epley is slightly more conservative than Brzycki, and above 10 it becomes the more generous of the two. Like all estimators it is most trustworthy when the set is taken close to failure with moderate reps, roughly 2 to 10, where the relationship between reps and load is close to linear for most lifters. Because it neither collapses at high reps nor exaggerates low-rep sets, it is a sensible default when you have no strong reason to prefer another model, which is exactly why so much software ships with it. LogTrain uses Epley as its default formula."
    case .brzycki:
        return "Matt Brzycki, a strength coach and author at Princeton University, published this formula in 1993 and it has since become the estimator most often cited in academic validation studies. Unlike Epley's straight line, Brzycki is hyperbolic: the estimate is the weight multiplied by 36 divided by (37 minus reps). Each additional rep therefore counts a little more than the one before it, and the curve accelerates as reps climb. In the low-rep range, up to about 10 reps, Brzycki tracks measured maxes closely and research generally rates it among the most accurate of the classic formulas there. Beyond 10 reps the denominator shrinks quickly and the estimate grows aggressively, so treat high-rep predictions with skepticism — a 20-rep set of a light weight will produce a flattering number that few lifters could actually lift for a single. Brzycki and Epley agree exactly at 10 reps, with Brzycki slightly higher below that point. Choose it if you usually test with heavy sets of 3 to 8 reps and want the estimator with the strongest research pedigree in that range. LogTrain clamps the rep count at 36 so the math stays finite."
    case .lombardi:
        return "The Lombardi formula takes a completely different mathematical shape from its linear and hyperbolic siblings: it multiplies the weight by the rep count raised to the power of 0.1. A power law with such a small exponent produces the gentlest curve of any common estimator — the estimate rises quickly over the first few reps and then flattens dramatically, so a 10-rep set is credited only about 26 percent above the bar weight and a 20-rep set barely more. That restraint is the whole point. Where Epley and especially Brzycki inflate high-rep sets into eye-catching maxes, Lombardi stays skeptical, which makes it the best fit for lifters who log endurance-style sets of 12, 15, or 20 reps and want a number they could plausibly hit on a heavy single. The trade-off is the mirror image: for genuinely heavy doubles and triples Lombardi gives the least credit of the four, so pure strength athletes testing at low reps may find it undersells them. Pick Lombardi if your training lives in the higher rep ranges and you would rather be pleasantly surprised at the bar than let down by it."
    case .oconner:
        return "Published by O'Conner and colleagues in 1989, this formula is the cautious sibling of Epley. It uses the same linear idea — every rep beyond the first adds a fixed fraction of the bar weight — but the fraction is 1/40 instead of Epley's 1/30, so each rep earns 25 percent less credit. The result is a deliberately conservative estimate across the entire rep range: at 5 reps O'Conner predicts 12.5 percent over the bar weight where Epley predicts nearly 17 percent. That restraint is valuable in situations where overestimating a max is riskier than underestimating it. Coaches programming percentages for beginners often prefer it, because a novice's estimated max sets their working weights for weeks, and an inflated number bakes in loads they cannot recover from. It also suits older lifters, rehab settings, and anyone returning from a layoff, where a margin of safety matters more than bragging rights. Because it is linear, it remains stable and predictable at higher reps rather than accelerating like Brzycki. Choose O'Conner when you want your training numbers anchored to what you can almost certainly lift, not what you might lift on a perfect day."
    default:
        return ""
    }
}
