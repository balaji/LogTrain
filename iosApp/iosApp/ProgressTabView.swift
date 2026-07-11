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
        weightUnit: .metric,
        latestBodyWeight: "",
        savingWeight: false
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
                uiState = value
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
                    Label("Log weight", systemImage: "scalemass")
                }
            }
        }
        .alert("Log body weight", isPresented: $showLogWeight) {
            TextField("Weight (\(unitLabel(uiState.weightUnit)))", text: $weightText)
                .keyboardType(.decimalPad)
            Button("Save") {
                state.logWeight(text: weightText)
                weightText = ""
            }
            Button("Cancel", role: .cancel) { weightText = "" }
        }
    }

    private var emptyState: some View {
        ContentUnavailableView {
            Label("No progress yet", systemImage: "chart.line.uptrend.xyaxis")
        } description: {
            Text("Insights appear after your first completed workout or logged weight.")
        } actions: {
            Button("Log body weight") { showLogWeight = true }
        }
    }

    private var cardList: some View {
        List {
            Section {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Body weight")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                        Text(uiState.latestBodyWeight.isEmpty
                            ? "Not logged yet"
                            : "\(uiState.latestBodyWeight) \(unitLabel(uiState.weightUnit))")
                            .font(.headline)
                    }
                    Spacer()
                    Button("Log weight") { showLogWeight = true }
                        .buttonStyle(.bordered)
                }
            }
            Section {
                ForEach(uiState.cards, id: \.id) { card in
                    InsightCardView(
                        card: card,
                        unit: unitLabel(uiState.weightUnit),
                        narration: narrations[card.id]
                    )
                }
            }
        }
    }
}

/// English label for the stored weights' unit; the shared logic only exposes
/// the `WeightUnit` enum. Mirrors sharedUI's `weightUnitLabel`.
private func unitLabel(_ unit: WeightUnit) -> String {
    unit == .imperial ? "lb" : "kg"
}

private struct InsightCardView: View {
    let card: InsightCard
    let unit: String
    /// Nil when the on-device model is unavailable or this card has no
    /// narration; the card then renders numbers-only.
    let narration: CardNarration?
    @State private var pulse = false

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(title)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                Spacer()
                trendIcon
            }
            Text(text(card.headlineKey, card.headlineArgs))
                .font(.title3.weight(.semibold))
            Text(text(card.detailKey, card.detailArgs))
                .font(.subheadline)
                .foregroundStyle(.secondary)
            if card.spark.count >= 2 {
                sparkline
                    .padding(.top, 8)
            }
            if let narration, narration.status != "failed" {
                aiInsight(narration)
                    .padding(.top, 8)
            }
        }
        .padding(.vertical, 4)
    }

    private var title: String {
        switch card.kind {
        case "volume": return "Weekly volume"
        case "frequency": return "Consistency"
        case "body_weight": return "Body weight"
        // progression/plateau cards are titled by the exercise name.
        default: return card.exerciseName ?? ""
        }
    }

    /// English templates for the language-neutral headline/detail keys the
    /// shared logic emits; mirrors sharedUI's insightHeadline/insightDetail.
    private func text(_ key: String, _ args: [String]) -> String {
        switch key {
        case "volume_this_week": return "\(args[0]) \(unit) this week"
        case "streak_weeks": return "\(args[0])-week streak"
        case "no_streak": return "No active streak"
        case "plateau_at": return "Plateau at ~\(args[0]) \(unit)"
        case "est_1rm": return "Est. 1RM \(args[0]) \(unit)"
        case "latest_body_weight": return "\(args[0]) \(unit)"
        case "pct_vs_4_week_avg": return "\(args[0])% vs 4-week average"
        case "sets_in_sessions": return "\(args[0]) sets in \(args[1]) sessions"
        case "workouts_30_days": return "\(args[0]) workouts in the last 30 days"
        case "workouts_30_days_longest": return "\(args[0]) workouts in the last 30 days · longest streak \(args[1]) weeks"
        case "flat_across_weeks": return "Est. 1RM flat across \(args[0]) weeks"
        case "slope_per_week": return "\(args[0]) \(unit)/week over \(args[1]) weeks"
        case "change_30_days": return "\(args[0]) \(unit) in 30 days"
        case "log_regularly": return "Log regularly to see your trend"
        // An unknown key means sharedLogic is newer than this view; show the
        // bare values rather than nothing.
        default: return args.joined(separator: " ")
        }
    }

    @ViewBuilder
    private func aiInsight(_ narration: CardNarration) -> some View {
        HStack(alignment: .top, spacing: 6) {
            Image(systemName: "sparkles")
                .font(.caption)
                .foregroundStyle(.tint)
                .padding(.top, 2)
            if narration.status == "loading" {
                Text("Reading your training data and writing a note about this trend.")
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

    @ViewBuilder
    private var trendIcon: some View {
        switch card.trend {
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
