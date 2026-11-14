import SwiftUI
import SharedUI

enum ChatActiveSheet: Identifiable {
    case chat
    case verifiedExercise(Int64)
    case verifiedDay(Int64)

    var id: String {
        switch self {
        case .chat: return "chat"
        case .verifiedExercise(let id): return "exercise-\(id)"
        case .verifiedDay(let day): return "day-\(day)"
        }
    }
}

/// Shared iOS coordinator owning the on-device `ChatState`, modal presentation,
/// and verification-pill navigation into Exercises / Calendar / Programs / Progress.
@Observable
final class ChatCoordinator {
    private let provider = FoundationModelChatProvider()
    let chatState: ChatState
    var activeSheet: ChatActiveSheet?
    var availability: String

    var isEntryVisible: Bool {
        availability != "device_not_eligible"
    }

    init() {
        self.chatState = ChatState(provider: provider)
        self.availability = provider.availability()
    }

    func refreshAvailability() {
        availability = provider.availability()
    }

    func open(seed: ChatSeedContext = ChatSeedContext(kind: "general", exerciseId: 0, exerciseName: "", epochDay: 0)) {
        refreshAvailability()
        chatState.openWithSeed(seed: seed)
        activeSheet = .chat
    }

    func openForExercise(id: Int64, name: String) {
        open(seed: ChatSeedContext(kind: "exercise", exerciseId: id, exerciseName: name, epochDay: 0))
    }

    func openForDay(epochDay: Int64) {
        open(seed: ChatSeedContext(kind: "day", exerciseId: 0, exerciseName: "", epochDay: epochDay))
    }
}

private struct ChatScrollMetrics: Equatable {
    let offsetY: CGFloat
    let distanceFromBottom: CGFloat
    let canScroll: Bool
}

private struct ChatScrollObservationKey: Equatable {
    let messageCount: Int
    let lastId: String
    let lastTextLen: Int
    let lastStatus: String
    let lastSourcesCount: Int
    let isGenerating: Bool
}

struct ChatSheetView: View {
    private static let bottomAnchorId = "__chat_bottom_anchor__"

    let chatState: ChatState
    let onSourceTap: (ChatSourceRef) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var uiState = ChatUiState(
        availability: "device_not_eligible",
        messages: [],
        isGenerating: false,
        seedContext: ChatSeedContext(kind: "general", exerciseId: 0, exerciseName: "", epochDay: 0),
        suggestedPrompts: []
    )
    @State private var inputText = ""
    @State private var scrollController = ChatAutoScrollController(
        throttleIntervalMillis: ChatAutoScrollController.companion.DEFAULT_THROTTLE_INTERVAL_MS
    )
    @State private var showScrollToBottomBadge = false
    @State private var throttleTask: Task<Void, Never>?
    @State private var scrollPhase: ScrollPhase = .idle
    @State private var lastUserScrollDirectionDown = false
    @State private var latestDistanceFromBottom: CGFloat = 0

    private var bottomThreshold: CGFloat {
        CGFloat(ChatAutoScrollController.companion.BOTTOM_THRESHOLD_DP)
    }

    private var scrollObservationKey: ChatScrollObservationKey {
        let last = uiState.messages.last
        return ChatScrollObservationKey(
            messageCount: uiState.messages.count,
            lastId: last?.id ?? "",
            lastTextLen: last?.text.utf16.count ?? 0,
            lastStatus: last?.status ?? "",
            lastSourcesCount: last?.sources.count ?? 0,
            isGenerating: uiState.isGenerating
        )
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                if let banner = L10n.chatAvailabilityMessage(uiState.availability) {
                    Text(banner)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .background(Color(.secondarySystemBackground))
                }

                if uiState.messages.isEmpty {
                    emptyState
                } else {
                    messageList
                }

                Divider()
                composerBar
            }
            .navigationTitle(L10n.chatTitle)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    if !uiState.messages.isEmpty {
                        Button(L10n.chatNewChat) {
                            chatState.clearConversation()
                        }
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(L10n.done) {
                        chatState.cancelGeneration()
                        dismiss()
                    }
                }
            }
        }
        .task {
            for await state in chatState.uiState {
                uiState = state
            }
        }
        .onChange(of: uiState.messages.isEmpty) { _, isEmpty in
            if isEmpty {
                throttleTask?.cancel()
                throttleTask = nil
                _ = scrollController.onMessagesUpdated(messages: [], isGenerating: false)
                showScrollToBottomBadge = false
                lastUserScrollDirectionDown = false
            }
        }
    }

    private var emptyState: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text(L10n.chatOnDeviceSubtitle)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)

                ForEach(Array(uiState.suggestedPrompts.enumerated()), id: \.offset) { _, prompt in
                    let label = L10n.chatSuggestedPrompt(prompt)
                    Button {
                        chatState.sendMessage(rawText: label)
                    } label: {
                        Text(label)
                            .font(.subheadline)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 10)
                            .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 12))
                    }
                    .buttonStyle(.plain)
                    .disabled(uiState.availability != "available")
                }
            }
            .padding(16)
        }
    }

    private var messageList: some View {
        ScrollViewReader { proxy in
            ScrollView {
                VStack(spacing: 0) {
                    LazyVStack(spacing: 12) {
                        ForEach(uiState.messages, id: \.id) { message in
                            ChatBubbleRow(
                                message: message,
                                onRetry: { chatState.retryLast() },
                                onSourceTap: { source in
                                    chatState.cancelGeneration()
                                    dismiss()
                                    onSourceTap(source)
                                }
                            )
                            .id(message.id)
                        }
                    }
                    .padding(16)

                    Color.clear
                        .frame(height: 1)
                        .id(Self.bottomAnchorId)
                }
            }
            .scrollDismissesKeyboard(.interactively)
            .onScrollPhaseChange { oldPhase, newPhase, context in
                scrollPhase = newPhase
                let dist = max(0, context.geometry.contentSize.height - context.geometry.visibleRect.maxY)
                if latestDistanceFromBottom != dist {
                    latestDistanceFromBottom = dist
                }

                let wasTouching = oldPhase == .tracking || oldPhase == .interacting
                let isTouching = newPhase == .tracking || newPhase == .interacting

                if isTouching && !wasTouching {
                    if lastUserScrollDirectionDown {
                        lastUserScrollDirectionDown = false
                    }
                    applyScrollDecision(scrollController.onUserTouchDown(), proxy: proxy)
                } else if wasTouching && !isTouching {
                    applyScrollDecision(scrollController.onUserTouchUp(), proxy: proxy)
                }

                if newPhase == .idle &&
                    !scrollController.isPinnedToBottom &&
                    dist <= bottomThreshold &&
                    (lastUserScrollDirectionDown || oldPhase == .tracking || oldPhase == .animating)
                {
                    applyScrollDecision(scrollController.onUserReachedBottom(), proxy: proxy)
                }
            }
            .onScrollGeometryChange(for: ChatScrollMetrics.self) { geo in
                let distanceFromBottom = max(0, geo.contentSize.height - geo.visibleRect.maxY)
                let canScroll = geo.contentSize.height > geo.visibleRect.height + 1
                return ChatScrollMetrics(
                    offsetY: geo.contentOffset.y,
                    distanceFromBottom: distanceFromBottom,
                    canScroll: canScroll
                )
            } action: { oldMetrics, newMetrics in
                let isUserDriven = scrollPhase == .tracking || scrollPhase == .interacting || scrollPhase == .decelerating
                guard isUserDriven else { return }

                if newMetrics.offsetY < oldMetrics.offsetY - 0.5 &&
                    (newMetrics.canScroll || newMetrics.distanceFromBottom > bottomThreshold)
                {
                    if lastUserScrollDirectionDown {
                        lastUserScrollDirectionDown = false
                    }
                    if scrollController.isPinnedToBottom || scrollController.isAtBottom {
                        applyScrollDecision(scrollController.onUserScrolledUp(), proxy: proxy)
                    }
                } else if newMetrics.offsetY > oldMetrics.offsetY + 0.5 {
                    if !lastUserScrollDirectionDown {
                        lastUserScrollDirectionDown = true
                    }
                    if !scrollController.isPinnedToBottom && newMetrics.distanceFromBottom <= bottomThreshold {
                        applyScrollDecision(scrollController.onUserReachedBottom(), proxy: proxy)
                    }
                }
            }
            .onChange(of: scrollObservationKey, initial: true) { _, _ in
                applyScrollDecision(
                    scrollController.onMessagesUpdated(
                        messages: uiState.messages,
                        isGenerating: uiState.isGenerating
                    ),
                    proxy: proxy
                )
            }
            .overlay(alignment: .bottom) {
                if showScrollToBottomBadge {
                    Button {
                        lastUserScrollDirectionDown = false
                        applyScrollDecision(
                            scrollController.onScrollToBottomTapped(),
                            proxy: proxy
                        )
                    } label: {
                        Label(L10n.chatScrollToBottom, systemImage: "arrow.down")
                            .font(.subheadline.weight(.medium))
                            .padding(.horizontal, 14)
                            .padding(.vertical, 8)
                            .background(.regularMaterial, in: Capsule())
                            .shadow(color: .black.opacity(0.14), radius: 6, y: 2)
                    }
                    .buttonStyle(.plain)
                    .padding(.bottom, 12)
                    .transition(.scale(scale: 0.9).combined(with: .opacity))
                }
            }
            .animation(.easeInOut(duration: 0.18), value: showScrollToBottomBadge)
            .onDisappear {
                throttleTask?.cancel()
                throttleTask = nil
            }
        }
    }

    private func applyScrollDecision(_ decision: ChatScrollDecision, proxy: ScrollViewProxy) {
        if showScrollToBottomBadge != decision.showScrollToBottomBadge {
            showScrollToBottomBadge = decision.showScrollToBottomBadge
        }

        switch decision.action {
        case ChatScrollDecision.companion.ACTION_CANCEL:
            throttleTask?.cancel()
            throttleTask = nil
        case ChatScrollDecision.companion.ACTION_SCROLL_SMOOTH:
            throttleTask?.cancel()
            throttleTask = nil
            withAnimation(.easeOut(duration: 0.08)) {
                proxy.scrollTo(Self.bottomAnchorId, anchor: .bottom)
            }
        case ChatScrollDecision.companion.ACTION_SCROLL_SETTLE:
            throttleTask?.cancel()
            throttleTask = nil
            settleToBottom(proxy: proxy)
        default:
            break
        }

        if decision.scheduleDelayMillis > 0 {
            throttleTask?.cancel()
            let delayMs = UInt64(decision.scheduleDelayMillis)
            throttleTask = Task { @MainActor in
                try? await Task.sleep(for: .milliseconds(delayMs))
                guard !Task.isCancelled else { return }
                applyScrollDecision(scrollController.onThrottleTimerFired(), proxy: proxy)
            }
        }
    }

    private func settleToBottom(proxy: ScrollViewProxy) {
        var transaction = Transaction()
        transaction.disablesAnimations = true
        withTransaction(transaction) {
            proxy.scrollTo(Self.bottomAnchorId, anchor: .bottom)
        }
        Task { @MainActor in
            await Task.yield()
            var followUp = Transaction()
            followUp.disablesAnimations = true
            withTransaction(followUp) {
                proxy.scrollTo(Self.bottomAnchorId, anchor: .bottom)
            }
        }
    }

    private var composerBar: some View {
        HStack(alignment: .bottom, spacing: 8) {
            TextField(L10n.chatInputPlaceholder, text: $inputText, axis: .vertical)
                .textFieldStyle(.plain)
                .lineLimit(1...4)
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 18))
                .disabled(uiState.availability != "available")
                .onSubmit {
                    if !uiState.isGenerating {
                        submitPrompt()
                    }
                }

            Button {
                if uiState.isGenerating {
                    chatState.cancelGeneration()
                } else {
                    submitPrompt()
                }
            } label: {
                Image(systemName: uiState.isGenerating ? "stop.circle.fill" : "arrow.up.circle.fill")
                    .font(.system(size: 28))
            }
            .accessibilityLabel(uiState.isGenerating ? L10n.chatStop : L10n.chatSend)
            .disabled(
                !uiState.isGenerating &&
                (uiState.availability != "available" || inputText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            )
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
    }

    private func submitPrompt() {
        let trimmed = inputText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        inputText = ""
        chatState.sendMessage(rawText: trimmed)
    }
}

private struct ChatBubbleRow: View {
    let message: ChatMessage
    let onRetry: () -> Void
    let onSourceTap: (ChatSourceRef) -> Void

    private var isUser: Bool {
        message.role == ChatMessage.companion.ROLE_USER
    }

    var body: some View {
        VStack(alignment: isUser ? .trailing : .leading, spacing: 6) {
            HStack {
                if isUser { Spacer(minLength: 48) }

                VStack(alignment: .leading, spacing: 6) {
                    if message.status == ChatMessage.companion.STATUS_STREAMING && message.text.isEmpty {
                        HStack(spacing: 8) {
                            ProgressView()
                                .controlSize(.small)
                            Text(L10n.chatThinking)
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }
                    } else if !message.text.isEmpty {
                        if isUser {
                            Text(message.text)
                                .font(.body)
                                .textSelection(.enabled)
                        } else {
                            MarkdownTextView(markdown: message.text)
                        }
                    }

                    if message.status == ChatMessage.companion.STATUS_CANCELLED {
                        Text(L10n.chatCancelled)
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }

                    if message.status == ChatMessage.companion.STATUS_ERROR {
                        Text(L10n.chatErrorLabel(message.errorKey))
                            .font(.footnote)
                            .foregroundStyle(.red)
                        Button(L10n.chatRetry) {
                            onRetry()
                        }
                        .font(.footnote.weight(.medium))
                    }
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .background(
                    isUser ? Color.accentColor.opacity(0.16) : Color(.secondarySystemBackground),
                    in: RoundedRectangle(cornerRadius: 16)
                )

                if !isUser { Spacer(minLength: 48) }
            }

            if !isUser && !message.sources.isEmpty && message.status == ChatMessage.companion.STATUS_COMPLETE {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ForEach(Array(message.sources.enumerated()), id: \.offset) { _, source in
                            Button {
                                onSourceTap(source)
                            } label: {
                                Text(source.label)
                                    .font(.caption)
                                    .padding(.horizontal, 10)
                                    .padding(.vertical, 5)
                                    .background(Color(.tertiarySystemFill), in: Capsule())
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
        }
    }
}
