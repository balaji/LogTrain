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

struct ChatSheetView: View {
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
            }
            .scrollDismissesKeyboard(.interactively)
            .onChange(of: uiState.messages.last?.text) { _, _ in
                if let lastId = uiState.messages.last?.id {
                    withAnimation(.easeOut(duration: 0.15)) {
                        proxy.scrollTo(lastId, anchor: .bottom)
                    }
                }
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
                        Text(message.text)
                            .font(.body)
                            .textSelection(.enabled)
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
