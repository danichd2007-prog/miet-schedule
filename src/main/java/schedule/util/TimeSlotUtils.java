package schedule.util;

import schedule.model.ScheduleEntry;
import schedule.model.TimeSlot;
import schedule.model.WeekType;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public final class TimeSlotUtils {

    private TimeSlotUtils() {
    }

    public static boolean overlaps(
            ScheduleEntry first,
            ScheduleEntry second) {

        Objects.requireNonNull(first);
        Objects.requireNonNull(second);

        TimeSlot a = first.getTimeSlot();
        TimeSlot b = second.getTimeSlot();

        if (a.getWeekNumber() != b.getWeekNumber()
                || a.getDay() != b.getDay()) {
            return false;
        }

        int aStart = a.getLessonNumber();
        int bStart = b.getLessonNumber();

        int aEnd = aStart + first.getLesson().getDuration() - 1;
        int bEnd = bStart + second.getLesson().getDuration() - 1;

        return aStart <= bEnd && bStart <= aEnd;
    }

    public static Set<WeekType> getCoveredWeeks(WeekType type) {
        Objects.requireNonNull(type);

        return switch (type) {
            case F_NUMERATOR -> EnumSet.of(WeekType.F_NUMERATOR);
            case F_DENOMINATOR -> EnumSet.of(WeekType.F_DENOMINATOR);
            case S_NUMERATOR -> EnumSet.of(WeekType.S_NUMERATOR);
            case S_DENOMINATOR -> EnumSet.of(WeekType.S_DENOMINATOR);
            case NUMERATOR -> EnumSet.of(
                    WeekType.F_NUMERATOR, WeekType.S_NUMERATOR);
            case DENOMINATOR -> EnumSet.of(
                    WeekType.F_DENOMINATOR, WeekType.S_DENOMINATOR);
            case EVERY_WEEK -> EnumSet.of(
                    WeekType.F_NUMERATOR,
                    WeekType.F_DENOMINATOR,
                    WeekType.S_NUMERATOR,
                    WeekType.S_DENOMINATOR);
        };
    }

    public static boolean weeksCover(
            WeekType available,
            WeekType required) {

        return getCoveredWeeks(available)
                .containsAll(getCoveredWeeks(required));
    }
}
