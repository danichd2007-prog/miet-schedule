package ru.miet.schedule.engine.model;

import ru.miet.schedule.domain.AcademicCalendar;
import ru.miet.schedule.domain.Classroom;
import ru.miet.schedule.domain.TeachingRequirement;
import ru.miet.schedule.domain.WeekDay;

import java.time.LocalDate;

/** Конкретное занятие на конкретной неделе семестра: результат разворота серии по календарю. */
public record ScheduledLesson(
        TeachingRequirement requirement,
        int week,
        WeekDay day,
        int pair,
        int duration,
        Classroom room) {

    public LocalDate date(AcademicCalendar calendar) {
        return calendar.getDate(week, day);
    }

    public int lastPair() {
        return pair + duration - 1;
    }
}
