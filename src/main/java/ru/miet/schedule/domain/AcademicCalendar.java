package ru.miet.schedule.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Календарь семестра: недели нумеруются с 1, фаза цикла = (неделя - 1) % 4. */
public final class AcademicCalendar {

    private final LocalDate firstMonday;
    private final int weekCount;

    public AcademicCalendar(LocalDate firstMonday, int weekCount) {
        this.firstMonday = Objects.requireNonNull(
                firstMonday, "First Monday cannot be null");

        if (firstMonday.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("First date must be Monday");
        }

        if (weekCount <= 0) {
            throw new IllegalArgumentException("Week count must be positive");
        }

        this.weekCount = weekCount;
    }

    public LocalDate getFirstMonday() {
        return firstMonday;
    }

    public int getWeekCount() {
        return weekCount;
    }

    public LocalDate getDate(int weekNumber, WeekDay day) {
        validateWeek(weekNumber);
        Objects.requireNonNull(day);

        return firstMonday
                .plusWeeks(weekNumber - 1L)
                .plusDays(day.ordinal());
    }

    /** Фаза цикла недели: 0 = первый числитель ... 3 = второй знаменатель. */
    public int phaseOf(int weekNumber) {
        validateWeek(weekNumber);
        return (weekNumber - 1) % WeekType.PHASES;
    }

    public WeekType getWeekType(int weekNumber) {
        return WeekType.ofPhase(phaseOf(weekNumber));
    }

    /** Номера недель семестра, на которые распространяется тип недели. */
    public List<Integer> weeksOf(WeekType type) {
        Objects.requireNonNull(type);

        List<Integer> weeks = new ArrayList<>();

        for (int week = 1; week <= weekCount; week++) {
            if (type.coversPhase(phaseOf(week))) {
                weeks.add(week);
            }
        }

        return weeks;
    }

    private void validateWeek(int weekNumber) {
        if (weekNumber < 1 || weekNumber > weekCount) {
            throw new IllegalArgumentException(
                    "Week number is outside the semester");
        }
    }
}
