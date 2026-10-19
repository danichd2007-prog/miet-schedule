package schedule.model;

import java.time.LocalTime;
import java.util.Objects;

public class TimeSlot {
    private final WeekDay day;
    private final int lessonNumber;
    private final WeekType weekType;
    private final LocalTime startTime;
    private final LocalTime endTime;

    public TimeSlot(WeekDay day,
                    int lessonNumber,
                    WeekType weekType,
                    LocalTime startTime,
                    LocalTime endTime) {

        this.day = Objects.requireNonNull(
                day, "Day cannot be null");

        this.weekType = Objects.requireNonNull(
                weekType, "WeekType cannot be null");

        this.startTime = Objects.requireNonNull(
                startTime, "Start time cannot be null");

        this.endTime = Objects.requireNonNull(
                endTime, "End time cannot be null");

        if (lessonNumber < 1) {
            throw new IllegalArgumentException(
                    "Lesson number must be positive");
        }

        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException(
                    "End time must be after start time");
        }

        this.lessonNumber = lessonNumber;
    }

    public WeekDay getDay() {
        return day;
    }

    public int getLessonNumber() {
        return lessonNumber;
    }

    public WeekType getWeekType() {
        return weekType;
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
        if (o == null || getClass() != o.getClass()) return false;

        TimeSlot timeSlot = (TimeSlot) o;

        return lessonNumber == timeSlot.lessonNumber
                && day == timeSlot.day
                && weekType == timeSlot.weekType
                && startTime.equals(timeSlot.startTime)
                && endTime.equals(timeSlot.endTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                day, lessonNumber, weekType, startTime, endTime);
    }

    @Override
    public String toString() {
        return day + ", пара " + lessonNumber +
                ", " + weekType +
                ", " + startTime + "-" + endTime;
    }
}
