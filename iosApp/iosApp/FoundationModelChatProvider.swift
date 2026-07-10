import Foundation
import FoundationModels
import SharedUI

/// `InsightChatProvider` backed by Apple's on-device Foundation Models.
/// Owns one `LanguageModelSession` per conversation; all statistics come
/// from the shared Kotlin tools (`InsightChatTools`), never from the model.
/// All Kotlin callbacks are invoked on the main actor, which the shared
/// `ProgressChatState` requires.
final class FoundationModelChatProvider: InsightChatProvider {
    @MainActor private var session: LanguageModelSession?

    static func availabilityString() -> String {
        switch SystemLanguageModel.default.availability {
        case .available:
            return "available"
        case .unavailable(.deviceNotEligible):
            return "device_not_eligible"
        case .unavailable(.appleIntelligenceNotEnabled):
            return "apple_intelligence_disabled"
        case .unavailable(.modelNotReady):
            return "model_downloading"
        case .unavailable:
            return "device_not_eligible"
        }
    }

    func availability() -> String {
        Self.availabilityString()
    }

    func prewarm() {
        Task { @MainActor in
            (try? await ensureSession())?.prewarm()
        }
    }

    func sendMessage(
        text: String,
        onPartial: @escaping (String) -> Void,
        onComplete: @escaping (String) -> Void,
        onError: @escaping (String) -> Void
    ) {
        Task { @MainActor in
            do {
                let session = try await ensureSession()
                onComplete(try await stream(session: session, text: text, onPartial: onPartial))
            } catch LanguageModelSession.GenerationError.exceededContextWindowSize {
                // The conversation outgrew the ~4K on-device context. Retry
                // once in a fresh session carrying only this message.
                do {
                    session = nil
                    let fresh = try await ensureSession()
                    onComplete(try await stream(session: fresh, text: text, onPartial: onPartial))
                } catch {
                    onError("This conversation is too long for the on-device model. Start a new chat.")
                }
            } catch {
                onError("The on-device model couldn't respond. Please try again.")
            }
        }
    }

    func resetSession() {
        Task { @MainActor in
            session = nil
        }
    }

    @MainActor
    private func ensureSession() async throws -> LanguageModelSession {
        if let session { return session }
        // Compact data-availability block computed by shared Kotlin; keeps
        // the model grounded without spending context on raw history.
        let dataContext = try await InsightToolExecutor.shared.chatInstructions()
        let created = LanguageModelSession(tools: insightChatTools()) {
            """
            You are a friendly strength-training assistant inside a workout \
            tracking app. Use the tools for every number or statistic; never \
            estimate or invent data. Keep answers short and concrete, and \
            note when the data is too thin to conclude anything.

            \(dataContext)
            """
        }
        session = created
        return created
    }

    @MainActor
    private func stream(
        session: LanguageModelSession,
        text: String,
        onPartial: @escaping (String) -> Void
    ) async throws -> String {
        var latest = ""
        for try await partial in session.streamResponse(to: text) {
            latest = partial.content
            onPartial(latest)
        }
        return latest
    }
}
