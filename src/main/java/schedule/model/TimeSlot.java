package schedule.model;

import java.time.LocalTime;
import java.util.Objects;

public class TimeSlot {

    private final int weekNumber;
    private final WeekDay day;
    private final int lessonNumber;
    private final LocalTime startTime;
    private final LocalTime endTime;

    public TimeSlot(
            int weekNumber,
            WeekDay day,
            int lessonNumber,
            LocalTime startTime,
            LocalTime endTime) {

        if (weekNumber <= 0) {
            throw new IllegalArgumentException(
                    "Week number must be positive");
        }

        if (lessonNumber < 1 || lessonNumber > 8) {
            throw new IllegalArgumentException(
                    "Lesson number must be between 1 and 8");
        }

        this.day = Objects.requireNonNull(
                day, "Day cannot be null");

        this.startTime = Objects.requireNonNull(
                startTime, "Start time cannot be null");

        this.endTime = Objects.requireNonNull(
                endTime, "End time cannot be null");

        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException(
                    "End time must be after start time");
        }

        this.weekNumber = weekNumber;
        this.lessonNumber = lessonNumber;
    }

    public int getWeekNumber() {
        return weekNumber;
    }

    public WeekDay getDay() {
        return day;
    }

    public int getLessonNumber() {
        return lessonNumber;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TimeSlot other)) return false;

        return weekNumber == other.weekNumber
                && lessonNumber == other.lessonNumber
                && day == other.day
                && startTime.equals(other.startTime)
                && endTime.equals(other.endTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                weekNumber,
                day,
                lessonNumber,
                startTime,
                endTime
        );
    }

    @Override
    public String toString() {
        return "Неделя " + weekNumber
                + ", " + day
                + ", пара " + lessonNumber
                + ", " + startTime + "-" + endTime;
    }
}
