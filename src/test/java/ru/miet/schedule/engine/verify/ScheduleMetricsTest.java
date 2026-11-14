package ru.miet.schedule.engine.verify;

import org.junit.jupiter.api.Test;
import ru.miet.schedule.domain.Classroom;
import ru.miet.schedule.domain.ClassroomType;
import ru.miet.schedule.domain.StudentGroup;
import ru.miet.schedule.domain.Teacher;
import ru.miet.schedule.domain.TeacherSubject;
import ru.miet.schedule.domain.TeachingRequirement;
import ru.miet.schedule.domain.WeekDay;
import ru.miet.schedule.engine.Fixtures;
import ru.miet.schedule.engine.model.ScheduledLesson;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScheduleMetricsTest {

    private final StudentGroup group = Fixtures.group("Г-1", 25);
    private final Teacher teacher = Fixtures.teacher("Иванов");
    private final TeacherSubject qualification =
            Fixtures.qualification(teacher, Fixtures.subject("Предмет"));
    private final Classroom room = Fixtures.room("1", ClassroomType.REGULAR, 30);

    private ScheduledLesson lesson(int week, WeekDay day, int pair) {
        TeachingRequirement r = Fixtures.seminar(qualification, group, 1);
        return new ScheduledLesson(r, week, day, pair, 1, room);
    }

    @Test
    void consecutiveLessonsHaveNoWindows() {
        ScheduleMetrics metrics = ScheduleMetrics.of(List.of(
                lesson(1, WeekDay.MONDAY, 1),
                lesson(1, WeekDay.MONDAY, 2)));

        assertEquals(0, metrics.studentWindows());
        assertEquals(0, metrics.teacherWindows());
    }

    @Test
    void gapBetweenLessonsCountsEveryEmptyPair() {
        ScheduleMetrics metrics = ScheduleMetrics.of(List.of(
                lesson(1, WeekDay.MONDAY, 1),
                lesson(1, WeekDay.MONDAY, 4)));

        assertEquals(2, metrics.studentWindows()); // пары 2 и 3
        assertEquals(2, metrics.teacherWindows());
    }

    @Test
    void differentDaysAndWeeksDoNotMakeWindows() {
        ScheduleMetrics metrics = ScheduleMetrics.of(List.of(
                lesson(1, WeekDay.MONDAY, 1),
                lesson(1, WeekDay.TUESDAY, 5),
                lesson(2, WeekDay.MONDAY, 8)));

        assertEquals(0, metrics.studentWindows());
    }

    @Test
    void onlyTheFirstCycleOfFourWeeksIsCounted() {
        ScheduleMetrics metrics = ScheduleMetrics.of(List.of(
                lesson(5, WeekDay.MONDAY, 1),
                lesson(5, WeekDay.MONDAY, 6)));

        assertEquals(0, metrics.lessons());
        assertEquals(0, metrics.studentWindows());
    }

    @Test
    void saturdayAndLateLessonsAreCounted() {
        ScheduleMetrics metrics = ScheduleMetrics.of(List.of(
                lesson(1, WeekDay.SATURDAY, 1),
                lesson(1, WeekDay.MONDAY, 4),   // заканчивается на 4 паре: не поздняя
                lesson(1, WeekDay.MONDAY, 5))); // 5 пара: поздняя

        assertEquals(3, metrics.lessons());
        assertEquals(1, metrics.saturdayLessons());
        assertEquals(1, metrics.lateLessons());
    }

    @Test
    void averageIsPerGroupPerWeekOfTheCycle() {
        // 2 окна у одной группы за 4 недели цикла = 0.5 окна в неделю
        ScheduleMetrics metrics = ScheduleMetrics.of(List.of(
                lesson(1, WeekDay.MONDAY, 1),
                lesson(1, WeekDay.MONDAY, 4)));

        assertEquals(1, metrics.groups());
        assertEquals(0.5, metrics.studentWindowsPerGroupWeek());
    }
}
