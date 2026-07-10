import SwiftUI
import SharedUI

/// On-device insights chat, backed by the shared `ProgressChatState` with
/// the Foundation Models provider. Reached from the Progress tab only when
/// the model is (or will be) available on this device.
struct ProgressChatView: View {
    @State private var state = ProgressChatState(provider: FoundationModelChatProvider())
    @State private var uiState = ProgressChatUiState(
        availability: "no_provider",
        messages: [],
        responding: false,
        errorMessage: nil
    )
    @State private var input = ""
    @FocusState private var inputFocused: Bool

    var body: some View {
        VStack(spacing: 0) {
            messageList
            inputBar
        }
        .navigationTitle("Ask about your training")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button {
                    state.resetConversation()
                } label: {
                    Label("New chat", systemImage: "square.and.pencil")
                }
                .disabled(uiState.messages.isEmpty)
            }
        }
        .task {
            for await value in state.uiState {
                uiState = value
            }
        }
        .alert(
            "Something went wrong",
            isPresented: Binding(
                get: { uiState.errorMessage != nil },
                set: { if !$0 { state.clearError() } }
            )
        ) {
            Button("OK", role: .cancel) { state.clearError() }
        } message: {
            Text(uiState.errorMessage ?? "")
        }
    }

    private var messageList: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 12) {
                    if uiState.messages.isEmpty {
                        emptyHint
                    }
                    ForEach(uiState.messages, id: \.id) { message in
                        MessageBubble(message: message)
                            .id(message.id)
                    }
                }
                .padding()
            }
            .onChange(of: uiState.messages.last?.text) {
                if let last = uiState.messages.last {
                    proxy.scrollTo(last.id, anchor: .bottom)
                }
            }
        }
    }

    private var emptyHint: some View {
        VStack(spacing: 12) {
            Image(systemName: "sparkles")
                .font(.largeTitle)
                .foregroundStyle(.secondary)
            Text(uiState.availability == "model_downloading"
                ? "The on-device model is still downloading. Answers may be unavailable for a bit."
                : "Ask about your training — progress on a lift, weekly volume, streaks, or body-weight trend. Everything runs on this device; your data never leaves it.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
            HStack {
                suggestionButton("How is my bench progressing?")
                suggestionButton("Am I training consistently?")
            }
        }
        .padding(.top, 48)
    }

    private func suggestionButton(_ text: String) -> some View {
        Button(text) {
            state.send(text: text)
        }
        .buttonStyle(.bordered)
        .font(.footnote)
    }

    private var inputBar: some View {
        HStack(spacing: 8) {
            TextField("Ask about your training", text: $input, axis: .vertical)
                .lineLimit(1...4)
                .textFieldStyle(.roundedBorder)
                .focused($inputFocused)
                .onSubmit(sendCurrent)
            Button(action: sendCurrent) {
                Image(systemName: "arrow.up.circle.fill")
                    .font(.title2)
            }
            .disabled(uiState.responding || input.trimmingCharacters(in: .whitespaces).isEmpty)
        }
        .padding(.horizontal)
        .padding(.vertical, 8)
        .background(.bar)
    }

    private func sendCurrent() {
        let text = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty, !uiState.responding else { return }
        input = ""
        state.send(text: text)
    }
}

private struct MessageBubble: View {
    let message: ChatMessage

    private var isUser: Bool { message.role == "user" }

    var body: some View {
        HStack {
            if isUser { Spacer(minLength: 48) }
            Group {
                if message.pending && message.text.isEmpty {
                    ProgressView()
                        .padding(12)
                } else {
                    Text(message.text)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 10)
                }
            }
            .background(
                isUser ? AnyShapeStyle(Color.accentColor.opacity(0.15)) : AnyShapeStyle(.fill),
                in: RoundedRectangle(cornerRadius: 16)
            )
            if !isUser { Spacer(minLength: 48) }
        }
    }
}
