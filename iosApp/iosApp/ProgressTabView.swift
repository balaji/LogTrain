import SwiftUI
import SharedUI
import Charts

/// Progress tab: insight cards computed in shared Kotlin, each narrated by
/// the on-device model when Apple Intelligence is available, plus body-weight
/// logging. Named to avoid clashing with SwiftUI's own `ProgressView`.
struct ProgressTabView: View {
    @State private var state = ProgressScreenState()
    @State private var narrationState = InsightNarrationState(
        provider: FoundationModelInsightProvider()
    )
    @State private var uiState = ProgressUiState(
        loading: true,
        cards: [],
        bodyPartBreakdown: [],
        weightUnit: .metric,
        latestBodyWeight: "",
        savingWeight: false,
        progressionOptions: [],
        progressionConfigs: []
    )
    @State private var narrations: [String: CardNarration] = [:]
    @State private var showLogWeight = false
    @State private var weightText = ""

    var body: some View {
        Group {
            if uiState.loading {
                ProgressView()
            } else if uiState.cards.isEmpty {
                emptyState
            } else {
                cardList
            }
        }
        .task {
            for await value in state.uiState {
                // Editing a progression card lands as a burst of emissions
                // (configs first, rebuilt cards a beat later); letting the
                // List animate each one reads as a jarring flicker.
                var transaction = Transaction()
                transaction.disablesAnimations = true
                withTransaction(transaction) { uiState = value }
            }
        }
        .task {
            for await value in narrationState.narrations {
                withAnimation(.easeIn(duration: 0.3)) {
                    narrations = value
                }
            }
        }
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button {
                    showLogWeight = true
                } label: {
                    Label(L10n.logWeight, systemImage: "scalemass")
                }
            }
        }
        .alert(L10n.logBodyWeight, isPresented: $showLogWeight) {
            TextField("\(L10n.weight) (\(unitLabel(uiState.weightUnit)))", text: $weightText)
                .keyboardType(.decimalPad)
            Button(L10n.save) {
                state.logWeight(text: weightText)
                weightText = ""
            }
            Button(L10n.cancel, role: .cancel) { weightText = "" }
        }
    }

    private var emptyState: some View {
        ContentUnavailableView {
            Label(L10n.noProgressYet, systemImage: "chart.line.uptrend.xyaxis")
        } description: {
            Text(L10n.progressEmptyHint)
        } actions: {
            Button(L10n.logBodyWeight) { showLogWeight = true }
        }
    }

    private var cardList: some View {
        List {
            Section {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(L10n.bodyWeight)
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                        Text(uiState.latestBodyWeight.isEmpty
                            ? L10n.notLoggedYet
                            : "\(uiState.latestBodyWeight) \(unitLabel(uiState.weightUnit))")
                            .font(.headline)
                    }
                    Spacer()
                    Button(L10n.logWeight) { showLogWeight = true }
                        .buttonStyle(.bordered)
                }
            }
            if !uiState.bodyPartBreakdown.isEmpty {
                Section {
                    BodyPartBreakdownCard(slices: uiState.bodyPartBreakdown)
                }
            }
            // One section per insight, like the profile screen's groups —
            // except the progression cards, which share a single section.
            ForEach(leadingCards, id: \.id) { card in
                Section {
                    InsightCardView(
                        card: card,
                        unit: unitLabel(uiState.weightUnit),
                        narration: narrations[card.id]
                    )
                }
            }
            if !progressionCards.isEmpty {
                Section {
                    ForEach(progressionCards, id: \.id) { card in
                        ProgressionCardView(
                            card: card,
                            selected: selectedExercises(for: card),
                            options: uiState.progressionOptions,
                            unit: unitLabel(uiState.weightUnit),
                            canRemove: uiState.progressionConfigs.count > 1,
                            narration: narrations[card.id],
                            onSetExercises: { state.setProgressionCardExercises(index: Int32(slot(of: card)), exercises: $0) },
                            onToggleAxis: { state.toggleProgressionAxis(index: Int32(slot(of: card))) },
                            onRemove: { state.removeProgressionCard(index: Int32(slot(of: card))) }
                        )
                    }
                    if uiState.progressionConfigs.count < 3, let unused = firstUnusedOption {
                        Button {
                            state.addProgressionCard(exercise: unused)
                        } label: {
                            Label(L10n.insightAddComparison, systemImage: "plus")
                        }
                    }
                }
            }
            ForEach(trailingCards, id: \.id) { card in
                Section {
                    InsightCardView(
                        card: card,
                        unit: unitLabel(uiState.weightUnit),
                        narration: narrations[card.id]
                    )
                }
            }
        }
    }

    /// The configured selection backing a progression card; falls back to the
    /// card's own series when state and cards briefly disagree mid-update.
    private func selectedExercises(for card: InsightCard) -> [String] {
        let slot = slot(of: card)
        if slot < uiState.progressionConfigs.count {
            return uiState.progressionConfigs[slot].exercises
        }
        return card.series.map(\.name)
    }

    private func slot(of card: InsightCard) -> Int {
        Int(card.id.dropFirst("progression:slot".count)) ?? 0
    }

    // buildCards emits progression cards contiguously (after volume and
    // frequency, before body weight), so partitioning around that run keeps
    // the original card order.
    private var progressionCards: [InsightCard] {
        uiState.cards.filter { progressionKinds.contains($0.kind) }
    }

    private var leadingCards: [InsightCard] {
        Array(uiState.cards.prefix { !progressionKinds.contains($0.kind) })
    }

    private var trailingCards: [InsightCard] {
        uiState.cards.dropFirst(leadingCards.count).filter { !progressionKinds.contains($0.kind) }
    }

    /// Default exercise for a new comparison card: the top eligible exercise
    /// not already on some card.
    private var firstUnusedOption: String? {
        uiState.progressionOptions.first { option in
            !uiState.progressionConfigs.contains { $0.exercises.contains(option) }
        } ?? uiState.progressionOptions.first
    }
}

private let progressionKinds: Set<String> = ["progression", "plateau", "progression_multi"]

/// Localized label for the stored weights' unit; the shared logic only exposes
/// the `WeightUnit` enum. Mirrors sharedUI's `weightUnitLabel`.
private func unitLabel(_ unit: WeightUnit) -> String {
    unit == .imperial ? L10n.unitLb : L10n.unitKg
}

/// Completed sets per primary body part as a pie plus legend; mirrors
/// sharedUI's BodyPartBreakdownCard, including the fixed CVD-safe categorical
/// palette (one step set per color mode) and the folded gray "Other" tail
/// past eight slices.
private struct BodyPartBreakdownCard: View {
    let slices: [BodyPartSlice]
    @Environment(\.colorScheme) private var colorScheme

    private static let maxSlices = 8
    private static let lightColors: [Color] = [
        Color(rgb: 0x2A78D6), Color(rgb: 0x1BAF7A), Color(rgb: 0xEDA100), Color(rgb: 0x008300),
        Color(rgb: 0x4A3AA7), Color(rgb: 0xE34948), Color(rgb: 0xE87BA4), Color(rgb: 0xEB6834),
    ]
    private static let darkColors: [Color] = [
        Color(rgb: 0x3987E5), Color(rgb: 0x199E70), Color(rgb: 0xC98500), Color(rgb: 0x008300),
        Color(rgb: 0x9085E9), Color(rgb: 0xE66767), Color(rgb: 0xD55181), Color(rgb: 0xD95926),
    ]
    private static let otherColor = Color(rgb: 0x898781)

    private struct Entry: Identifiable {
        let label: String
        let color: Color
        let setCount: Int
        var id: String { label }
    }

    private var total: Int {
        slices.reduce(0) { $0 + Int($1.setCount) }
    }

    private var entries: [Entry] {
        let colors = colorScheme == .dark ? Self.darkColors : Self.lightColors
        let shown = slices.count > Self.maxSlices ? Array(slices.prefix(Self.maxSlices - 1)) : slices
        var result = shown.enumerated().map { i, slice in
            Entry(label: L10n.exerciseCatalogLabel(slice.bodyPart), color: colors[i], setCount: Int(slice.setCount))
        }
        let rest = total - shown.reduce(0) { $0 + Int($1.setCount) }
        if rest > 0 {
            result.append(Entry(label: L10n.bodyPartOther, color: Self.otherColor, setCount: rest))
        }
        return result
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(L10n.insightTitleBodyParts)
                .font(.subheadline)
                .foregroundStyle(.secondary)
            HStack(spacing: 16) {
                Chart(entries) { entry in
                    SectorMark(angle: .value("Sets", entry.setCount), angularInset: 1)
                        .foregroundStyle(entry.color)
                }
                .chartLegend(.hidden)
                .frame(width: 112, height: 112)
                VStack(alignment: .leading, spacing: 6) {
                    ForEach(entries) { entry in
                        legendRow(entry)
                    }
                }
            }
            .padding(.top, 8)
        }
        .padding(.vertical, 4)
    }

    private func legendRow(_ entry: Entry) -> some View {
        HStack(spacing: 8) {
            Circle()
                .fill(entry.color)
                .frame(width: 10, height: 10)
            Text(entry.label)
                .font(.subheadline)
            Spacer()
            Text(L10n.insightBodyPartShare("\(entry.setCount)", "\(percent(entry))"))
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
    }

    private func percent(_ entry: Entry) -> Int {
        Int((Double(entry.setCount) * 100 / Double(total)).rounded())
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

/// Localized templates for the language-neutral headline/detail keys the
/// shared logic emits; mirrors sharedUI's insightHeadline/insightDetail.
private func insightText(_ key: String, _ args: [String], _ unit: String) -> String {
    switch key {
    case "volume_this_week": return L10n.insightVolumeThisWeek(args[0], unit)
    case "streak_weeks": return L10n.insightStreakWeeks(args[0])
    case "no_streak": return L10n.insightNoStreak
    case "plateau_at": return L10n.insightPlateauAt(args[0], unit)
    case "est_1rm": return L10n.insightEst1Rm(args[0], unit)
    case "latest_body_weight": return L10n.insightLatestBodyWeight(args[0], unit)
    case "pct_vs_4_week_avg": return L10n.insightPctVs4WeekAvg(args[0])
    case "sets_in_sessions": return L10n.insightSetsInSessions(args[0], args[1])
    case "workouts_30_days": return L10n.insightWorkouts30Days(args[0])
    case "workouts_30_days_longest": return L10n.insightWorkouts30DaysLongest(args[0], args[1])
    case "flat_across_weeks": return L10n.insightFlatAcrossWeeks(args[0])
    case "slope_per_week": return L10n.insightSlopePerWeek(args[0], unit, args[1])
    case "change_30_days": return L10n.insightChange30Days(args[0], unit)
    case "log_regularly": return L10n.insightLogRegularly
    // An unknown key means sharedLogic is newer than this view; show the
    // bare values rather than nothing.
    default: return args.joined(separator: " ")
    }
}

/// One decimal, trailing ".0" dropped; mirrors sharedLogic's
/// InsightMath.formatNumber for the per-series legend stats.
private func formatStat(_ value: Double) -> String {
    let rounded = (value * 10).rounded() / 10
    return rounded == rounded.rounded() ? String(Int(rounded)) : String(rounded)
}

private struct TrendIconView: View {
    let trend: String

    var body: some View {
        switch trend {
        case "up":
            Image(systemName: "arrow.up.right").foregroundStyle(.green)
        case "down":
            Image(systemName: "arrow.down.right").foregroundStyle(.orange)
        case "flat":
            Image(systemName: "arrow.right").foregroundStyle(.secondary)
        default:
            EmptyView()
        }
    }
}

private struct InsightNarrationView: View {
    let narration: CardNarration
    @State private var pulse = false

    var body: some View {
        HStack(alignment: .top, spacing: 6) {
            Image(systemName: "sparkles")
                .font(.caption)
                .foregroundStyle(.tint)
                .padding(.top, 2)
            if narration.status == "loading" {
                Text(L10n.insightNarrationLoading)
                    .font(.footnote)
                    .redacted(reason: .placeholder)
                    .opacity(pulse ? 0.4 : 0.9)
                    .animation(
                        .easeInOut(duration: 0.9).repeatForever(autoreverses: true),
                        value: pulse
                    )
                    .onAppear { pulse = true }
            } else {
                Text(narration.text)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .transition(.opacity)
            }
        }
    }
}

/// One visit within a week's consistency bar, stacked with its siblings.
private struct WeeklyVisitSegment: Identifiable {
    let weekKey: String
    let index: Int
    var id: String { "\(weekKey):\(index)" }
}

private struct InsightCardView: View {
    let card: InsightCard
    let unit: String
    /// Nil when the on-device model is unavailable or this card has no
    /// narration; the card then renders numbers-only.
    let narration: CardNarration?

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(title)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                Spacer()
                TrendIconView(trend: card.trend)
            }
            Text(insightText(card.headlineKey, card.headlineArgs, unit))
                .font(.title3.weight(.semibold))
            Text(insightText(card.detailKey, card.detailArgs, unit))
                .font(.subheadline)
                .foregroundStyle(.secondary)
            if card.kind == "frequency", !card.spark.isEmpty {
                weeklyVisitBars
                    .padding(.top, 8)
            } else if card.spark.count >= 2 {
                sparkline
                    .padding(.top, 8)
            }
            if let narration, narration.status != "failed" {
                InsightNarrationView(narration: narration)
                    .padding(.top, 8)
            }
        }
        .padding(.vertical, 4)
    }

    private var title: String {
        switch card.kind {
        case "volume": return L10n.insightTitleVolume
        case "frequency": return L10n.insightTitleFrequency
        case "body_weight": return L10n.bodyWeight
        // progression/plateau cards are titled by the exercise name.
        default: return card.exerciseName ?? ""
        }
    }

    /// Sessions per week as a stack of one segment per visit, one slot per
    /// week (zero weeks keep their slot in the fixed time axis, via an
    /// explicit x-domain since they contribute no segments); mirrors
    /// sharedUI's WeeklyVisitBars. Segmenting the bar makes the count
    /// directly countable instead of requiring a height comparison.
    private var weeklyVisitBars: some View {
        let weekKeys = card.spark.map { String($0.epochDay) }
        let maxCount = Swift.max(card.spark.map(\.value).max() ?? 1, 1)
        return Chart(weeklyVisitSegments) { segment in
            BarMark(
                x: .value("Week", segment.weekKey),
                yStart: .value("Start", Double(segment.index) + 0.08),
                yEnd: .value("End", Double(segment.index) + 0.92)
            )
            .cornerRadius(2)
        }
        .chartXAxis(.hidden)
        .chartYAxis(.hidden)
        .chartXScale(domain: weekKeys)
        .chartYScale(domain: 0...maxCount)
        .frame(height: 40)
    }

    private var weeklyVisitSegments: [WeeklyVisitSegment] {
        card.spark.flatMap { point in
            (0..<Int(point.value)).map { index in
                WeeklyVisitSegment(weekKey: String(point.epochDay), index: index)
            }
        }
    }

    private var sparkline: some View {
        Chart(card.spark, id: \.epochDay) { point in
            LineMark(
                x: .value("Day", point.epochDay),
                y: .value("Value", point.value)
            )
            .interpolationMethod(.monotone)
        }
        .chartXAxis(.hidden)
        .chartYAxis(.hidden)
        .chartXScale(domain: xDomain)
        .chartYScale(domain: yDomain)
        .frame(height: 40)
    }

    private var xDomain: ClosedRange<Int64> {
        let days = card.spark.map(\.epochDay)
        let lo = days.min() ?? 0
        let hi = days.max() ?? 1
        return lo...(hi > lo ? hi : lo + 1)
    }

    private var yDomain: ClosedRange<Double> {
        let values = card.spark.map(\.value)
        let lo = values.min() ?? 0
        let hi = values.max() ?? 1
        let pad = Swift.max((hi - lo) * 0.1, 0.5)
        return (lo - pad)...(hi + pad)
    }
}

/// A user-configurable progression card: an exercise multi-select menu (up
/// to three exercises overlaid on one est-1RM chart), a %-change axis
/// toggle, and a remove action. Single-exercise cards keep the classic
/// headline, detail, and narration; comparisons render one legend row per
/// series and are never narrated. Mirrors sharedUI's ProgressionCard.
private struct ProgressionCardView: View {
    let card: InsightCard
    let selected: [String]
    let options: [String]
    let unit: String
    let canRemove: Bool
    let narration: CardNarration?
    let onSetExercises: ([String]) -> Void
    let onToggleAxis: () -> Void
    let onRemove: () -> Void
    @Environment(\.colorScheme) private var colorScheme

    private static let maxCompared = 3
    // First steps of the body-part palettes: fixed, CVD-safe, per color mode.
    private static let lightColors: [Color] = [
        Color(rgb: 0x2A78D6), Color(rgb: 0x1BAF7A), Color(rgb: 0xEDA100),
    ]
    private static let darkColors: [Color] = [
        Color(rgb: 0x3987E5), Color(rgb: 0x199E70), Color(rgb: 0xC98500),
    ]

    private func seriesColor(_ index: Int) -> Color {
        let palette = colorScheme == .dark ? Self.darkColors : Self.lightColors
        return palette[index % palette.count]
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            exerciseMenu
            if card.series.count > 1 {
                legend
            } else {
                HStack {
                    Text(insightText(card.headlineKey, card.headlineArgs, unit))
                        .font(.title3.weight(.semibold))
                    Spacer()
                    TrendIconView(trend: card.trend)
                }
                Text(insightText(card.detailKey, card.detailArgs, unit))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            if card.series.contains(where: { $0.points.count >= 2 }) {
                chart
                    .padding(.top, 8)
            }
            controls
            if let narration, narration.status != "failed" {
                InsightNarrationView(narration: narration)
                    .padding(.top, 8)
            }
        }
        .padding(.vertical, 4)
    }

    private var exerciseMenu: some View {
        Menu {
            ForEach(options, id: \.self) { option in
                let isSelected = selected.contains(option)
                Button {
                    onSetExercises(isSelected ? selected.filter { $0 != option } : selected + [option])
                } label: {
                    if isSelected {
                        Label(option, systemImage: "checkmark")
                    } else {
                        Text(option)
                    }
                }
                .disabled(isSelected ? selected.count <= 1 : selected.count >= Self.maxCompared)
            }
        } label: {
            HStack(spacing: 4) {
                Text(selected.joined(separator: ", "))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                Image(systemName: "chevron.up.chevron.down")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                Spacer()
            }
        }
        .buttonStyle(.plain)
    }

    private var legend: some View {
        VStack(alignment: .leading, spacing: 4) {
            ForEach(Array(card.series.enumerated()), id: \.element.name) { index, series in
                HStack(spacing: 8) {
                    Circle()
                        .fill(seriesColor(index))
                        .frame(width: 10, height: 10)
                    Text(series.name)
                        .font(.subheadline)
                        .lineLimit(1)
                    Spacer()
                    Text(L10n.insightEst1Rm(formatStat(series.latest), unit))
                        .font(.subheadline)
                    Text(L10n.insightSeriesSlope("\(series.slopePerWeek >= 0 ? "+" : "")\(formatStat(series.slopePerWeek))", unit))
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .padding(.top, 4)
    }

    private var chart: some View {
        Chart {
            ForEach(Array(card.series.enumerated()), id: \.element.name) { index, series in
                let base = series.points.first?.value ?? 0
                ForEach(series.points, id: \.epochDay) { point in
                    LineMark(
                        x: .value("Day", point.epochDay),
                        y: .value("Value", displayValue(point.value, base: base)),
                        series: .value("Exercise", series.name)
                    )
                    .interpolationMethod(.monotone)
                    .foregroundStyle(seriesColor(index))
                }
            }
        }
        .chartXAxis(.hidden)
        // Unlike the single sparkline, the y-axis stays visible so the
        // %-change toggle has an observable effect.
        .chartYAxis {
            AxisMarks(position: .leading, values: .automatic(desiredCount: 2))
        }
        // Explicit domains: Swift Charts includes zero in automatic numeric
        // domains, which would squash epoch-day x-values into an invisible
        // sliver at the right edge.
        .chartXScale(domain: xDomain)
        .chartYScale(domain: yDomain)
        .frame(height: 56)
    }

    /// Re-bases a point to percent moved since the series' first week when
    /// the card is in %-change mode.
    private func displayValue(_ value: Double, base: Double) -> Double {
        guard card.showPctChange else { return value }
        return base == 0 ? 0 : (value / base - 1) * 100
    }

    /// All charted values after the %-change transform, series by series.
    private var displayedValues: [Double] {
        card.series.flatMap { series in
            let base = series.points.first?.value ?? 0
            return series.points.map { displayValue($0.value, base: base) }
        }
    }

    private var xDomain: ClosedRange<Int64> {
        let days = card.series.flatMap { $0.points.map(\.epochDay) }
        let lo = days.min() ?? 0
        let hi = days.max() ?? 1
        return lo...(hi > lo ? hi : lo + 1)
    }

    private var yDomain: ClosedRange<Double> {
        let values = displayedValues
        let lo = values.min() ?? 0
        let hi = values.max() ?? 1
        let pad = Swift.max((hi - lo) * 0.1, 0.5)
        return (lo - pad)...(hi + pad)
    }

    private var controls: some View {
        HStack {
            Button(L10n.insightAxisPctChange) { onToggleAxis() }
                .font(.footnote)
                .buttonStyle(.bordered)
                .tint(card.showPctChange ? .accentColor : .secondary)
            Spacer()
            if canRemove {
                Button(role: .destructive) {
                    onRemove()
                } label: {
                    Image(systemName: "trash")
                }
                .buttonStyle(.borderless)
            }
        }
        .padding(.top, 4)
    }
}
