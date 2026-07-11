import Foundation
import FoundationModels
import SharedUI

/// `InsightGenerationProvider` backed by Apple's on-device Foundation Models.
/// Each generation runs in a fresh, tool-free `LanguageModelSession`:
/// instructions differ per card, and short-lived sessions keep every one-shot
/// at minimum context so the ~4K window can never accumulate across cards.
/// Model weights load process-wide, so per-session cost is negligible after
/// the first prewarm. All Kotlin callbacks are invoked on the main actor,
/// which the shared `InsightNarrationState` requires.
final class FoundationModelInsightProvider: InsightGenerationProvider {
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
            LanguageModelSession().prewarm()
        }
    }

    func generate(
        instructions: String,
        prompt: String,
        onComplete: @escaping (String) -> Void,
        onError: @escaping (String) -> Void
    ) {
        Task { @MainActor in
            do {
                let session = LanguageModelSession(instructions: instructions)
                let response = try await session.respond(to: prompt)
                onComplete(response.content)
            } catch {
                onError("On-device generation failed: \(error)")
            }
        }
    }
}
