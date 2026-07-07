import SwiftUI
import SharedUI

private let weekNumberWidth: CGFloat = 32
private let weekdayLabelsSundayFirst = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"]
private let weekdayLabelsMondayFirst = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]

struct CalendarView: View {
    @State private var model: CalendarModel?
    @State private var profile: Profile?

    private var weekStartsMonday: Bool { profile?.weekStartsMonday == true }

    var body: some View {
        Group {
            if let model {
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
            } else {
                ProgressView()
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

#Preview {
    CalendarView()
}
