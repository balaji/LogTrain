import SwiftUI
import SharedUI

private let weekNumberWidth: CGFloat = 32
private let weekdayLabelsSundayFirst = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"]
private let weekdayLabelsMondayFirst = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]

struct CalendarView: View {
    @State private var model: CalendarModel?
    @State private var profile: Profile?
    @State private var showingSessionList = false

    private var weekStartsMonday: Bool { profile?.weekStartsMonday == true }

    var body: some View {
        Group {
            if showingSessionList {
                CompletedSessionListView()
            } else if let model {
                calendarContent(model)
            } else {
                ProgressView()
            }
        }
        .navigationTitle(showingSessionList ? "Workout History" : "Calendar")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    showingSessionList.toggle()
                } label: {
                    Image(systemName: showingSessionList ? "calendar" : "list.bullet")
                }
                .accessibilityLabel(showingSessionList ? "Show calendar" : "Show workout history")
            }
        }
        .navigationDestination(for: Int64.self) { epochDay in
            DayDetailView(epochDay: epochDay)
        }
        .task {
            for await m in CalendarRepository.shared.calendar {
                model = m
            }
        }
        .task {
            for await value in ProfileRepository.shared.profile {
                profile = value
            }
        }
    }

    private func calendarContent(_ model: CalendarModel) -> some View {
        VStack(spacing: 0) {
            weekdayHeader
            Divider()
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(spacing: 0) {
                        ForEach(model.weeks, id: \.weekStartEpochDay) { week in
                            WeekRowView(week: week)
                                .id(week.weekStartEpochDay)
                        }
                    }
                }
                .onAppear {
                    let index = max(Int(model.currentWeekIndex) - 2, 0)
                    guard model.weeks.indices.contains(index) else { return }
                    let target = model.weeks[index].weekStartEpochDay
                    // Deferred so LazyVStack has laid out before scrolling.
                    DispatchQueue.main.async {
                        proxy.scrollTo(target, anchor: .top)
                    }
                }
            }
        }
    }

    private var weekdayHeader: some View {
        let labels = weekStartsMonday ? weekdayLabelsMondayFirst : weekdayLabelsSundayFirst
        return HStack(spacing: 0) {
            Text("Wk")
                .frame(width: weekNumberWidth)
            ForEach(labels, id: \.self) { label in
                Text(label)
                    .frame(maxWidth: .infinity)
            }
        }
        .font(.caption2)
        .foregroundStyle(.secondary)
        .padding(.horizontal, 8)
        .padding(.vertical, 8)
    }
}

private struct WeekRowView: View {
    let week: CalendarWeek

    var body: some View {
        HStack(spacing: 0) {
            Text("\(week.weekNumber)")
                .font(.caption2)
                .foregroundStyle(.secondary)
                .frame(width: weekNumberWidth)
            ForEach(week.days, id: \.epochDay) { day in
                DayCellView(day: day)
                    .frame(maxWidth: .infinity)
            }
        }
        .padding(.horizontal, 8)
    }
}

private struct DayCellView: View {
    let day: CalendarDay

    var body: some View {
        Group {
            if day.hasCompletedWorkout {
                NavigationLink(value: day.epochDay) {
                    dayContent
                }
                .buttonStyle(.plain)
            } else {
                dayContent
            }
        }
        .frame(maxWidth: .infinity)
        .background(day.isWeekend ? Color(.secondarySystemBackground) : Color.clear)
    }

    private var dayContent: some View {
        VStack(spacing: 2) {
            // Blank placeholder keeps row heights uniform.
            Text(day.monthLabel ?? " ")
                .font(.caption2)
                .foregroundStyle(.tint)
            Text("\(day.dayOfMonth)")
                .font(.body)
                .foregroundStyle(day.isFuture ? AnyShapeStyle(.tertiary) : AnyShapeStyle(.primary))
                .frame(width: 28, height: 28)
                .background(
                    day.isToday ? AnyShapeStyle(.tint.opacity(0.2)) : AnyShapeStyle(.clear),
                    in: Circle(),
                )
            Circle()
                .fill(day.hasCompletedWorkout ? AnyShapeStyle(.tint) : AnyShapeStyle(.clear))
                .frame(width: 5, height: 5)
        }
        .padding(.vertical, 4)
    }
}

// MARK: - Completed session list

/// Flat, most-recent-first list of the days that have a completed workout,
/// from the shared `completedWorkoutDays` flow. Rows push the existing
/// `DayDetailView` through the shared `Int64` navigation destination.
private struct CompletedSessionListView: View {
    @State private var days: [CompletedWorkoutDay]?

    var body: some View {
        Group {
            if let days {
                if days.isEmpty {
                    ContentUnavailableView("No completed workouts", systemImage: "figure.strengthtraining.traditional")
                } else {
                    List(days, id: \.epochDay) { day in
                        NavigationLink(value: day.epochDay) {
                            CompletedSessionRow(day: day)
                        }
                    }
                }
            } else {
                ProgressView()
            }
        }
        .task {
            for await value in CalendarRepository.shared.completedWorkoutDays {
                days = value
            }
        }
    }
}

private struct CompletedSessionRow: View {
    let day: CompletedWorkoutDay

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(Self.dayFormatter.string(from: Date(timeIntervalSince1970: Double(day.epochDay) * 86400)))
                .font(.body)
            if !day.routineNames.isEmpty {
                Text(day.routineNames.joined(separator: " · "))
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 2)
    }

    private static let dayFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "MMM d, yyyy"
        formatter.timeZone = TimeZone(identifier: "UTC")
        return formatter
    }()
}

#Preview {
    CalendarView()
}
