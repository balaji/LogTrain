import SharedUI

/// Column helpers for the per-exercise-type set rows, mirroring
/// SetColumns.kt on Compose.
enum SetFieldColumns {
    /// The measured-value columns a set row shows, in display order; weight
    /// always renders first and is not listed. Mirrors
    /// `ExerciseType.displaySetFields` in sharedLogic — keep the two in sync.
    static func fields(for type: ExerciseType) -> [SetField] {
        switch type {
        case .duration: return [.duration]
        case .distance: return [.distance]
        case .repsDuration: return [.reps, .duration]
        case .repsDistance: return [.reps, .distance]
        case .distanceDuration: return [.duration, .distance]
        default: return [.reps]
        }
    }

    /// Header label for one column. Distance follows the weight unit setting —
    /// label only, values are stored as entered, like the weight column's kg/lb.
    static func label(for field: SetField, weightUnit: WeightUnit) -> String {
        switch field {
        case .duration: return L10n.columnTime
        case .distance: return weightUnit == .imperial ? L10n.columnMi : L10n.columnKm
        default: return L10n.columnReps
        }
    }
}
