import Foundation
import FoundationModels
import SharedUI

/// FoundationModels `Tool` wrappers for the shared Kotlin insight tools.
/// Each struct is a thin, compile-time-schema (`@Generable`) declaration
/// that delegates to `InsightToolExecutor` by name — Kotlin owns execution
/// and formatting, so cards and chat always quote identical numbers.
/// Adding a tool touches the Kotlin executor plus one struct here; a future
/// Android provider can instead consume `InsightToolExecutor.specs`.
func insightChatTools() -> [any Tool] {
    [
        GetWeeklyVolumeTool(),
        GetPersonalRecordsTool(),
        GetExerciseProgressionTool(),
        GetTrainingFrequencyTool(),
        GetBodyWeightTrendTool(),
        GetProfileSummaryTool(),
    ]
}

private func callKotlin(_ name: String, _ args: [String: String] = [:]) async throws -> String {
    try await InsightToolExecutor.shared.call(name: name, args: args)
}

struct GetWeeklyVolumeTool: Tool {
    let name = "getWeeklyVolume"
    let description = "Total training volume (weight x reps) per week for recent weeks."

    @Generable
    struct Arguments {
        @Guide(description: "How many weeks of history, between 1 and 12. Defaults to 8.")
        var weeks: Int?
    }

    func call(arguments: Arguments) async throws -> String {
        try await callKotlin(name, ["weeks": arguments.weeks.map(String.init) ?? ""])
    }
}

struct GetPersonalRecordsTool: Tool {
    let name = "getPersonalRecords"
    let description = "Best set per exercise by estimated one-rep max, all time."

    @Generable
    struct Arguments {
        @Guide(description: "Exact exercise name to filter to; omit for the top records overall.")
        var exerciseName: String?
    }

    func call(arguments: Arguments) async throws -> String {
        try await callKotlin(name, ["exerciseName": arguments.exerciseName ?? ""])
    }
}

struct GetExerciseProgressionTool: Tool {
    let name = "getExerciseProgression"
    let description = "Weekly best estimated 1RM trend for one exercise over the last 12 weeks."

    @Generable
    struct Arguments {
        @Guide(description: "The exercise name, e.g. \"Barbell Bench Press\".")
        var exerciseName: String
    }

    func call(arguments: Arguments) async throws -> String {
        try await callKotlin(name, ["exerciseName": arguments.exerciseName])
    }
}

struct GetTrainingFrequencyTool: Tool {
    let name = "getTrainingFrequency"
    let description = "Workout streaks and session counts over recent weeks."

    @Generable
    struct Arguments {}

    func call(arguments: Arguments) async throws -> String {
        try await callKotlin(name)
    }
}

struct GetBodyWeightTrendTool: Tool {
    let name = "getBodyWeightTrend"
    let description = "Logged body-weight entries: latest value, 30-day change, and trend."

    @Generable
    struct Arguments {
        @Guide(description: "How many days back to look, between 7 and 180. Defaults to 90.")
        var days: Int?
    }

    func call(arguments: Arguments) async throws -> String {
        try await callKotlin(name, ["days": arguments.days.map(String.init) ?? ""])
    }
}

struct GetProfileSummaryTool: Tool {
    let name = "getProfileSummary"
    let description = "The user's profile: name, age, gender, height, current weight."

    @Generable
    struct Arguments {}

    func call(arguments: Arguments) async throws -> String {
        try await callKotlin(name)
    }
}
