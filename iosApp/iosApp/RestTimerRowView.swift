import SwiftUI
import SharedUI

/// The rest countdown shown under the set that started it: remaining time and
/// a bar draining once a second. Tapping the row reveals −10s / Skip / +10s
/// controls acting on the shared `RestTimerController`. SwiftUI counterpart
/// of the Compose `RestTimerRow`.
struct RestTimerRowView: View {
    let state: RestTimerUiState
    @State private var expanded = false

    private var progress: Double {
        state.totalSeconds > 0 ? Double(state.remainingSeconds) / Double(state.totalSeconds) : 0
    }

    var body: some View {
        VStack(spacing: 4) {
            HStack(spacing: 12) {
                Text(RestTimerFormat.format(seconds: Int(state.remainingSeconds)))
                    .font(.headline)
                    .monospacedDigit()
                ProgressView(value: progress)
                    // One linear second per tick, so the bar drains
                    // continuously instead of stepping.
                    .animation(.linear(duration: 1), value: progress)
            }
            if expanded {
                HStack {
                    Spacer()
                    Button(L10n.restTimerMinus10) { RestTimerController.shared.adjust(deltaSeconds: -10) }
                    Spacer()
                    Button(L10n.restTimerSkip) { RestTimerController.shared.cancel() }
                    Spacer()
                    Button(L10n.restTimerPlus10) { RestTimerController.shared.adjust(deltaSeconds: 10) }
                    Spacer()
                }
                .buttonStyle(.borderless)
                .font(.subheadline)
            }
        }
        .padding(.vertical, 8)
        .padding(.horizontal, 12)
        .background(Color.accentColor.opacity(0.12))
        .clipShape(RoundedRectangle(cornerRadius: 10))
        .contentShape(Rectangle())
        .onTapGesture {
            withAnimation(.easeOut(duration: 0.15)) { expanded.toggle() }
        }
    }
}
