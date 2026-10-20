package schedule.calendar;

import schedule.model.WeekDay;
import schedule.model.WeekType;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

public class AcademicCalendar {

    private final LocalDate firstMonday;
    private final int weekCount;
    private final boolean firstWeekIsNumerator;

    public AcademicCalendar(
            LocalDate firstMonday,
            int weekCount,
            boolean firstWeekIsNumerator,
            Set<LocalDate> nonTeachingDates) {

        this.firstMonday = Objects.requireNonNull(
                firstMonday, "First Monday cannot be null");

        if (firstMonday.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException(
                    "First date must be Monday");
        }

        if (weekCount <= 0) {
            throw new IllegalArgumentException(
                    "Week count must be positive");
        }

        this.weekCount = weekCount;
        this.firstWeekIsNumerator = firstWeekIsNumerator;
    }

    public LocalDate getDate(int weekNumber, WeekDay day) {
        validateWeek(weekNumber);
        Objects.requireNonNull(day);

        return firstMonday
                .plusWeeks(weekNumber - 1L)
                .plusDays(day.ordinal());
    }

    public boolean isNumerator(int weekNumber) {
        validateWeek(weekNumber);

        return ((weekNumber - 1) % 2 == 0)
                == firstWeekIsNumerator;
    }

    public int getWeekCount() {
        return weekCount;
    }

    public LocalDate getFirstMonday() {
        return firstMonday;
    }

    private void validateWeek(int weekNumber) {
        if (weekNumber < 1 || weekNumber > weekCount) {
            throw new IllegalArgumentException(
                    "Week number is outside the semester");
        }
    }

    public WeekType getWeekType(int weekNumber) {
        validateWeek(weekNumber);

        return switch ((weekNumber - 1) % 4) {
            case 0 -> WeekType.F_NUMERATOR;
            case 1 -> WeekType.F_DENOMINATOR;
            case 2 -> WeekType.S_NUMERATOR;
            case 3 -> WeekType.S_DENOMINATOR;
            default -> throw new IllegalStateException();
        };
    }


}
