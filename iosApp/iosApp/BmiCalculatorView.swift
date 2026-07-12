import SwiftUI
import SharedUI

/// Native mirror of Compose's `BmiCalculatorSheet`: weight and height prefill
/// from the saved profile in the user's unit system — kg/cm text fields in
/// metric, or lb text plus a tap-to-reveal feet/inches wheel in imperial.
/// Everything is converted to kg/cm for the calculation, and either input can
/// be overridden for a one-off estimate. The math, thresholds, and formatting
/// come from shared Kotlin.
struct BmiCalculatorView: View {
    private enum Field {
        case weight, height
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
    @State private var result: Double? = nil
    @FocusState private var focusedField: Field?

    private var unit: WeightUnit { settings.weightUnit }
    private var weightUnitLabel: String { unit == .imperial ? L10n.unitLb : L10n.unitKg }
    private var weight: Double? { Double(weightText).flatMap { $0 > 0 ? $0 : nil } }
    private var heightCm: Double? {
        if unit == .imperial {
            return HeightConversion.shared.feetInchesToCm(feet: Int32(feet), inches: Int32(inches))
        }
        return Double(heightText).flatMap { $0 > 0 ? $0 : nil }
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
                    result = InsightMath.shared.bmi(weightKg: unit.toKilograms(value: weight), heightCm: heightCm)
                }
                .disabled(weight == nil || heightCm == nil)
            }
            if let result {
                Section {
                    Text(L10n.bmiResult(
                        InsightMath.shared.formatNumber(value: result, maxDecimals: 1),
                        bmiCategoryLabel(BmiCategory.companion.of(bmi: result))
                    ))
                    BmiSpectrumView(bmi: result)
                        .padding(.vertical, 4)
                }
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
/// `BmiSpectrum`, including the band thresholds and the 15–40 display range.
private struct BmiSpectrumView: View {
    @Environment(\.colorScheme) private var scheme
    let bmi: Double

    // Mirrors BmiCategory.CHART_MIN / CHART_MAX in sharedLogic.
    private let chartMin = 15.0
    private let chartMax = 40.0

    // (category, upper bound) mirroring BmiCategory's lower bounds in
    // sharedLogic; the first band starts at chartMin, the last ends at chartMax.
    private var bands: [(category: BmiCategory, upper: Double)] {
        [(.underweight, 18.5), (.normal, 25.0), (.overweight, 30.0), (.obese, chartMax)]
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
                            .frame(width: w * bandFraction(band.upper, previous: lowerBound(band.category)))
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

    private func lowerBound(_ category: BmiCategory) -> Double {
        max(category.lowerBound, chartMin)
    }

    private func bandFraction(_ upper: Double, previous lower: Double) -> Double {
        (upper - lower) / (chartMax - chartMin)
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

private extension Color {
    init(rgb: UInt32) {
        self.init(
            red: Double((rgb >> 16) & 0xFF) / 255,
            green: Double((rgb >> 8) & 0xFF) / 255,
            blue: Double(rgb & 0xFF) / 255
        )
    }
}
