import SwiftUI
import SharedUI
import Charts

/// Progress tab: deterministic insight cards computed in shared Kotlin, plus
/// body-weight logging. Named to avoid clashing with SwiftUI's own
/// `ProgressView`.
struct ProgressTabView: View {
    @State private var state = ProgressScreenState()
    @State private var uiState = ProgressUiState(
        loading: true,
        cards: [],
        weightUnitLabel: "kg",
        latestBodyWeight: "",
        savingWeight: false
    )
    @State private var showLogWeight = false
    @State private var showChat = false
    @State private var weightText = ""

    /// Chat entry only appears when the on-device model exists (or is still
    /// downloading); ineligible devices keep the cards-only experience.
    private let chatAvailability = FoundationModelChatProvider.availabilityString()
    private var chatEnabled: Bool {
        chatAvailability == "available" || chatAvailability == "model_downloading"
    }

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
        .navigationDestination(isPresented: $showChat) {
            ProgressChatView()
        }
        .toolbar {
            if chatEnabled {
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        showChat = true
                    } label: {
                        Label("Ask about your training", systemImage: "sparkles")
                    }
                }
            }
            ToolbarItem(placement: .primaryAction) {
                Button {
                    showLogWeight = true
                } label: {
                    Label("Log weight", systemImage: "scalemass")
                }
            }
        }
        .alert("Log body weight", isPresented: $showLogWeight) {
            TextField("Weight (\(uiState.weightUnitLabel))", text: $weightText)
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
                            : "\(uiState.latestBodyWeight) \(uiState.weightUnitLabel)")
                            .font(.headline)
                    }
                    Spacer()
                    Button("Log weight") { showLogWeight = true }
                        .buttonStyle(.bordered)
                }
            }
            Section {
                ForEach(uiState.cards, id: \.id) { card in
                    InsightCardView(card: card)
                }
            }
        }
    }
}

private struct InsightCardView: View {
    let card: InsightCard

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(card.title)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                Spacer()
                trendIcon
            }
            Text(card.headline)
                .font(.title3.weight(.semibold))
            Text(card.detail)
                .font(.subheadline)
                .foregroundStyle(.secondary)
            if card.spark.count >= 2 {
                sparkline
                    .padding(.top, 8)
            }
        }
        .padding(.vertical, 4)
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
