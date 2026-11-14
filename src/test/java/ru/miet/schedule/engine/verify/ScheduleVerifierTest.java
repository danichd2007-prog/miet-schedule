package ru.miet.schedule.engine.verify;

import org.junit.jupiter.api.Test;
import ru.miet.schedule.domain.Classroom;
import ru.miet.schedule.domain.ClassroomType;
import ru.miet.schedule.domain.StudentGroup;
import ru.miet.schedule.domain.Teacher;
import ru.miet.schedule.domain.TeacherAvailability;
import ru.miet.schedule.domain.TeacherSubject;
import ru.miet.schedule.domain.TeachingRequirement;
import ru.miet.schedule.domain.WeekDay;
import ru.miet.schedule.domain.WeekType;
import ru.miet.schedule.engine.Fixtures;
import ru.miet.schedule.engine.model.ScheduledLesson;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Проверяем сам верификатор: он должен находить каждый вид нарушения.
 * Занятия собираем вручную, не через решатель.
 */
class ScheduleVerifierTest {

    private final Teacher teacher = Fixtures.teacher("Иванов");
    private final TeacherSubject qualification =
            Fixtures.qualification(teacher, Fixtures.subject("Предмет"));
    private final Classroom room = Fixtures.room("1", ClassroomType.REGULAR, 30);

    /** Нагрузка в одно занятие на 1 неделю: pairs = 1 даёт lessonCount() = 1. */
    private TeachingRequirement once(StudentGroup group, TeacherSubject q) {
        return Fixtures.seminar(q, group, 1);
    }

    private ScheduledLesson lesson(
            TeachingRequirement r, int week, WeekDay day, int pair, Classroom room) {

        return new ScheduledLesson(r, week, day, pair, r.lessonDuration(), room);
    }

    private List<String> verify(
            List<TeachingRequirement> requirements,
            List<TeacherAvailability> availability,
            List<ScheduledLesson> lessons,
            int maxPerDay) {

        return ScheduleVerifier.verify(
                requirements, availability, Fixtures.CALENDAR, lessons, maxPerDay, Set.of());
    }

    private boolean mentions(List<String> violations, String text) {
        return violations.stream().anyMatch(v -> v.contains(text));
    }

    @Test
    void correctScheduleHasNoViolations() {
        TeachingRequirement r = once(Fixtures.group("Г-1", 25), qualification);

        assertEquals(List.of(), verify(
                List.of(r), List.of(),
                List.of(lesson(r, 1, WeekDay.MONDAY, 1, room)), 6));
    }

    @Test
    void teacherDoubleBookingIsFound() {
        TeachingRequirement a = once(Fixtures.group("Г-1", 25), qualification);
        TeachingRequirement b = once(Fixtures.group("Г-2", 25), qualification);
        Classroom other = Fixtures.room("2", ClassroomType.REGULAR, 30);

        List<String> violations = verify(
                List.of(a, b), List.of(),
                List.of(lesson(a, 1, WeekDay.MONDAY, 1, room),
                        lesson(b, 1, WeekDay.MONDAY, 1, other)), 6);

        assertTrue(mentions(violations, "Преподаватель"), violations.toString());
    }

    @Test
    void groupDoubleBookingIsFound() {
        StudentGroup group = Fixtures.group("Г-1", 25);
        TeacherSubject second = Fixtures.qualification(
                Fixtures.teacher("Петров"), Fixtures.subject("Другой"));
        TeachingRequirement a = once(group, qualification);
        TeachingRequirement b = once(group, second);
        Classroom other = Fixtures.room("2", ClassroomType.REGULAR, 30);

        List<String> violations = verify(
                List.of(a, b), List.of(),
                List.of(lesson(a, 1, WeekDay.MONDAY, 1, room),
                        lesson(b, 1, WeekDay.MONDAY, 1, other)), 6);

        assertTrue(mentions(violations, "Группа"), violations.toString());
    }

    @Test
    void roomDoubleBookingIsFound() {
        TeacherSubject second = Fixtures.qualification(
                Fixtures.teacher("Петров"), Fixtures.subject("Другой"));
        TeachingRequirement a = once(Fixtures.group("Г-1", 25), qualification);
        TeachingRequirement b = once(Fixtures.group("Г-2", 25), second);

        List<String> violations = verify(
                List.of(a, b), List.of(),
                List.of(lesson(a, 1, WeekDay.MONDAY, 1, room),
                        lesson(b, 1, WeekDay.MONDAY, 1, room)), 6);

        assertTrue(mentions(violations, "Аудитория"), violations.toString());
    }

    @Test
    void sameSlotInDifferentWeeksIsNotAConflict() {
        TeacherSubject second = Fixtures.qualification(
                Fixtures.teacher("Петров"), Fixtures.subject("Другой"));
        TeachingRequirement a = once(Fixtures.group("Г-1", 25), qualification);
        TeachingRequirement b = once(Fixtures.group("Г-2", 25), second);

        assertEquals(List.of(), verify(
                List.of(a, b), List.of(),
                List.of(lesson(a, 1, WeekDay.MONDAY, 1, room),
                        lesson(b, 2, WeekDay.MONDAY, 1, room)), 6));
    }

    @Test
    void wrongRoomTypeAndTooFewSeatsAreFound() {
        TeachingRequirement r = once(Fixtures.group("Г-1", 40), qualification);
        Classroom computer = Fixtures.room("К", ClassroomType.COMPUTER, 50);
        Classroom tiny = Fixtures.room("Т", ClassroomType.REGULAR, 20);

        assertTrue(mentions(verify(List.of(r), List.of(),
                List.of(lesson(r, 1, WeekDay.MONDAY, 1, computer)), 6), "тип аудитории"));
        assertTrue(mentions(verify(List.of(r), List.of(),
                List.of(lesson(r, 1, WeekDay.MONDAY, 1, tiny)), 6), "мест"));
    }

    @Test
    void teacherAvailabilityIsChecked() {
        TeachingRequirement r = once(Fixtures.group("Г-1", 25), qualification);
        TeacherAvailability tuesdayOnly = new TeacherAvailability(
                teacher, WeekDay.TUESDAY, 1, 8, WeekType.EVERY_WEEK);

        assertTrue(mentions(verify(List.of(r), List.of(tuesdayOnly),
                List.of(lesson(r, 1, WeekDay.MONDAY, 1, room)), 6), "недоступен"));

        assertEquals(List.of(), verify(List.of(r), List.of(tuesdayOnly),
                List.of(lesson(r, 1, WeekDay.TUESDAY, 1, room)), 6));
    }

    @Test
    void availabilityDependsOnTheWeekType() {
        TeachingRequirement r = once(Fixtures.group("Г-1", 25), qualification);
        TeacherAvailability numeratorOnly = new TeacherAvailability(
                teacher, WeekDay.MONDAY, 1, 8, WeekType.NUMERATOR);

        // неделя 1 числитель, неделя 2 знаменатель
        assertEquals(List.of(), verify(List.of(r), List.of(numeratorOnly),
                List.of(lesson(r, 1, WeekDay.MONDAY, 1, room)), 6));
        assertTrue(mentions(verify(List.of(r), List.of(numeratorOnly),
                List.of(lesson(r, 2, WeekDay.MONDAY, 1, room)), 6), "недоступен"));
    }

    @Test
    void dailyLoadLimitIsChecked() {
        StudentGroup group = Fixtures.group("Г-1", 25);
        TeacherSubject second = Fixtures.qualification(
                Fixtures.teacher("Петров"), Fixtures.subject("Другой"));
        TeachingRequirement a = once(group, qualification);
        TeachingRequirement b = once(group, second);
        Classroom other = Fixtures.room("2", ClassroomType.REGULAR, 30);

        List<String> violations = verify(
                List.of(a, b), List.of(),
                List.of(lesson(a, 1, WeekDay.MONDAY, 1, room),
                        lesson(b, 1, WeekDay.MONDAY, 2, other)), 1);

        assertTrue(mentions(violations, "пар в день"), violations.toString());
    }

    @Test
    void missingAndExtraLessonsAreFound() {
        TeachingRequirement r = Fixtures.seminar(
                qualification, Fixtures.group("Г-1", 25), 2); // 2 занятия

        List<String> missing = verify(List.of(r), List.of(),
                List.of(lesson(r, 1, WeekDay.MONDAY, 1, room)), 6);
        assertTrue(mentions(missing, "Размещено 1 занятий из 2"), missing.toString());

        // честно неразмещённая нагрузка не считается нарушением
        assertEquals(List.of(), ScheduleVerifier.verify(
                List.of(r), List.of(), Fixtures.CALENDAR,
                List.of(lesson(r, 1, WeekDay.MONDAY, 1, room)), 6, Set.of(r)));

        List<ScheduledLesson> extra = new ArrayList<>();
        extra.add(lesson(r, 1, WeekDay.MONDAY, 1, room));
        extra.add(lesson(r, 2, WeekDay.MONDAY, 1, room));
        extra.add(lesson(r, 3, WeekDay.MONDAY, 1, room));

        assertTrue(mentions(verify(List.of(r), List.of(), extra, 6), "больше нормы"));
    }

    @Test
    void lessonOutsideOfTheDayIsFound() {
        TeachingRequirement lab = Fixtures.lab(
                qualification, Fixtures.group("Г-1", 25), 2, ClassroomType.REGULAR);

        List<String> violations = verify(List.of(lab), List.of(),
                List.of(lesson(lab, 1, WeekDay.MONDAY, 8, room)), 6); // блок 8-9

        assertTrue(mentions(violations, "за границы дня"), violations.toString());
    }
}
