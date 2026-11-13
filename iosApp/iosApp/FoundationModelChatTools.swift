import Foundation
import FoundationModels
import SharedUI

/// Thread-safe accumulator collecting structured [ChatSourceRef] pills across
/// tool calls made during one model turn.
final class ChatSourceCollector: @unchecked Sendable {
    private let lock = NSLock()
    private var refs: [ChatSourceRef] = []

    func append(_ newRefs: [ChatSourceRef]) {
        guard !newRefs.isEmpty else { return }
        lock.lock()
        refs.append(contentsOf: newRefs)
        lock.unlock()
    }

    func drain() -> [ChatSourceRef] {
        lock.lock()
        let snapshot = refs
        refs.removeAll()
        lock.unlock()

        var seen = Set<String>()
        return snapshot.filter { ref in
            seen.insert("\(ref.kind):\(ref.exerciseId):\(ref.epochDay):\(ref.label)").inserted
        }
    }
}

/// Builds the read-only `Tool` set backed by Kotlin's `ChatToolbox` for a session.
func makeFoundationChatTools(collector: ChatSourceCollector) -> [any Tool] {
    [
        GetTrainingSummaryTool(collector: collector),
        GetPersonalRecordsTool(collector: collector),
        GetExerciseProgressionTool(collector: collector),
        GetRecentWorkoutsTool(collector: collector),
        GetWorkoutsForDayTool(collector: collector),
        GetProgramsAndRoutinesTool(collector: collector),
        SearchExercisesTool(collector: collector),
        GetBodyMetricsAndProfileTool(collector: collector),
    ]
}

private func executeTool(
    collector: ChatSourceCollector,
    block: () async throws -> ToolOutput
) async throws -> String {
    let output = try await block()
    collector.append(output.sources)
    return output.summary
}

struct GetTrainingSummaryTool: Tool {
    let name = "get_training_summary"
    let description = "Weekly training volume trend, workout frequency/streaks, and body-part breakdown."
    let collector: ChatSourceCollector

    @Generable
    struct Arguments {
        @Guide(description: "Lookback window in weeks, 1 to 12. Defaults to 8.")
        var weeks: Int?
    }

    func call(arguments: Arguments) async throws -> String {
        try await executeTool(collector: collector) {
            try await ChatToolbox.shared.getTrainingSummary(weeks: Int32(arguments.weeks ?? 8))
        }
    }
}

struct GetPersonalRecordsTool: Tool {
    let name = "get_personal_records"
    let description = "Personal records across exercises or for one named exercise."
    let collector: ChatSourceCollector

    @Generable
    struct Arguments {
        @Guide(description: "Optional exercise name or abbreviation (e.g. bench, OHP, RDL).")
        var exerciseQuery: String?
        @Guide(description: "Max PR rows when unfiltered, 1 to 12. Defaults to 5.")
        var limit: Int?
    }

    func call(arguments: Arguments) async throws -> String {
        try await executeTool(collector: collector) {
            try await ChatToolbox.shared.getPersonalRecords(
                exerciseQuery: arguments.exerciseQuery,
                limit: Int32(arguments.limit ?? 5)
            )
        }
    }
}

struct GetExerciseProgressionTool: Tool {
    let name = "get_exercise_progression"
    let description = "Weekly progression and recent working sets for one exercise."
    let collector: ChatSourceCollector

    @Generable
    struct Arguments {
        @Guide(description: "Exercise name or abbreviation.")
        var exerciseQuery: String
        @Guide(description: "Lookback window in weeks, 2 to 24. Defaults to 12.")
        var weeks: Int?
    }

    func call(arguments: Arguments) async throws -> String {
        try await executeTool(collector: collector) {
            try await ChatToolbox.shared.getExerciseProgression(
                exerciseQuery: arguments.exerciseQuery,
                weeks: Int32(arguments.weeks ?? 12)
            )
        }
    }
}

struct GetRecentWorkoutsTool: Tool {
    let name = "get_recent_workouts"
    let description = "Recent completed workouts with date, duration, routine, and working sets."
    let collector: ChatSourceCollector

    @Generable
    struct Arguments {
        @Guide(description: "Max workouts to return, 1 to 10. Defaults to 5.")
        var limit: Int?
        @Guide(description: "Optional routine name filter.")
        var routineQuery: String?
        @Guide(description: "Optional lookback window in days.")
        var days: Int?
    }

    func call(arguments: Arguments) async throws -> String {
        try await executeTool(collector: collector) {
            try await ChatToolbox.shared.getRecentWorkouts(
                limit: Int32(arguments.limit ?? 5),
                routineQuery: arguments.routineQuery,
                days: arguments.days.map { KotlinInt(int: Int32($0)) }
            )
        }
    }
}

struct GetWorkoutsForDayTool: Tool {
    let name = "get_workouts_for_day"
    let description = "Completed workouts logged on one specific epoch day."
    let collector: ChatSourceCollector

    @Generable
    struct Arguments {
        @Guide(description: "Day index as days since 1970-01-01.")
        var epochDay: Int
    }

    func call(arguments: Arguments) async throws -> String {
        try await executeTool(collector: collector) {
            try await ChatToolbox.shared.getWorkoutsForDay(epochDay: Int64(arguments.epochDay))
        }
    }
}

struct GetProgramsAndRoutinesTool: Tool {
    let name = "get_programs_and_routines"
    let description = "Programs, routines in order, target sets/weights, and last-performed dates."
    let collector: ChatSourceCollector

    @Generable
    struct Arguments {
        @Guide(description: "Optional routine name filter.")
        var routineQuery: String?
    }

    func call(arguments: Arguments) async throws -> String {
        try await executeTool(collector: collector) {
            try await ChatToolbox.shared.getProgramsAndRoutines(routineQuery: arguments.routineQuery)
        }
    }
}

struct SearchExercisesTool: Tool {
    let name = "search_exercises"
    let description = "Search the exercise catalog by name, body part, or equipment for form or substitutes."
    let collector: ChatSourceCollector

    @Generable
    struct Arguments {
        @Guide(description: "Optional exercise name or keyword.")
        var query: String?
        @Guide(description: "Optional muscle or group such as chest, back, legs, or arms.")
        var bodyPart: String?
        @Guide(description: "Optional equipment such as barbell, dumbbell, cable, or bodyweight.")
        var equipment: String?
        @Guide(description: "Set true when exercise form instructions are needed.")
        var includeDescription: Bool?
        @Guide(description: "Max results, 1 to 12. Defaults to 8.")
        var limit: Int?
    }

    func call(arguments: Arguments) async throws -> String {
        try await executeTool(collector: collector) {
            try await ChatToolbox.shared.searchExercises(
                query: arguments.query,
                bodyPart: arguments.bodyPart,
                equipment: arguments.equipment,
                includeDescription: arguments.includeDescription ?? false,
                limit: Int32(arguments.limit ?? 8)
            )
        }
    }
}

struct GetBodyMetricsAndProfileTool: Tool {
    let name = "get_body_metrics_and_profile"
    let description = "Body weight trend, profile details, and BMI computed with the configured formula."
    let collector: ChatSourceCollector

    @Generable
    struct Arguments {
        @Guide(description: "Lookback window in days, 7 to 365. Defaults to 90.")
        var days: Int?
    }

    func call(arguments: Arguments) async throws -> String {
        try await executeTool(collector: collector) {
            try await ChatToolbox.shared.getBodyMetricsAndProfile(days: Int32(arguments.days ?? 90))
        }
    }
}
