import Foundation
import FoundationModels
import SharedUI

private final class ChatTaskHandle: ChatStreamHandle {
    private var task: Task<Void, Never>?

    init(task: Task<Void, Never>) {
        self.task = task
    }

    func cancel() {
        task?.cancel()
        task = nil
    }
}

/// On-device iOS `ChatGenerationProvider` backed by Apple's `FoundationModels`.
///
/// Reuses one `LanguageModelSession` per active conversation so multi-turn
/// follow-ups stay fast, and resets the session on `request.resetSession`,
/// instruction changes, or `.exceededContextWindowSize`. All callbacks run on
/// `@MainActor` as required by Kotlin's `ChatState`.
final class FoundationModelChatProvider: ChatGenerationProvider {
    @MainActor private var session: LanguageModelSession?
    @MainActor private var lastInstructions: String = ""
    private let sourceCollector = ChatSourceCollector()

    func availability() -> String {
        FoundationModelInsightProvider.availabilityString()
    }

    func supportsToolCalling() -> Bool {
        true
    }

    func prewarm() {
        Task { @MainActor in
            LanguageModelSession().prewarm()
        }
    }

    func generate(
        request: ChatRequest,
        onPartial: @escaping (String) -> Void,
        onComplete: @escaping (String, [ChatSourceRef]) -> Void,
        onError: @escaping (String, String) -> Void
    ) -> ChatStreamHandle {
        let task = Task { @MainActor [weak self] in
            guard let self else { return }
            guard availability() == "available" else {
                onError(
                    ChatMessage.companion.ERROR_UNAVAILABLE,
                    "Apple Intelligence is currently unavailable."
                )
                return
            }

            let needsFreshSession =
                session == nil ||
                request.resetSession ||
                lastInstructions != request.instructions

            if needsFreshSession {
                session = LanguageModelSession(
                    tools: makeFoundationChatTools(collector: sourceCollector),
                    instructions: request.instructions
                )
                lastInstructions = request.instructions
            }

            guard let activeSession = session else { return }
            _ = sourceCollector.drain()

            let prompt = Self.composePrompt(
                request: request,
                includeCompactedHistory: needsFreshSession
            )

            do {
                let stream = activeSession.streamResponse(to: prompt)
                var latestText = ""
                for try await snapshot in stream {
                    if Task.isCancelled { return }
                    latestText = snapshot.content
                    onPartial(latestText)
                }

                if Task.isCancelled { return }
                let trimmed = latestText.trimmingCharacters(in: .whitespacesAndNewlines)
                let sources = sourceCollector.drain()
                if trimmed.isEmpty {
                    onError(
                        ChatMessage.companion.ERROR_GENERATION_FAILED,
                        "Empty response from Apple Intelligence."
                    )
                } else {
                    onComplete(trimmed, sources)
                }
            } catch is CancellationError {
                _ = sourceCollector.drain()
            } catch let genError as LanguageModelSession.GenerationError {
                _ = sourceCollector.drain()
                handleGenerationError(genError, onError: onError)
            } catch {
                _ = sourceCollector.drain()
                if !Task.isCancelled {
                    onError(
                        ChatMessage.companion.ERROR_GENERATION_FAILED,
                        "On-device generation failed: \(error.localizedDescription)"
                    )
                }
            }
        }
        return ChatTaskHandle(task: task)
    }

    @MainActor
    private func handleGenerationError(
        _ error: LanguageModelSession.GenerationError,
        onError: @escaping (String, String) -> Void
    ) {
        switch error {
        case .exceededContextWindowSize:
            session = nil
            onError(
                ChatMessage.companion.ERROR_CONTEXT_TOO_LONG,
                "Context window limit reached."
            )
        case .assetsUnavailable:
            session = nil
            onError(
                ChatMessage.companion.ERROR_UNAVAILABLE,
                "Apple Intelligence model assets are unavailable."
            )
        default:
            onError(
                ChatMessage.companion.ERROR_GENERATION_FAILED,
                "On-device generation failed: \(error.localizedDescription)"
            )
        }
    }

    private static func composePrompt(
        request: ChatRequest,
        includeCompactedHistory: Bool
    ) -> String {
        var sections: [String] = []
        if includeCompactedHistory, !request.history.isEmpty {
            let transcript = request.history.map { turn in
                let speaker = turn.role == ChatMessage.companion.ROLE_USER ? "User" : "Assistant"
                return "\(speaker): \(turn.text)"
            }.joined(separator: "\n")
            sections.append("Recent conversation:\n\(transcript)")
        }

        let trimmedContext = request.preloadedContext.trimmingCharacters(in: .whitespacesAndNewlines)
        if !trimmedContext.isEmpty {
            sections.append("Context from Train Free:\n\(trimmedContext)")
        }

        sections.append(sections.isEmpty ? request.userMessage : "Question: \(request.userMessage)")
        return sections.joined(separator: "\n\n")
    }
}
