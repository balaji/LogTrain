import SwiftUI
import SharedUI

/// Native mirror of Compose's `BmiCalculatorSheet`: weight and height prefill
/// from the saved profile in the user's unit system — kg/cm text fields in
/// metric, or lb text plus a tap-to-reveal feet/inches wheel in imperial.
/// Everything is converted to kg/cm for the calculation, and either input can
/// be overridden for a one-off estimate. Tapping a formula or classification
/// row saves it as the app-wide default and re-derives the shown result. The
/// math, thresholds, and formatting come from shared Kotlin.
struct BmiCalculatorView: View {
    private enum Field {
        case weight, height
    }

    /// Which option's explainer sheet is open, if any.
    private enum BmiInfoItem: Identifiable {
        case formula(BmiFormula)
        case classification(BmiClassification)

        var id: String {
            switch self {
            case .formula(let formula): return "formula_\(formula.value)"
            case .classification(let classification): return "classification_\(classification.value)"
            }
        }

        var title: String {
            switch self {
            case .formula(let formula): return bmiFormulaName(formula)
            case .classification(let classification): return bmiClassificationName(classification)
            }
        }

        var body: String {
            switch self {
            case .formula(let formula):
                switch formula {
                case .standard: return L10n.bmiInfoStandard
                case .trefethen: return L10n.bmiInfoTrefethen
                default: return ""
                }
            case .classification(let classification):
                switch classification {
                case .who: return L10n.bmiInfoWho
                case .asianPacific: return L10n.bmiInfoAsianPacific
                default: return ""
                }
            }
        }
    }

    private static let defaultHeightCm = 170.0

    let settings: UserSettings
    let profile: Profile?
    @State private var weightText = ""
    @State private var heightText = ""
    @State private var feet = 5
    @State private var inches = 10
    @State private var heightWheelsExpanded = false
    @State private var prefilled = false
    // Inputs captured at Calculate time; BMI and category derive from the
    // current settings so option taps update the shown result live.
    @State private var calculatedKg: Double? = nil
    @State private var calculatedCm: Double? = nil
    @State private var infoItem: BmiInfoItem? = nil
    @FocusState private var focusedField: Field?

    private let formulas: [BmiFormula] = [.standard, .trefethen]
    private let classifications: [BmiClassification] = [.who, .asianPacific]

    private var unit: WeightUnit { settings.weightUnit }
    private var weightUnitLabel: String { unit == .imperial ? L10n.unitLb : L10n.unitKg }
    private var weight: Double? { Double(weightText).flatMap { $0 > 0 ? $0 : nil } }
    private var heightCm: Double? {
        if unit == .imperial {
            return HeightConversion.shared.feetInchesToCm(feet: Int32(feet), inches: Int32(inches))
        }
        return Double(heightText).flatMap { $0 > 0 ? $0 : nil }
    }

    private var result: Double? {
        guard let calculatedKg, let calculatedCm else { return nil }
        return settings.bmiFormula.bmi(weightKg: calculatedKg, heightCm: calculatedCm)
    }

    var body: some View {
        Form {
            Section {
                LabeledContent("\(L10n.weight) (\(weightUnitLabel))") {
                    TextField("", text: $weightText)
                        .keyboardType(.decimalPad)
                        .multilineTextAlignment(.trailing)
                        .focused($focusedField, equals: .weight)
                }
                if unit == .imperial {
                    FeetInchesField(
                        label: L10n.height,
                        feet: $feet,
                        inches: $inches,
                        expanded: $heightWheelsExpanded
                    )
                } else {
                    LabeledContent("\(L10n.height) (\(L10n.unitCm))") {
                        TextField("", text: $heightText)
                            .keyboardType(.decimalPad)
                            .multilineTextAlignment(.trailing)
                            .focused($focusedField, equals: .height)
                    }
                }
                Button(L10n.calculate) {
                    focusedField = nil
                    withAnimation { heightWheelsExpanded = false }
                    guard let weight, let heightCm else { return }
                    calculatedKg = unit.toKilograms(value: weight)
                    calculatedCm = heightCm
                }
                .disabled(weight == nil || heightCm == nil)
            }
            if let result {
                Section {
                    Text(L10n.bmiResult(
                        InsightMath.shared.formatNumber(value: result, maxDecimals: 1),
                        bmiCategoryLabel(settings.bmiClassification.of(bmi: result))
                    ))
                    BmiSpectrumView(bmi: result, classification: settings.bmiClassification)
                        .padding(.vertical, 4)
                }
            }
            Section(L10n.bmiFormulaHeading) {
                ForEach(formulas, id: \.self) { formula in
                    optionRow(
                        label: bmiFormulaName(formula),
                        selected: formula == settings.bmiFormula,
                        value: calculatedKg.flatMap { kg in
                            calculatedCm.map { cm in
                                InsightMath.shared.formatNumber(
                                    value: formula.bmi(weightKg: kg, heightCm: cm),
                                    maxDecimals: 1
                                )
                            }
                        },
                        onSelect: {
                            Task { try? await UserSettingsRepository.shared.setBmiFormula(formula: formula) }
                        },
                        onInfo: { infoItem = .formula(formula) }
                    )
                }
            }
            Section {
                ForEach(classifications, id: \.self) { classification in
                    optionRow(
                        label: bmiClassificationName(classification),
                        selected: classification == settings.bmiClassification,
                        value: result.map { bmiCategoryLabel(classification.of(bmi: $0)) },
                        onSelect: {
                            Task {
                                try? await UserSettingsRepository.shared
                                    .setBmiClassification(classification: classification)
                            }
                        },
                        onInfo: { infoItem = .classification(classification) }
                    )
                }
            } header: {
                Text(L10n.bmiClassificationHeading)
            } footer: {
                Text(L10n.bmiSelectedFormulaHint)
            }
        }
        .onAppear(perform: prefill)
        // Typing in a text field puts the height wheels away.
        .onChange(of: focusedField) { _, newValue in
            if newValue != nil {
                withAnimation { heightWheelsExpanded = false }
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
        .sheet(item: $infoItem) { item in
            infoSheet(item)
        }
    }

    private func optionRow(
        label: String,
        selected: Bool,
        value: String?,
        onSelect: @escaping () -> Void,
        onInfo: @escaping () -> Void
    ) -> some View {
        HStack {
            Image(systemName: "checkmark")
                .foregroundStyle(.tint)
                .opacity(selected ? 1 : 0)
            Text(label)
            Spacer()
            Text(value ?? "—")
                .foregroundStyle(.secondary)
            // Borderless so the info tap doesn't also trigger row selection.
            Button(action: onInfo) {
                Image(systemName: "info.circle")
            }
            .buttonStyle(.borderless)
        }
        .contentShape(Rectangle())
        .onTapGesture(perform: onSelect)
    }

    private func infoSheet(_ item: BmiInfoItem) -> some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text(item.body)
                    Link(L10n.findOutMore, destination: URL(
                        string: "https://google.com/search?q="
                            + L10n.bmiSearchQuery(item.title)
                                .replacingOccurrences(of: " ", with: "%20")
                    )!)
                }
                .padding()
            }
            .navigationTitle(item.title)
            .navigationBarTitleDisplayMode(.inline)
        }
        .presentationDetents([.medium, .large])
    }

    /// Seed the inputs from the saved profile once, in the active unit system.
    private func prefill() {
        guard !prefilled else { return }
        prefilled = true
        if let weight = profile?.weight?.doubleValue {
            weightText = InsightMath.shared.formatNumber(value: unit.fromKilograms(kg: weight), maxDecimals: 1)
        }
        let cm = profile?.height?.doubleValue ?? Self.defaultHeightCm
        if unit == .imperial {
            let fi = HeightConversion.shared.cmToFeetInches(cm: cm)
            feet = min(max(Int(fi.feet), FeetInchesField.feetRange.lowerBound), FeetInchesField.feetRange.upperBound)
            inches = Int(fi.inches)
        } else if let height = profile?.height?.doubleValue {
            heightText = InsightMath.shared.formatNumber(value: height, maxDecimals: 1)
        }
    }
}

/// Horizontal color-banded BMI scale with a marker at `bmi`; mirrors Compose's
/// `BmiSpectrum`, including the 15–40 display range. Band thresholds come from
/// the selected classification in shared Kotlin.
private struct BmiSpectrumView: View {
    @Environment(\.colorScheme) private var scheme
    let bmi: Double
    let classification: BmiClassification

    // Mirrors BmiCategory.CHART_MIN / CHART_MAX in sharedLogic.
    private let chartMin = 15.0
    private let chartMax = 40.0

    private let categories: [BmiCategory] = [.underweight, .normal, .overweight, .obese]

    // (category, lower, upper) built from the classification's cutoffs; the
    // first band starts at chartMin, the last ends at chartMax.
    private var bands: [(category: BmiCategory, lower: Double, upper: Double)] {
        categories.enumerated().map { i, category in
            let lower = max(classification.lowerBound(category: category), chartMin)
            let upper = i == categories.count - 1
                ? chartMax
                : classification.lowerBound(category: categories[i + 1])
            return (category, lower, upper)
        }
    }

    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width
            let clamped = min(max(bmi, chartMin), chartMax)
            let markerX = w * (clamped - chartMin) / (chartMax - chartMin)
            let barTop = 16.0
            let barHeight = 14.0
            ZStack(alignment: .topLeading) {
                HStack(spacing: 0) {
                    ForEach(bands, id: \.category) { band in
                        Rectangle()
                            .fill(color(band.category))
                            .frame(width: w * (band.upper - band.lower) / (chartMax - chartMin))
                    }
                }
                .frame(width: w, height: barHeight)
                .clipShape(Capsule())
                .offset(y: barTop)

                Rectangle()
                    .fill(Color.primary)
                    .frame(width: 2, height: barHeight)
                    .offset(x: markerX - 1, y: barTop)

                Image(systemName: "arrowtriangle.down.fill")
                    .font(.system(size: 12))
                    .foregroundStyle(Color.primary)
                    .offset(x: markerX - 6, y: 0)
            }
        }
        .frame(height: 34)
    }

    // Health-band palette mirroring Compose's BmiColors{Light,Dark}.
    private func color(_ category: BmiCategory) -> Color {
        let dark = scheme == .dark
        switch category {
        case .underweight: return Color(rgb: dark ? 0x3987E5 : 0x2A78D6)
        case .normal: return Color(rgb: dark ? 0x199E70 : 0x1BAF7A)
        case .overweight: return Color(rgb: dark ? 0xC98500 : 0xEDA100)
        case .obese: return Color(rgb: dark ? 0xE66767 : 0xE34948)
        default: return .gray
        }
    }
}

/// Display labels mirror Compose's `bmiCategoryLabel`.
func bmiCategoryLabel(_ category: BmiCategory) -> String {
    switch category {
    case .underweight: return L10n.bmiCategoryUnderweight
    case .normal: return L10n.bmiCategoryNormal
    case .overweight: return L10n.bmiCategoryOverweight
    case .obese: return L10n.bmiCategoryObese
    default: return ""
    }
}

/// Display names mirror Compose's `bmiFormulaLabel`.
func bmiFormulaName(_ formula: BmiFormula) -> String {
    switch formula {
    case .standard: return L10n.bmiFormulaStandard
    case .trefethen: return L10n.bmiFormulaTrefethen
    default: return formula.value.capitalized
    }
}

/// Display names mirror Compose's `bmiClassificationLabel`.
func bmiClassificationName(_ classification: BmiClassification) -> String {
    switch classification {
    case .who: return L10n.bmiClassificationWho
    case .asianPacific: return L10n.bmiClassificationAsianPacific
    default: return classification.value.capitalized
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
}
