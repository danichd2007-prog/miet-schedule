package ru.miet.schedule.engine.verify;

import ru.miet.schedule.domain.StudentGroup;
import ru.miet.schedule.domain.WeekDay;
import ru.miet.schedule.domain.WeekType;
import ru.miet.schedule.engine.model.ScheduledLesson;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Показатели удобства расписания. Считаются по первым четырём неделям семестра:
 * это один полный цикл (первый и второй числитель и знаменатель), дальше всё повторяется.
 *
 * Окно: пустая пара между двумя занятиями одного дня. Чем меньше окон, тем удобнее.
 * Это метрики, а не проверка: расписание с окнами корректно, просто неудобно.
 *
 * @param lessons                    занятий в четырёх типовых неделях
 * @param groups                     групп в расписании
 * @param studentWindows             окон у студентов за цикл (по всем группам и дням)
 * @param teacherWindows             окон у преподавателей за цикл
 * @param studentWindowsPerGroupWeek среднее число окон у одной группы в неделю
 * @param saturdayLessons            занятий в субботу
 * @param lateLessons                занятий, заканчивающихся позже 4 пары
 */
public record ScheduleMetrics(
        int lessons,
        int groups,
        int studentWindows,
        int teacherWindows,
        double studentWindowsPerGroupWeek,
        int saturdayLessons,
        int lateLessons) {

    private record DayKey(Object owner, int week, int day) {
    }

    public static ScheduleMetrics of(Collection<ScheduledLesson> lessons) {
        Map<DayKey, Integer> groupDays = new HashMap<>();
        Map<DayKey, Integer> teacherDays = new HashMap<>();
        Set<StudentGroup> groups = new HashSet<>();

        int counted = 0;
        int saturday = 0;
        int late = 0;

        for (ScheduledLesson lesson : lessons) {
            if (lesson.week() > WeekType.PHASES) {
                continue;
            }

            counted++;

            if (lesson.day() == WeekDay.SATURDAY) {
                saturday++;
            }

            if (lesson.lastPair() > 4) {
                late++;
            }

            int bits = ((1 << lesson.duration()) - 1) << (lesson.pair() - 1);
            int day = lesson.day().ordinal();

            teacherDays.merge(
                    new DayKey(lesson.requirement().teacher(), lesson.week(), day),
                    bits, (a, b) -> a | b);

            for (StudentGroup group : lesson.requirement().groups()) {
                groups.add(group);
                groupDays.merge(
                        new DayKey(group, lesson.week(), day), bits, (a, b) -> a | b);
            }
        }

        int studentWindows = windows(groupDays);
        int teacherWindows = windows(teacherDays);

        double perGroupWeek = groups.isEmpty()
                ? 0
                : studentWindows / (double) (groups.size() * WeekType.PHASES);

        return new ScheduleMetrics(
                counted, groups.size(), studentWindows, teacherWindows,
                perGroupWeek, saturday, late);
    }

    private static int windows(Map<DayKey, Integer> days) {
        int total = 0;

        for (int mask : days.values()) {
            int first = Integer.numberOfTrailingZeros(mask);
            int last = 31 - Integer.numberOfLeadingZeros(mask);

            total += (last - first + 1) - Integer.bitCount(mask);
        }

        return total;
    }
}
