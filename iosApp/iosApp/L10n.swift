import Foundation
import SharedUI

/*
 * All user-facing text for the iOS shell. Keys mirror the Compose resource
 * names in sharedUI/src/commonMain/composeResources/values/strings.xml so the
 * two platforms' translation tables stay aligned; keys that exist only on iOS
 * sit at the end of their section. `String(localized:defaultValue:)` renders
 * the English default until a String Catalog with translations is added, at
 * which point Xcode extracts these keys automatically.
 *
 * Views must not hardcode display text; add a constant here instead. Exercise
 * names and descriptions are catalog/user data and stay verbatim, but their
 * bodyPart / equipment / exerciseType keys localize via exerciseCatalogLabel.
 */
enum L10n {
    // MARK: - App
    /// Brand name, deliberately untranslated.
    static let appName = "LogTrain"
    static let appTagline = String(localized: "app_tagline", defaultValue: "Track your workouts, all on your device.")

    // MARK: - Common actions
    static let cancel = String(localized: "cancel", defaultValue: "Cancel")
    static let delete = String(localized: "delete", defaultValue: "Delete")
    static let save = String(localized: "save", defaultValue: "Save")
    static let edit = String(localized: "edit", defaultValue: "Edit")
    static let add = String(localized: "add", defaultValue: "Add")
    static let ok = String(localized: "ok", defaultValue: "OK")
    static let yes = String(localized: "yes", defaultValue: "Yes")
    static let no = String(localized: "no", defaultValue: "No")
    static let collapse = String(localized: "collapse", defaultValue: "Collapse")
    static let expand = String(localized: "expand", defaultValue: "Expand")
    static let create = String(localized: "create", defaultValue: "Create")
    static let name = String(localized: "name", defaultValue: "Name")
    static let close = String(localized: "close", defaultValue: "Close")
    static let somethingWentWrong = String(localized: "something_went_wrong", defaultValue: "Something went wrong")
    // iOS-only
    static let done = String(localized: "done", defaultValue: "Done")
    static let select = String(localized: "select", defaultValue: "Select")
    static let any = String(localized: "any", defaultValue: "Any")
    static let rename = String(localized: "rename", defaultValue: "Rename")

    // MARK: - Tabs
    static let tabExercises = String(localized: "tab_exercises", defaultValue: "Exercises")
    static let tabWorkouts = String(localized: "tab_workouts", defaultValue: "Workouts")
    static let tabHistory = String(localized: "tab_history", defaultValue: "History")
    static let tabProgress = String(localized: "tab_progress", defaultValue: "Progress")

    // MARK: - Active session / workout dialogs
    static let discardWorkoutTitle = String(localized: "discard_workout_title", defaultValue: "Discard workout?")
    static let discardWorkoutMessage = String(localized: "discard_workout_message", defaultValue: "This workout and all logged sets will be deleted.")
    static let discard = String(localized: "discard", defaultValue: "Discard")
    static let discardWorkout = String(localized: "discard_workout", defaultValue: "Discard workout")
    static let workoutInProgressTitle = String(localized: "workout_in_progress_title", defaultValue: "A Workout is in progress")
    static let nothingToSaveTitle = String(localized: "nothing_to_save_title", defaultValue: "Nothing to save")
    static let nothingToSaveMessage = String(localized: "nothing_to_save_message", defaultValue: "There are no completed sets to save. Discard?")
    static func restTimerCountdown(_ remaining: String) -> String {
        String(localized: "rest_timer_countdown", defaultValue: "Rest · \(remaining)")
    }
    static let restOverTitle = String(localized: "rest_over_title", defaultValue: "Rest is over")
    static let restOverMessage = String(localized: "rest_over_message", defaultValue: "Time for your next set.")

    // MARK: - Workout session / routine editor
    static let addExercise = String(localized: "add_exercise_cd", defaultValue: "Add Exercise")
    static let reorderExercises = String(localized: "reorder_exercises", defaultValue: "Reorder exercises")
    static let columnSet = String(localized: "column_set", defaultValue: "SET")
    static let columnPrevious = String(localized: "column_previous", defaultValue: "PREVIOUS")
    static let columnKg = String(localized: "column_kg", defaultValue: "KG")
    static let columnLb = String(localized: "column_lb", defaultValue: "LB")
    static let columnReps = String(localized: "column_reps", defaultValue: "REPS")
    static let columnTime = String(localized: "column_time", defaultValue: "TIME")
    static let columnKm = String(localized: "column_km", defaultValue: "KM")
    static let columnMi = String(localized: "column_mi", defaultValue: "MI")
    static let durationPlaceholder = String(localized: "duration_placeholder", defaultValue: "h:mm")
    static let warmupMarker = String(localized: "warmup_marker", defaultValue: "W")
    static let dropsetMarker = String(localized: "dropset_marker", defaultValue: "D")
    static let setType = String(localized: "set_type", defaultValue: "Set type")
    static let setTypeNormal = String(localized: "set_type_normal", defaultValue: "Normal")
    static let warmUp = String(localized: "warm_up", defaultValue: "Warm-up")
    static let dropSet = String(localized: "drop_set", defaultValue: "Drop set")
    static let plateBar = String(localized: "plate_bar", defaultValue: "Bar")
    static let plateUseNumeric = String(localized: "plate_use_numeric", defaultValue: "123")
    static let plateUsePlates = String(localized: "plate_use_plates", defaultValue: "Plates")
    static let platePerSide = String(localized: "plate_per_side", defaultValue: "per side")
    static let deleteExercise = String(localized: "delete_exercise", defaultValue: "Delete Exercise")
    static let routineNoExercises = String(localized: "routine_no_exercises", defaultValue: "No exercises yet. Tap Edit to add exercises.")
    static func setsByExercise(_ count: Int, _ exercise: String) -> String {
        String(localized: "sets_by_exercise", defaultValue: "\(count) x \(exercise)")
    }
    // iOS-only
    static let addSet = String(localized: "add_set", defaultValue: "Add Set")

    // MARK: - Programs
    static let programsTitle = String(localized: "programs_title", defaultValue: "Programs")
    static let emptyWorkoutTitle = String(localized: "empty_workout_title", defaultValue: "Empty workout")
    static let emptyWorkout = String(localized: "empty_workout", defaultValue: "Empty Workout")
    static let chooseProgram = String(localized: "choose_program", defaultValue: "Choose Program")
    static let newProgram = String(localized: "new_program", defaultValue: "New program")
    static let renameProgram = String(localized: "rename_program", defaultValue: "Rename program")
    static let deleteProgramTitle = String(localized: "delete_program_title", defaultValue: "Delete program?")
    static func deleteProgramMessage(_ name: String) -> String {
        String(localized: "delete_program_message", defaultValue: "\"\(name)\" and all of its routines will be deleted.")
    }
    static let newRoutine = String(localized: "new_routine", defaultValue: "New routine")
    static let renameRoutine = String(localized: "rename_routine", defaultValue: "Rename routine")
    static let deleteRoutineTitle = String(localized: "delete_routine_title", defaultValue: "Delete routine?")
    static func deleteRoutineMessage(_ name: String) -> String {
        String(localized: "delete_routine_message", defaultValue: "\"\(name)\" will be deleted.")
    }
    static let addSampleTitle = String(localized: "add_sample_title", defaultValue: "Add to your programs?")
    static func addSampleMessage(_ name: String) -> String {
        String(localized: "add_sample_message", defaultValue: "\"\(name)\" will be copied into your programs, where you can edit it.")
    }
    static let startEmptyWorkout = String(localized: "start_empty_workout", defaultValue: "Start Empty Workout")
    static let myPrograms = String(localized: "my_programs", defaultValue: "My Programs")
    static let noProgramsYet = String(localized: "no_programs_yet", defaultValue: "No programs yet. Tap + to create one.")
    static let noRoutinesYet = String(localized: "no_routines_yet", defaultValue: "No routines yet. Tap + to add one.")
    static let samplePrograms = String(localized: "sample_programs", defaultValue: "Sample Programs")
    static let addRoutine = String(localized: "add_routine", defaultValue: "Add Routine")
    static func sampleDaysSummary(_ days: Int, _ summary: String) -> String {
        String(localized: "sample_days_summary", defaultValue: "\(days) days a week · \(summary)")
    }
    static let copyToMyPrograms = String(localized: "copy_to_my_programs", defaultValue: "Copy to my programs")
    static func exerciseCount(_ count: Int) -> String {
        String(localized: "exercise_count", defaultValue: "\(count) exercises")
    }

    // MARK: - Sample programs (ids from sharedLogic's SampleProgram)
    static let sampleFullBody3DayName = String(localized: "sample_full_body_3day_name", defaultValue: "3 Day Full Body")
    static let sampleFullBody3DaySummary = String(localized: "sample_full_body_3day_summary", defaultValue: "Three full-body sessions a week. The simplest way to start.")
    static let sampleUpperLower4DayName = String(localized: "sample_upper_lower_4day_name", defaultValue: "4 Day Upper Lower")
    static let sampleUpperLower4DaySummary = String(localized: "sample_upper_lower_4day_summary", defaultValue: "Two upper-body and two lower-body days, alternating through the week.")
    static let samplePushPullLegs5DayName = String(localized: "sample_push_pull_legs_5day_name", defaultValue: "5 Day Push Pull Legs")
    static let samplePushPullLegs5DaySummary = String(localized: "sample_push_pull_legs_5day_summary", defaultValue: "A push, pull and legs split, then an upper and lower day to finish the week.")
    static let sampleRoutineFullBodyA = String(localized: "sample_routine_full_body_a", defaultValue: "Full Body A")
    static let sampleRoutineFullBodyB = String(localized: "sample_routine_full_body_b", defaultValue: "Full Body B")
    static let sampleRoutineFullBodyC = String(localized: "sample_routine_full_body_c", defaultValue: "Full Body C")
    static let sampleRoutineUpperA = String(localized: "sample_routine_upper_a", defaultValue: "Upper A")
    static let sampleRoutineLowerA = String(localized: "sample_routine_lower_a", defaultValue: "Lower A")
    static let sampleRoutineUpperB = String(localized: "sample_routine_upper_b", defaultValue: "Upper B")
    static let sampleRoutineLowerB = String(localized: "sample_routine_lower_b", defaultValue: "Lower B")
    static let sampleRoutinePush = String(localized: "sample_routine_push", defaultValue: "Push")
    static let sampleRoutinePull = String(localized: "sample_routine_pull", defaultValue: "Pull")
    static let sampleRoutineLegs = String(localized: "sample_routine_legs", defaultValue: "Legs")
    static let sampleRoutineUpper = String(localized: "sample_routine_upper", defaultValue: "Upper")
    static let sampleRoutineLower = String(localized: "sample_routine_lower", defaultValue: "Lower")

    // MARK: - Exercises
    static let addNew = String(localized: "add_new", defaultValue: "Add New")
    static let searchExercises = String(localized: "search_exercises", defaultValue: "Search exercises")
    static let bodyPart = String(localized: "body_part", defaultValue: "Body part")
    static let equipment = String(localized: "equipment", defaultValue: "Equipment")
    static let noExercisesMatch = String(localized: "no_exercises_match", defaultValue: "No exercises match your filters.")
    static let myExercises = String(localized: "my_exercises", defaultValue: "My Exercises")
    static let allExercises = String(localized: "all_exercises", defaultValue: "All Exercises")
    static let deleteExerciseTitle = String(localized: "delete_exercise_title", defaultValue: "Delete exercise?")
    static func deleteExerciseMessage(_ name: String) -> String {
        String(localized: "delete_exercise_message", defaultValue: "\"\(name)\" will be permanently deleted.")
    }
    static func exerciseInUse(_ name: String) -> String {
        String(localized: "exercise_in_use", defaultValue: "\"\(name)\" is used by a routine or workout history and can't be deleted.")
    }
    static let newExercise = String(localized: "new_exercise", defaultValue: "New Exercise")
    static let searchInGoogle = String(localized: "search_in_google", defaultValue: "Search in Google")
    static let exerciseType = String(localized: "exercise_type", defaultValue: "Type")
    static let nameRequired = String(localized: "name_required", defaultValue: "Name is required")
    static let duplicateExerciseName = String(localized: "duplicate_exercise_name", defaultValue: "An exercise with this name already exists")
    static func describeExerciseQuery(_ name: String) -> String {
        String(localized: "describe_exercise_query", defaultValue: "Describe the exercise \(name)")
    }
    static let exerciseHistorySection = String(localized: "exercise_history_section", defaultValue: "12-week history")
    static let exerciseHistoryNoData = String(localized: "exercise_history_no_data", defaultValue: "Log some sets to see history here.")
    static let exerciseHistoryMetric1rm = String(localized: "exercise_history_metric_1rm", defaultValue: "1RM")
    static let exerciseHistoryMetricVolume = String(localized: "exercise_history_metric_volume", defaultValue: "Volume")
    static let exerciseHistoryMetricDuration = String(localized: "exercise_history_metric_duration", defaultValue: "Duration")
    static let exerciseHistoryMetricDistance = String(localized: "exercise_history_metric_distance", defaultValue: "Distance")
    static func exerciseSetCount(_ count: Int) -> String {
        count == 1
            ? String(localized: "exercise_set_count_one", defaultValue: "1 set")
            : String(localized: "exercise_set_count_other", defaultValue: "\(count) sets")
    }
    // iOS-only
    static let cannotDelete = String(localized: "cannot_delete", defaultValue: "Can't delete")
    /// Navigation-title fallbacks when no user data is available yet.
    static let exercise = String(localized: "exercise", defaultValue: "Exercise")
    static let routine = String(localized: "routine", defaultValue: "Routine")

    // MARK: - Profile
    static let profile = String(localized: "profile", defaultValue: "Profile")
    static let age = String(localized: "age", defaultValue: "Age")
    static let gender = String(localized: "gender", defaultValue: "Gender")
    static let weight = String(localized: "weight", defaultValue: "Weight")
    static let height = String(localized: "height", defaultValue: "Height")
    static func weightKgValue(_ value: String) -> String {
        String(localized: "weight_kg_value", defaultValue: "\(value) kg")
    }
    static func heightCmValue(_ value: String) -> String {
        String(localized: "height_cm_value", defaultValue: "\(value) cm")
    }
    static func weightLbValue(_ value: String) -> String {
        String(localized: "weight_lb_value", defaultValue: "\(value) lb")
    }
    static func heightFtInValue(_ feet: String, _ inches: String) -> String {
        String(localized: "height_ft_in_value", defaultValue: "\(feet) ft \(inches) in")
    }
    static let weightKgLabel = String(localized: "weight_kg_label", defaultValue: "Weight (kg)")
    static let heightCmLabel = String(localized: "height_cm_label", defaultValue: "Height (cm)")
    static let back = String(localized: "back", defaultValue: "Back")
    static let genderMale = String(localized: "gender_male", defaultValue: "Male")
    static let genderFemale = String(localized: "gender_female", defaultValue: "Female")
    static let genderOther = String(localized: "gender_other", defaultValue: "Other")
    static let genderPreferNotToSay = String(localized: "gender_prefer_not_to_say", defaultValue: "Prefer not to say")
    /// Display label for a language-neutral gender key stored in
    /// profile.gender (GENDER_KEYS in the shared ProfileFormState); mirrors
    /// sharedUI's genderLabel. Unknown values are shown verbatim.
    static func genderLabel(_ key: String) -> String {
        switch key {
        case "male": return genderMale
        case "female": return genderFemale
        case "other": return genderOther
        case "prefer_not_to_say": return genderPreferNotToSay
        default: return key
        }
    }
    static let createAProfile = String(localized: "create_a_profile", defaultValue: "Create a Profile")
    static let createProfile = String(localized: "create_profile", defaultValue: "Create Profile")

    // MARK: - Profile validation
    static func profileNameTooLong(_ max: Int) -> String {
        String(localized: "profile_name_too_long", defaultValue: "Name must be at most \(max) characters")
    }
    static let profileNameLettersOnly = String(localized: "profile_name_letters_only", defaultValue: "Name must contain only letters")
    static func profileAgeRange(_ min: Int, _ max: Int) -> String {
        String(localized: "profile_age_range", defaultValue: "Age must be between \(min) and \(max)")
    }
    // iOS-only
    static let profileNameInvalid = String(localized: "profile_name_invalid", defaultValue: "Invalid name")

    // MARK: - Settings
    static let settings = String(localized: "settings", defaultValue: "Settings")
    static let sendNotifications = String(localized: "send_notifications", defaultValue: "Send notifications")
    static let notificationsDeniedTitle = String(localized: "notifications_denied_title", defaultValue: "Notifications are off")
    static let notificationsDeniedMessage = String(localized: "notifications_denied_message", defaultValue: "LogTrain is not allowed to send notifications. Allow notifications for LogTrain in system settings to turn this on.")
    static let openSettings = String(localized: "open_settings", defaultValue: "Open settings")
    static let weekStartsOn = String(localized: "week_starts_on", defaultValue: "Week starts on")
    static let appearance = String(localized: "appearance", defaultValue: "Appearance")
    static let weightUnits = String(localized: "weight_units", defaultValue: "Weight units")
    static let appearanceLight = String(localized: "appearance_light", defaultValue: "Light")
    static let appearanceDark = String(localized: "appearance_dark", defaultValue: "Dark")
    static let appearanceSystem = String(localized: "appearance_system", defaultValue: "System")
    static let unitsMetric = String(localized: "units_metric", defaultValue: "Metric")
    static let unitsImperial = String(localized: "units_imperial", defaultValue: "Imperial")
    static let restTimerDuration = String(localized: "rest_timer_duration", defaultValue: "Rest timer")
    static let restTimerSound = String(localized: "rest_timer_sound", defaultValue: "Rest timer sound")
    static let restSoundNone = String(localized: "rest_sound_none", defaultValue: "None")
    static let restSoundDefault = String(localized: "rest_sound_default", defaultValue: "Default")
    static let restSoundProxima = String(localized: "rest_sound_proxima", defaultValue: "Proxima")
    static let restSoundMira = String(localized: "rest_sound_mira", defaultValue: "Mira")
    static let restSoundAntares = String(localized: "rest_sound_antares", defaultValue: "Antares")
    static let restSoundAltair = String(localized: "rest_sound_altair", defaultValue: "Altair")
    static let restSoundDeneb = String(localized: "rest_sound_deneb", defaultValue: "Deneb")
    static func restTimerSoundLabel(_ sound: RestTimerSound) -> String {
        switch sound {
        case .none: return restSoundNone
        case .`default`: return restSoundDefault
        case .proxima: return restSoundProxima
        case .mira: return restSoundMira
        case .antares: return restSoundAntares
        case .altair: return restSoundAltair
        case .deneb: return restSoundDeneb
        }
    }
    // iOS-only: the week-start picker shows full weekday names
    static let weekdaySunday = String(localized: "weekday_sunday", defaultValue: "Sunday")
    static let weekdayMonday = String(localized: "weekday_monday", defaultValue: "Monday")

    // MARK: - Developer tools
    static let developer = String(localized: "developer", defaultValue: "Developer")
    static let generate = String(localized: "generate", defaultValue: "Generate")
    static let generateTestDataTitle = String(localized: "generate_test_data_title", defaultValue: "Generate test data")
    static let generateTestDataMessage = String(localized: "generate_test_data_message", defaultValue: "Fills the calendar with roughly three workouts a week and regular weigh-ins, ending today.")
    static let daysOfHistory = String(localized: "days_of_history", defaultValue: "Days of history")
    static let deleteTestDataTitle = String(localized: "delete_test_data_title", defaultValue: "Delete test data")
    static func deleteTestDataMessage(_ count: Int) -> String {
        String(localized: "delete_test_data_message", defaultValue: "Removes all \(count) generated workouts and their weigh-ins. Your own logged data is untouched.")
    }
    // iOS-only: menu button that opens the generate alert
    static let generateTestDataButton = String(localized: "generate_test_data_button", defaultValue: "Generate test data…")

    // MARK: - Progress
    static let noProgressYet = String(localized: "no_progress_yet", defaultValue: "No progress yet")
    static let progressEmptyHint = String(localized: "progress_empty_hint", defaultValue: "Insights appear after your first completed workout or logged weight.")
    static let logBodyWeight = String(localized: "log_body_weight", defaultValue: "Log body weight")
    static let bodyWeight = String(localized: "body_weight", defaultValue: "Body weight")
    static let notLoggedYet = String(localized: "not_logged_yet", defaultValue: "Not logged yet")
    static let logWeight = String(localized: "log_weight", defaultValue: "Log weight")

    // MARK: - Calendar / history
    static let showCalendar = String(localized: "show_calendar", defaultValue: "Show calendar")
    static let showWorkoutHistory = String(localized: "show_workout_history", defaultValue: "Show workout history")
    static let weekNumberAbbrev = String(localized: "week_number_abbrev", defaultValue: "Wk")
    static let noCompletedWorkouts = String(localized: "no_completed_workouts", defaultValue: "No completed workouts")

    // MARK: - Day detail
    static let workout = String(localized: "workout", defaultValue: "Workout")
    static func setNumber(_ number: Int) -> String {
        String(localized: "set_number", defaultValue: "Set \(number)")
    }
    static func repsValue(_ reps: String) -> String {
        String(localized: "reps_value", defaultValue: "\(reps) reps")
    }
    static func durationValue(_ duration: String) -> String {
        String(localized: "duration_value", defaultValue: "\(duration) h")
    }
    static func distanceKmValue(_ distance: String) -> String {
        String(localized: "distance_km_value", defaultValue: "\(distance) km")
    }

    // MARK: - Weight units
    static let unitKg = String(localized: "unit_kg", defaultValue: "kg")
    static let unitLb = String(localized: "unit_lb", defaultValue: "lb")
    static let unitCm = String(localized: "unit_cm", defaultValue: "cm")
    static let unitFt = String(localized: "unit_ft", defaultValue: "ft")
    static let unitIn = String(localized: "unit_in", defaultValue: "in")

    // MARK: - Dates
    static let monthsShort: [String] = [
        String(localized: "month_jan_short", defaultValue: "Jan"),
        String(localized: "month_feb_short", defaultValue: "Feb"),
        String(localized: "month_mar_short", defaultValue: "Mar"),
        String(localized: "month_apr_short", defaultValue: "Apr"),
        String(localized: "month_may_short", defaultValue: "May"),
        String(localized: "month_jun_short", defaultValue: "Jun"),
        String(localized: "month_jul_short", defaultValue: "Jul"),
        String(localized: "month_aug_short", defaultValue: "Aug"),
        String(localized: "month_sep_short", defaultValue: "Sep"),
        String(localized: "month_oct_short", defaultValue: "Oct"),
        String(localized: "month_nov_short", defaultValue: "Nov"),
        String(localized: "month_dec_short", defaultValue: "Dec"),
    ]
    /// 1-based month, mirroring LogicLabels.monthAbbreviation.
    static func monthAbbreviation(_ monthOfYear: Int) -> String {
        monthsShort[max(1, min(12, monthOfYear)) - 1]
    }
    static func dayTitle(_ month: String, _ day: String, _ year: String) -> String {
        String(localized: "day_title", defaultValue: "\(month) \(day), \(year)")
    }
    static let weekdaySunShort = String(localized: "weekday_sun_short", defaultValue: "Sun")
    static let weekdayMonShort = String(localized: "weekday_mon_short", defaultValue: "Mon")
    static let weekdayTueShort = String(localized: "weekday_tue_short", defaultValue: "Tue")
    static let weekdayWedShort = String(localized: "weekday_wed_short", defaultValue: "Wed")
    static let weekdayThuShort = String(localized: "weekday_thu_short", defaultValue: "Thu")
    static let weekdayFriShort = String(localized: "weekday_fri_short", defaultValue: "Fri")
    static let weekdaySatShort = String(localized: "weekday_sat_short", defaultValue: "Sat")
    static let weekdaysShortSundayFirst: [String] = [
        weekdaySunShort, weekdayMonShort, weekdayTueShort, weekdayWedShort,
        weekdayThuShort, weekdayFriShort, weekdaySatShort,
    ]
    static let weekdaysShortMondayFirst: [String] = [
        weekdayMonShort, weekdayTueShort, weekdayWedShort, weekdayThuShort,
        weekdayFriShort, weekdaySatShort, weekdaySunShort,
    ]

    // MARK: - Insight cards (args are pre-formatted numbers from sharedLogic)
    static let insightTitleVolume = String(localized: "insight_title_volume", defaultValue: "Weekly volume")
    static let insightTitleFrequency = String(localized: "insight_title_frequency", defaultValue: "Consistency")
    static let insightTitleBodyParts = String(localized: "insight_title_body_parts", defaultValue: "Body part focus")
    static func insightBodyPartShare(_ sets: String, _ percent: String) -> String {
        String(localized: "insight_body_part_share", defaultValue: "\(sets) sets · \(percent)%")
    }
    static func insightVolumeThisWeek(_ volume: String, _ unit: String) -> String {
        String(localized: "insight_volume_this_week", defaultValue: "\(volume) \(unit) this week")
    }
    static func insightStreakWeeks(_ weeks: String) -> String {
        String(localized: "insight_streak_weeks", defaultValue: "\(weeks)-week streak")
    }
    static let insightNoStreak = String(localized: "insight_no_streak", defaultValue: "No active streak")
    static func insightPlateauAt(_ weight: String, _ unit: String) -> String {
        String(localized: "insight_plateau_at", defaultValue: "Plateau at ~\(weight) \(unit)")
    }
    static func insightEst1Rm(_ weight: String, _ unit: String) -> String {
        String(localized: "insight_est_1rm", defaultValue: "Est. 1RM \(weight) \(unit)")
    }
    static func insightLatestBodyWeight(_ weight: String, _ unit: String) -> String {
        String(localized: "insight_latest_body_weight", defaultValue: "\(weight) \(unit)")
    }
    static func insightPctVs4WeekAvg(_ percent: String) -> String {
        String(localized: "insight_pct_vs_4_week_avg", defaultValue: "\(percent)% vs 4-week average")
    }
    static func insightSetsInSessions(_ sets: String, _ sessions: String) -> String {
        String(localized: "insight_sets_in_sessions", defaultValue: "\(sets) sets in \(sessions) sessions")
    }
    static func insightWorkouts30Days(_ workouts: String) -> String {
        String(localized: "insight_workouts_30_days", defaultValue: "\(workouts) workouts in the last 30 days")
    }
    static func insightWorkouts30DaysLongest(_ workouts: String, _ weeks: String) -> String {
        String(localized: "insight_workouts_30_days_longest", defaultValue: "\(workouts) workouts in the last 30 days · longest streak \(weeks) weeks")
    }
    static func insightFlatAcrossWeeks(_ weeks: String) -> String {
        String(localized: "insight_flat_across_weeks", defaultValue: "Est. 1RM flat across \(weeks) weeks")
    }
    static func insightSlopePerWeek(_ slope: String, _ unit: String, _ weeks: String) -> String {
        String(localized: "insight_slope_per_week", defaultValue: "\(slope) \(unit)/week over \(weeks) weeks")
    }
    static func insightChange30Days(_ change: String, _ unit: String) -> String {
        String(localized: "insight_change_30_days", defaultValue: "\(change) \(unit) in 30 days")
    }
    static let insightLogRegularly = String(localized: "insight_log_regularly", defaultValue: "Log regularly to see your trend")
    static let insightAddComparison = String(localized: "insight_add_comparison", defaultValue: "Add comparison")
    static let insightRemoveCard = String(localized: "insight_remove_card", defaultValue: "Remove card")
    static let insightAxisPctChange = String(localized: "insight_axis_pct_change", defaultValue: "% change")
    static func insightSeriesSlope(_ slope: String, _ unit: String) -> String {
        String(localized: "insight_series_slope", defaultValue: "\(slope) \(unit)/week")
    }
    // iOS-only: shown while the on-device model writes a card narration
    static let insightNarrationLoading = String(localized: "insight_narration_loading", defaultValue: "Reading your training data and writing a note about this trend.")

    // MARK: - 1RM calculator
    static let oneRmCalculator = String(localized: "one_rm_calculator", defaultValue: "1 Rep Max Calculator")
    static let reps = String(localized: "reps", defaultValue: "Reps")
    static let calculate = String(localized: "calculate", defaultValue: "Calculate")
    static let findOutMore = String(localized: "find_out_more", defaultValue: "Find out more")
    static let oneRmSelectedFormulaHint = String(localized: "one_rm_selected_formula_hint", defaultValue: "Tap a formula to make it the default for all insights.")
    static let oneRmHighRepsHint = String(localized: "one_rm_high_reps_hint", defaultValue: "Above 15 reps these estimates get much less accurate — managing that many reps is a good sign you can try a heavier weight.")
    static func oneRmSearchQuery(_ formula: String) -> String {
        String(localized: "one_rm_search_query", defaultValue: "\(formula) one rep max formula")
    }
    static let oneRmFormulaEpley = String(localized: "one_rm_formula_epley", defaultValue: "Epley")
    static let oneRmFormulaBrzycki = String(localized: "one_rm_formula_brzycki", defaultValue: "Brzycki")
    static let oneRmFormulaLombardi = String(localized: "one_rm_formula_lombardi", defaultValue: "Lombardi")
    static let oneRmFormulaOconner = String(localized: "one_rm_formula_oconner", defaultValue: "O'Conner")
    static let oneRmInfoEpley = String(localized: "one_rm_info_epley", defaultValue: "The Epley formula, published by strength coach Boyd Epley in 1985, is the most widely used one-rep-max estimator in training apps, spreadsheets, and gym folklore. It is a simple linear model: every rep you perform beyond the first adds one thirtieth of the bar weight to the estimate. That linearity makes it easy to reason about — double the extra reps and you double the added credit — and it behaves predictably across the whole rep range. Epley and Brzycki, the two most popular formulas, agree exactly at 10 reps; below 10 Epley is slightly more conservative than Brzycki, and above 10 it becomes the more generous of the two. Like all estimators it is most trustworthy when the set is taken close to failure with moderate reps, roughly 2 to 10, where the relationship between reps and load is close to linear for most lifters. Because it neither collapses at high reps nor exaggerates low-rep sets, it is a sensible default when you have no strong reason to prefer another model, which is exactly why so much software ships with it. LogTrain uses Epley as its default formula.")
    static let oneRmInfoBrzycki = String(localized: "one_rm_info_brzycki", defaultValue: "Matt Brzycki, a strength coach and author at Princeton University, published this formula in 1993 and it has since become the estimator most often cited in academic validation studies. Unlike Epley's straight line, Brzycki is hyperbolic: the estimate is the weight multiplied by 36 divided by (37 minus reps). Each additional rep therefore counts a little more than the one before it, and the curve accelerates as reps climb. In the low-rep range, up to about 10 reps, Brzycki tracks measured maxes closely and research generally rates it among the most accurate of the classic formulas there. Beyond 10 reps the denominator shrinks quickly and the estimate grows aggressively, so treat high-rep predictions with skepticism — a 20-rep set of a light weight will produce a flattering number that few lifters could actually lift for a single. Brzycki and Epley agree exactly at 10 reps, with Brzycki slightly higher below that point. Choose it if you usually test with heavy sets of 3 to 8 reps and want the estimator with the strongest research pedigree in that range. LogTrain clamps the rep count at 36 so the math stays finite.")
    static let oneRmInfoLombardi = String(localized: "one_rm_info_lombardi", defaultValue: "The Lombardi formula takes a completely different mathematical shape from its linear and hyperbolic siblings: it multiplies the weight by the rep count raised to the power of 0.1. A power law with such a small exponent produces the gentlest curve of any common estimator — the estimate rises quickly over the first few reps and then flattens dramatically, so a 10-rep set is credited only about 26 percent above the bar weight and a 20-rep set barely more. That restraint is the whole point. Where Epley and especially Brzycki inflate high-rep sets into eye-catching maxes, Lombardi stays skeptical, which makes it the best fit for lifters who log endurance-style sets of 12, 15, or 20 reps and want a number they could plausibly hit on a heavy single. The trade-off is the mirror image: for genuinely heavy doubles and triples Lombardi gives the least credit of the four, so pure strength athletes testing at low reps may find it undersells them. Pick Lombardi if your training lives in the higher rep ranges and you would rather be pleasantly surprised at the bar than let down by it.")
    static let oneRmInfoOconner = String(localized: "one_rm_info_oconner", defaultValue: "Published by O'Conner and colleagues in 1989, this formula is the cautious sibling of Epley. It uses the same linear idea — every rep beyond the first adds a fixed fraction of the bar weight — but the fraction is 1/40 instead of Epley's 1/30, so each rep earns 25 percent less credit. The result is a deliberately conservative estimate across the entire rep range: at 5 reps O'Conner predicts 12.5 percent over the bar weight where Epley predicts nearly 17 percent. That restraint is valuable in situations where overestimating a max is riskier than underestimating it. Coaches programming percentages for beginners often prefer it, because a novice's estimated max sets their working weights for weeks, and an inflated number bakes in loads they cannot recover from. It also suits older lifters, rehab settings, and anyone returning from a layoff, where a margin of safety matters more than bragging rights. Because it is linear, it remains stable and predictable at higher reps rather than accelerating like Brzycki. Choose O'Conner when you want your training numbers anchored to what you can almost certainly lift, not what you might lift on a perfect day.")

    // MARK: - Tools (Profile > Tools)
    static let tools = String(localized: "tools", defaultValue: "Tools")
    static let bmiCalculator = String(localized: "bmi_calculator", defaultValue: "BMI Calculator")
    static func bmiResult(_ bmi: String, _ category: String) -> String {
        String(localized: "bmi_result", defaultValue: "\(bmi) BMI — \(category)")
    }
    static let bmiCategoryUnderweight = String(localized: "bmi_category_underweight", defaultValue: "Underweight")
    static let bmiCategoryNormal = String(localized: "bmi_category_normal", defaultValue: "Normal")
    static let bmiCategoryOverweight = String(localized: "bmi_category_overweight", defaultValue: "Overweight")
    static let bmiCategoryObese = String(localized: "bmi_category_obese", defaultValue: "Obese")
    static let bmiFormulaHeading = String(localized: "bmi_formula_heading", defaultValue: "Formula")
    static let bmiClassificationHeading = String(localized: "bmi_classification_heading", defaultValue: "Classification")
    static let bmiSelectedFormulaHint = String(localized: "bmi_selected_formula_hint", defaultValue: "Tap a formula or classification to make it the default wherever BMI is shown.")
    static let bmiFormulaStandard = String(localized: "bmi_formula_standard", defaultValue: "Standard")
    static let bmiFormulaTrefethen = String(localized: "bmi_formula_trefethen", defaultValue: "New BMI (Trefethen)")
    static let bmiClassificationWho = String(localized: "bmi_classification_who", defaultValue: "WHO international")
    static let bmiClassificationAsianPacific = String(localized: "bmi_classification_asian_pacific", defaultValue: "WHO Asian-Pacific")
    static func bmiSearchQuery(_ name: String) -> String {
        String(localized: "bmi_search_query", defaultValue: "\(name) BMI")
    }
    static let bmiInfoStandard = String(localized: "bmi_info_standard", defaultValue: "The standard body mass index divides weight in kilograms by height in meters squared. The Belgian statistician Adolphe Quetelet devised the ratio in the 1830s while searching for a simple description of the average man, and in the 1970s the physiologist Ancel Keys confirmed it as the best of the easy height-weight indexes and gave it its modern name. It is the formula behind virtually every BMI chart, medical guideline, and study you will encounter, so if you want a number that is directly comparable with what a doctor or a paper reports, this is the one to use. Its weaknesses are equally well documented. Squaring height flatters tall people and penalizes short ones slightly, because real bodies do not scale in only two dimensions. More importantly for lifters, it cannot tell muscle from fat: a muscular person can read as overweight while carrying very little fat. Treat it as a population screening tool rather than a verdict on an individual physique. LogTrain uses Standard as its default formula.")
    static let bmiInfoTrefethen = String(localized: "bmi_info_trefethen", defaultValue: "Nick Trefethen, professor of numerical analysis at the University of Oxford, proposed this revision in a 2013 letter to The Economist. His observation was that the standard index scales weight against height squared, as if people were two-dimensional, while true three-dimensional scaling would use height cubed; real bodies grow somewhere in between, because taller people are not simply scaled-up copies of shorter ones. He split the difference with an exponent of 2.5 and multiplied by 1.3 so that adults of average height get roughly the same number under either formula. The effect is a modest correction at the extremes: short people score a little higher than the standard index says, and tall people score a little lower — undoing the standard formula's tendency to flatter the tall and penalize the short. The values land on the same scale, so the usual category cutoffs still apply. The New BMI has not been adopted by any health body and remains a mathematician's refinement rather than a clinical standard, but it is a reasonable choice if you are notably tall or short and want a fairer number.")
    static let bmiInfoWho = String(localized: "bmi_info_who", defaultValue: "The World Health Organization's international classification is the one behind the familiar BMI chart: below 18.5 is underweight, 18.5 to just under 25 is the normal range, 25 to just under 30 is overweight, and 30 or above is obese. The bands trace back to actuarial and epidemiological work linking body mass to mortality, and the WHO formalized them for adults in the 1990s as simple, memorable action points where health risks begin to climb. Because nearly every study, guideline, and medical record uses these cutoffs, they are the right choice when you want your category to match what a clinician or published research would say. Their main caveat is that risk does not rise at the same BMI in every population: the thresholds were derived largely from European data, and for some groups disease risk climbs noticeably earlier — which is exactly what the Asian-Pacific classification addresses. The categories also inherit BMI's blindness to body composition, so a muscular lifter should read them loosely. LogTrain uses the WHO international classification by default.")
    static let bmiInfoAsianPacific = String(localized: "bmi_info_asian_pacific", defaultValue: "In 2000 the WHO's Western Pacific regional office, with the International Obesity Task Force, published The Asia-Pacific Perspective: Redefining Obesity, proposing lower action points for adults of Asian descent: overweight from a BMI of 23 and obesity from 27.5, instead of the international 25 and 30. The evidence behind it is that at the same BMI, many Asian populations carry a higher percentage of body fat and face a higher risk of type 2 diabetes and cardiovascular disease, so waiting for the international thresholds means acting too late. A 2004 WHO expert consultation published in The Lancet endorsed 23 and 27.5 as public-health action points, and several countries, including India, Singapore, and Japan, use cutoffs in this range in clinical practice. The underweight boundary stays at 18.5, and the BMI number itself is unchanged — only the point where it starts counting as elevated moves down. Choose this classification if your background is South or East Asian, or if your clinician uses the lower action points with you.")

    // MARK: - Exercise catalog keys (lowercase DB values → display labels)
    /// Display label for a lowercase exercise-catalog key (bodyPart, equipment,
    /// or exerciseType value stored in the DB). Mirrors LogicLabels.kt's
    /// exerciseCatalogLabel; unknown keys — e.g. from a newer catalog — fall
    /// back to the old capitalize-the-key rendering.
    static func exerciseCatalogLabel(_ key: String) -> String {
        switch key {
        case "back": return String(localized: "body_part_back", defaultValue: "Back")
        case "biceps": return String(localized: "body_part_biceps", defaultValue: "Biceps")
        case "calves": return String(localized: "body_part_calves", defaultValue: "Calves")
        case "chest": return String(localized: "body_part_chest", defaultValue: "Chest")
        case "core": return String(localized: "body_part_core", defaultValue: "Core")
        case "forearms": return String(localized: "body_part_forearms", defaultValue: "Forearms")
        case "full body": return String(localized: "body_part_full_body", defaultValue: "Full body")
        case "glutes": return String(localized: "body_part_glutes", defaultValue: "Glutes")
        case "hamstrings": return String(localized: "body_part_hamstrings", defaultValue: "Hamstrings")
        case "shoulders": return String(localized: "body_part_shoulders", defaultValue: "Shoulders")
        case "thighs": return String(localized: "body_part_thighs", defaultValue: "Thighs")
        case "triceps": return String(localized: "body_part_triceps", defaultValue: "Triceps")
        case "machine": return String(localized: "equipment_machine", defaultValue: "Machine")
        case "body weight": return String(localized: "equipment_body_weight", defaultValue: "Body weight")
        case "cable": return String(localized: "equipment_cable", defaultValue: "Cable")
        case "barbell": return String(localized: "equipment_barbell", defaultValue: "Barbell")
        case "smith machine": return String(localized: "equipment_smith_machine", defaultValue: "Smith machine")
        case "dumbbell": return String(localized: "equipment_dumbbell", defaultValue: "Dumbbell")
        case "kettlebell": return String(localized: "equipment_kettlebell", defaultValue: "Kettlebell")
        case "other": return String(localized: "equipment_other", defaultValue: "Other")
        case "band": return String(localized: "equipment_band", defaultValue: "Band")
        case "strength": return String(localized: "exercise_type_strength", defaultValue: "Strength")
        case "duration": return String(localized: "exercise_type_duration", defaultValue: "Duration")
        case "distance": return String(localized: "exercise_type_distance", defaultValue: "Distance")
        case "reps_duration": return String(localized: "exercise_type_reps_duration", defaultValue: "Reps and duration")
        case "distance_duration": return String(localized: "exercise_type_distance_duration", defaultValue: "Distance and duration")
        case "reps_distance": return String(localized: "exercise_type_reps_distance", defaultValue: "Reps and distance")
        default: return key.prefix(1).uppercased() + key.dropFirst()
        }
    }
    /// Folded tail of the body-part pie chart, not a catalog value.
    static let bodyPartOther = String(localized: "body_part_other", defaultValue: "Other")

    /// Full name of a sharedLogic SetType, for the set-type menu.
    static func setTypeLabel(_ setType: SetType) -> String {
        switch setType {
        case .warmup: return warmUp
        case .drop: return dropSet
        default: return setTypeNormal
        }
    }

    /// The marker shown in place of a set number. Normal sets keep their
    /// working-set number, so they have none.
    static func setTypeMarker(_ setType: SetType) -> String? {
        switch setType {
        case .warmup: return warmupMarker
        case .drop: return dropsetMarker
        default: return nil
        }
    }
}
