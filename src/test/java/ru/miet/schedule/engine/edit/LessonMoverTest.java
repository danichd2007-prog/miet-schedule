package ru.miet.schedule.engine.edit;

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
import ru.miet.schedule.engine.edit.LessonMover.Candidate;
import ru.miet.schedule.engine.edit.LessonMover.Key;
import ru.miet.schedule.engine.edit.LessonMover.Plan;
import ru.miet.schedule.engine.edit.LessonMover.Scope;
import ru.miet.schedule.engine.model.Problem;
import ru.miet.schedule.engine.model.ScheduledLesson;
import ru.miet.schedule.engine.solver.ConstructiveSolver;
import ru.miet.schedule.engine.solver.SolverConfig;
import ru.miet.schedule.engine.synthetic.SyntheticUniversity;
import ru.miet.schedule.engine.verify.ScheduleVerifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LessonMoverTest {

    private final StudentGroup g1 = Fixtures.group("Г-1", 25);
    private final StudentGroup g2 = Fixtures.group("Г-2", 25);
    private final Teacher t1 = Fixtures.teacher("Первый");
    private final Teacher t2 = Fixtures.teacher("Второй");
    private final TeacherSubject q1 = Fixtures.qualification(t1, Fixtures.subject("Физика"));
    private final TeacherSubject q2 = Fixtures.qualification(t2, Fixtures.subject("Химия"));
    private final Classroom r1 = Fixtures.room("1", ClassroomType.REGULAR, 30);
    private final Classroom r2 = Fixtures.room("2", ClassroomType.REGULAR, 30);
    private final Classroom tiny = Fixtures.room("3", ClassroomType.REGULAR, 10);
    private final Classroom lab = Fixtures.room("4", ClassroomType.LABORATORY, 30);

    private LessonMover mover(TeacherAvailability... availability) {
        return new LessonMover(new LessonMover.Context(
                Fixtures.CALENDAR, List.of(availability), 6));
    }

    private ScheduledLesson seminar(TeacherSubject q, StudentGroup g, int week, WeekDay day, int pair, Classroom room) {
        return new ScheduledLesson(Fixtures.seminar(q, g, 1), week, day, pair, 1, room);
    }

    private static Key key(ScheduledLesson l) {
        return new Key(l.requirement().getId(), l.week(), l.day(), l.pair());
    }

    private Plan move(LessonMover mover, List<ScheduledLesson> all, ScheduledLesson l,
                      Scope scope, WeekDay day, int pair, Classroom room) {
        return mover.plan(all, key(l), scope, day, pair, room);
    }

    private static boolean mentions(List<String> messages, String part) {
        return messages.stream().anyMatch(m -> m.contains(part));
    }

    @Test
    void moveToFreeSlotIsValidAndOriginalListIsUntouched() {
        ScheduledLesson a = seminar(q1, g1, 1, WeekDay.MONDAY, 1, r1);
        List<ScheduledLesson> all = new ArrayList<>(List.of(a));

        Plan plan = move(mover(), all, a, Scope.SINGLE_WEEK, WeekDay.TUESDAY, 3, r1);

        assertTrue(plan.valid(), () -> plan.errors().toString());
        assertEquals(1, plan.moves().size());
        assertEquals(List.of(a), all);

        List<ScheduledLesson> after = LessonMover.applied(all, plan);
        assertEquals(1, after.size());
        assertEquals(WeekDay.TUESDAY, after.get(0).day());
        assertEquals(3, after.get(0).pair());
    }

    @Test
    void teacherGroupAndRoomConflictsAreReported() {
        ScheduledLesson moving = seminar(q1, g1, 1, WeekDay.MONDAY, 1, r1);
        ScheduledLesson sameTeacher = seminar(q1, g2, 1, WeekDay.TUESDAY, 2, r2);
        ScheduledLesson sameGroup = seminar(q2, g1, 1, WeekDay.TUESDAY, 3, r2);
        ScheduledLesson sameRoom = seminar(q2, g2, 1, WeekDay.TUESDAY, 4, r1);
        List<ScheduledLesson> all = List.of(moving, sameTeacher, sameGroup, sameRoom);

        assertTrue(mentions(move(mover(), all, moving, Scope.SINGLE_WEEK, WeekDay.TUESDAY, 2, r1).errors(),
                "Преподаватель Первый"));
        assertTrue(mentions(move(mover(), all, moving, Scope.SINGLE_WEEK, WeekDay.TUESDAY, 3, r1).errors(),
                "У группы Г-1"));
        assertTrue(mentions(move(mover(), all, moving, Scope.SINGLE_WEEK, WeekDay.TUESDAY, 4, r1).errors(),
                "Аудитория 1"));
    }

    @Test
    void roomTypeAndSeatsAreChecked() {
        ScheduledLesson a = seminar(q1, g1, 1, WeekDay.MONDAY, 1, r1);
        List<ScheduledLesson> all = List.of(a);

        assertTrue(mentions(move(mover(), all, a, Scope.SINGLE_WEEK, WeekDay.MONDAY, 2, lab).errors(), "по типу"));
        assertTrue(mentions(move(mover(), all, a, Scope.SINGLE_WEEK, WeekDay.MONDAY, 2, tiny).errors(), "10 мест"));
    }

    @Test
    void teacherAvailabilityIsHardWhenRulesExist() {
        ScheduledLesson a = seminar(q1, g1, 1, WeekDay.MONDAY, 1, r1);
        TeacherAvailability onlyTuesday =
                new TeacherAvailability(t1, WeekDay.TUESDAY, 1, 6, WeekType.EVERY_WEEK);

        Plan bad = move(mover(onlyTuesday), List.of(a), a, Scope.SINGLE_WEEK, WeekDay.WEDNESDAY, 2, r1);
        Plan good = move(mover(onlyTuesday), List.of(a), a, Scope.SINGLE_WEEK, WeekDay.TUESDAY, 2, r1);

        assertTrue(mentions(bad.errors(), "не работает в это время"));
        assertTrue(good.valid());
    }

    @Test
    void availabilityDependsOnWeekType() {
        // доступен только в числители: неделя 1 — числитель, неделя 2 — знаменатель
        TeacherAvailability numeratorOnly =
                new TeacherAvailability(t1, WeekDay.MONDAY, 1, 8, WeekType.NUMERATOR);
        ScheduledLesson w1 = seminar(q1, g1, 1, WeekDay.TUESDAY, 1, r1);
        ScheduledLesson w2 = seminar(q1, g1, 2, WeekDay.TUESDAY, 1, r1);

        assertTrue(move(mover(numeratorOnly), List.of(w1), w1, Scope.SINGLE_WEEK, WeekDay.MONDAY, 1, r1).valid());
        assertFalse(move(mover(numeratorOnly), List.of(w2), w2, Scope.SINGLE_WEEK, WeekDay.MONDAY, 1, r1).valid());
    }

    @Test
    void blockLessonMustFitIntoTheDay() {
        TeacherSubject q = Fixtures.qualification(t1, Fixtures.subject("Лабораторная"));
        ScheduledLesson block = new ScheduledLesson(
                Fixtures.lab(q, g1, 2, ClassroomType.REGULAR), 1, WeekDay.MONDAY, 1, 2, r1);

        Plan plan = move(mover(), List.of(block), block, Scope.SINGLE_WEEK, WeekDay.MONDAY, 8, r1);

        assertTrue(mentions(plan.errors(), "не помещается в день"));
        assertTrue(move(mover(), List.of(block), block, Scope.SINGLE_WEEK, WeekDay.MONDAY, 7, r1).valid());
    }

    @Test
    void maxPairsPerDayIsEnforced() {
        List<ScheduledLesson> all = new ArrayList<>();

        for (int pair = 1; pair <= 6; pair++) {
            all.add(seminar(Fixtures.qualification(Fixtures.teacher("Т" + pair), Fixtures.subject("П" + pair)),
                    g1, 1, WeekDay.MONDAY, pair, Fixtures.room("К" + pair, ClassroomType.REGULAR, 30)));
        }

        ScheduledLesson seventh = seminar(q1, g1, 1, WeekDay.TUESDAY, 1, r1);
        all.add(seventh);

        Plan plan = move(mover(), all, seventh, Scope.SINGLE_WEEK, WeekDay.MONDAY, 7, r1);

        assertTrue(mentions(plan.errors(), "пар в день"));
    }

    @Test
    void seriesScopeMovesAllFollowingWeeksOnly() {
        TeachingRequirement weekly = Fixtures.seminar(q1, g1, 8);
        List<ScheduledLesson> all = new ArrayList<>();

        for (int week = 1; week <= 8; week++) {
            all.add(new ScheduledLesson(weekly, week, WeekDay.MONDAY, 1, 1, r1));
        }

        ScheduledLesson fromFive = all.get(4);

        Plan series = move(mover(), all, fromFive, Scope.FROM_THIS_WEEK, WeekDay.THURSDAY, 2, r1);
        Plan single = move(mover(), all, fromFive, Scope.SINGLE_WEEK, WeekDay.THURSDAY, 2, r1);

        assertTrue(series.valid());
        assertEquals(4, series.moves().size()); // недели 5, 6, 7, 8
        assertEquals(1, single.moves().size());

        List<ScheduledLesson> after = LessonMover.applied(all, series);
        assertEquals(8, after.size());
        assertEquals(4L, after.stream().filter(l -> l.day() == WeekDay.MONDAY).count());
        assertTrue(after.stream().filter(l -> l.day() == WeekDay.THURSDAY).allMatch(l -> l.week() >= 5));
    }

    @Test
    void conflictInOneWeekOfTheSeriesBlocksTheWholeMoveAndNamesTheWeek() {
        TeachingRequirement weekly = Fixtures.seminar(q1, g1, 4);
        List<ScheduledLesson> all = new ArrayList<>();

        for (int week = 1; week <= 4; week++) {
            all.add(new ScheduledLesson(weekly, week, WeekDay.MONDAY, 1, 1, r1));
        }

        all.add(seminar(q2, g1, 3, WeekDay.FRIDAY, 2, r2)); // только на 3 неделе

        Plan plan = move(mover(), all, all.get(0), Scope.FROM_THIS_WEEK, WeekDay.FRIDAY, 2, r1);

        assertFalse(plan.valid());
        assertTrue(mentions(plan.errors(), "(неделя 3)"), plan.errors().toString());
    }

    @Test
    void newWindowGivesWarningButNotError() {
        ScheduledLesson first = seminar(q1, g1, 1, WeekDay.MONDAY, 1, r1);
        ScheduledLesson moving = seminar(q2, g1, 1, WeekDay.MONDAY, 2, r2);
        List<ScheduledLesson> all = List.of(first, moving);

        Plan plan = move(mover(), all, moving, Scope.SINGLE_WEEK, WeekDay.MONDAY, 5, r2);

        assertTrue(plan.valid());
        assertTrue(mentions(plan.warnings(), "У группы Г-1 станет больше окон"), plan.warnings().toString());
        assertTrue(mentions(plan.warnings(), "позже 4 пары"));
    }

    @Test
    void saturdayGivesWarning() {
        ScheduledLesson a = seminar(q1, g1, 1, WeekDay.MONDAY, 1, r1);
        Plan plan = move(mover(), List.of(a), a, Scope.SINGLE_WEEK, WeekDay.SATURDAY, 1, r1);

        assertTrue(plan.valid());
        assertTrue(mentions(plan.warnings(), "субботу"));
    }

    @Test
    void movingToTheSamePlaceIsRejected() {
        ScheduledLesson a = seminar(q1, g1, 1, WeekDay.MONDAY, 1, r1);
        Plan plan = move(mover(), List.of(a), a, Scope.SINGLE_WEEK, WeekDay.MONDAY, 1, r1);

        assertFalse(plan.valid());
        assertTrue(mentions(plan.errors(), "уже стоит"));
    }

    @Test
    void unknownLessonIsReported() {
        ScheduledLesson a = seminar(q1, g1, 1, WeekDay.MONDAY, 1, r1);
        Plan plan = mover().plan(List.of(a), new Key(999999, 1, WeekDay.MONDAY, 1),
                Scope.SINGLE_WEEK, WeekDay.TUESDAY, 1, r1);

        assertFalse(plan.valid());
        assertTrue(mentions(plan.errors(), "не найдено"));
    }

    @Test
    void suggestionsAreAllValidAndSortedByFewestWarnings() {
        ScheduledLesson a = seminar(q1, g1, 1, WeekDay.MONDAY, 1, r1);
        ScheduledLesson blocker = seminar(q2, g1, 1, WeekDay.MONDAY, 2, r2);
        List<ScheduledLesson> all = List.of(a, blocker);

        List<Candidate> suggestions = mover().suggest(all, key(a), Scope.SINGLE_WEEK, List.of(r1, r2, tiny, lab), 100);

        assertFalse(suggestions.isEmpty());
        // занятое место и неподходящие аудитории не предлагаются
        assertTrue(suggestions.stream().noneMatch(c -> c.day() == WeekDay.MONDAY && c.pair() == 2));
        assertTrue(suggestions.stream().noneMatch(c -> c.room().equals(tiny) || c.room().equals(lab)));

        for (Candidate c : suggestions) {
            assertTrue(move(mover(), all, a, Scope.SINGLE_WEEK, c.day(), c.pair(), c.room()).valid());
        }

        for (int i = 1; i < suggestions.size(); i++) {
            assertTrue(suggestions.get(i - 1).warnings() <= suggestions.get(i).warnings());
        }
    }

    /**
     * Главная проверка: на случайных переносах в большом расписании вывод мувера
     * («допустимо» или нет) совпадает с вердиктом независимого верификатора.
     */
    @Test
    void agreesWithTheIndependentVerifierOnRandomMoves() {
        SyntheticUniversity.Data data = SyntheticUniversity.generate(20, 3, 1.0);
        SolverConfig config = SolverConfig.defaults();
        Problem problem = Problem.of(data.requirements(), data.rooms(), data.availability(), data.calendar());
        List<ScheduledLesson> lessons = new ConstructiveSolver(config).solve(problem).toScheduledLessons();

        assertTrue(ScheduleVerifier.verify(data.requirements(), data.availability(), data.calendar(),
                lessons, config.maxPairsPerDay(), Set.of()).isEmpty());

        LessonMover mover = new LessonMover(new LessonMover.Context(
                data.calendar(), data.availability(), config.maxPairsPerDay()));

        Random random = new Random(7);
        WeekDay[] days = WeekDay.values();
        int valid = 0;
        int invalid = 0;

        for (int trial = 0; trial < 500; trial++) {
            ScheduledLesson lesson = lessons.get(random.nextInt(lessons.size()));
            Scope scope = random.nextBoolean() ? Scope.SINGLE_WEEK : Scope.FROM_THIS_WEEK;
            WeekDay day = days[random.nextInt(days.length)];
            int pair = 1 + random.nextInt(8);
            Classroom room = data.rooms().get(random.nextInt(data.rooms().size()));

            Plan plan = mover.plan(lessons, key(lesson), scope, day, pair, room);

            if (plan.moves().isEmpty()) {
                continue; // «уже стоит в этом месте»
            }

            List<ScheduledLesson> moved = LessonMover.applied(lessons, plan);
            List<String> violations = ScheduleVerifier.verify(data.requirements(), data.availability(),
                    data.calendar(), moved, config.maxPairsPerDay(), new HashSet<>());

            assertEquals(violations.isEmpty(), plan.valid(),
                    () -> "Расхождение: мувер " + (plan.valid() ? "разрешил" : "запретил " + plan.errors())
                            + ", верификатор: " + violations);

            if (plan.valid()) {
                valid++;
            } else {
                invalid++;
            }
        }

        assertTrue(valid >= 20, "мало допустимых переносов в выборке: " + valid);
        assertTrue(invalid >= 20, "мало недопустимых переносов в выборке: " + invalid);
    }
}
