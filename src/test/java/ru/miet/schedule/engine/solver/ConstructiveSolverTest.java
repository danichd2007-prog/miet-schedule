package ru.miet.schedule.engine.solver;

import org.junit.jupiter.api.Test;
import ru.miet.schedule.domain.Classroom;
import ru.miet.schedule.domain.ClassroomType;
import ru.miet.schedule.domain.StudentGroup;
import ru.miet.schedule.domain.Teacher;
import ru.miet.schedule.domain.TeacherAvailability;
import ru.miet.schedule.domain.TeacherCategory;
import ru.miet.schedule.domain.TeacherSubject;
import ru.miet.schedule.domain.TeachingRequirement;
import ru.miet.schedule.domain.WeekDay;
import ru.miet.schedule.domain.WeekType;
import ru.miet.schedule.engine.Fixtures;
import ru.miet.schedule.engine.model.Problem;
import ru.miet.schedule.engine.synthetic.SyntheticUniversity;
import ru.miet.schedule.engine.verify.ScheduleVerifier;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConstructiveSolverTest {

    private static final SolverConfig CONFIG = SolverConfig.defaults();

    /** Решает и сразу проверяет независимым верификатором: ни одного нарушения. */
    private SolveResult solve(
            List<TeachingRequirement> requirements,
            List<Classroom> rooms,
            List<TeacherAvailability> availability,
            SolverConfig config) {

        Problem problem = Problem.of(requirements, rooms, availability, Fixtures.CALENDAR);
        SolveResult result = new ConstructiveSolver(config).solve(problem);

        List<String> violations = ScheduleVerifier.verify(
                requirements, availability, Fixtures.CALENDAR,
                result.toScheduledLessons(), config.maxPairsPerDay(),
                result.unplacedRequirements());

        assertTrue(violations.isEmpty(), "Нарушения: " + violations);

        return result;
    }

    private SolveResult solve(
            List<TeachingRequirement> requirements, List<Classroom> rooms) {

        return solve(requirements, rooms, List.of(), CONFIG);
    }

    @Test
    void simpleScheduleIsCompleteAndValid() {
        StudentGroup group = Fixtures.group("Г-1", 25);
        TeacherSubject math = Fixtures.qualification(
                Fixtures.teacher("Иванов"), Fixtures.subject("Матан"));
        TeacherSubject phys = Fixtures.qualification(
                Fixtures.teacher("Петров"), Fixtures.subject("Физика"));

        SolveResult result = solve(
                List.of(
                        Fixtures.lecture(math, 16, group),
                        Fixtures.seminar(math, group, 16),
                        Fixtures.seminar(phys, group, 8)),
                List.of(
                        Fixtures.room("1", ClassroomType.LECTURE, 100),
                        Fixtures.room("2", ClassroomType.REGULAR, 30)));

        assertTrue(result.isComplete());
        assertEquals(3, result.placements().size());
        assertEquals(16 + 16 + 8, result.toScheduledLessons().size());
    }

    @Test
    void oneTeacherIsNeverDoubleBooked() {
        TeacherSubject qualification = Fixtures.qualification(
                Fixtures.teacher("Иванов"), Fixtures.subject("Предмет"));

        List<TeachingRequirement> requirements = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            requirements.add(Fixtures.seminar(
                    qualification, Fixtures.group("Г-" + i, 25), 16));
        }

        List<Classroom> rooms = List.of(
                Fixtures.room("1", ClassroomType.REGULAR, 30),
                Fixtures.room("2", ClassroomType.REGULAR, 30),
                Fixtures.room("3", ClassroomType.REGULAR, 30));

        SolveResult result = solve(requirements, rooms);

        assertTrue(result.isComplete());
        assertEquals(5, result.placements().size());
    }

    @Test
    void missingRoomTypeIsReportedInsteadOfThrowing() {
        StudentGroup group = Fixtures.group("Г-1", 25);
        TeacherSubject java = Fixtures.qualification(
                Fixtures.teacher("Иванов"), Fixtures.subject("Java"));
        TeacherSubject phys = Fixtures.qualification(
                Fixtures.teacher("Петров"), Fixtures.subject("Физика"));

        SolveResult result = solve(
                List.of(
                        Fixtures.lab(java, group, 16, ClassroomType.COMPUTER),
                        Fixtures.seminar(phys, group, 16)),
                List.of(Fixtures.room("1", ClassroomType.REGULAR, 30)));

        assertFalse(result.isComplete());
        assertEquals(1, result.unplaced().size());
        assertEquals(1, result.placements().size()); // остальное размещено
        assertTrue(result.unplaced().get(0).reason().contains("нет аудитории"),
                result.unplaced().get(0).reason());
    }

    @Test
    void teacherAvailabilityIsHonored() {
        Teacher teacher = Fixtures.teacher("Совместитель", TeacherCategory.PART_TIME);
        TeacherSubject qualification = Fixtures.qualification(
                teacher, Fixtures.subject("Предмет"));

        TeacherAvailability tuesday = new TeacherAvailability(
                teacher, WeekDay.TUESDAY, 3, 4, WeekType.EVERY_WEEK);

        SolveResult result = solve(
                List.of(Fixtures.seminar(qualification, Fixtures.group("Г-1", 25), 16)),
                List.of(Fixtures.room("1", ClassroomType.REGULAR, 30)),
                List.of(tuesday),
                CONFIG);

        assertTrue(result.isComplete());

        Placement placement = result.placements().get(0);

        assertEquals(WeekDay.TUESDAY.ordinal(), placement.day());
        assertTrue(placement.pair() == 3 || placement.pair() == 4);
    }

    @Test
    void smallestSufficientRoomIsChosen() {
        StudentGroup a = Fixtures.group("А", 25);
        StudentGroup b = Fixtures.group("Б", 25);
        StudentGroup c = Fixtures.group("В", 25);
        TeacherSubject qualification = Fixtures.qualification(
                Fixtures.teacher("Иванов"), Fixtures.subject("Лекции"));

        SolveResult result = solve(
                List.of(Fixtures.lecture(qualification, 16, a, b, c)), // 75 студентов
                List.of(
                        Fixtures.room("малая", ClassroomType.LECTURE, 60),
                        Fixtures.room("большая", ClassroomType.LECTURE, 200),
                        Fixtures.room("средняя", ClassroomType.LECTURE, 80)));

        assertTrue(result.isComplete());
        assertEquals("средняя",
                result.problem().room(result.placements().get(0).room()).getNumber());
    }

    @Test
    void groupDayLoadLimitIsRespected() {
        StudentGroup group = Fixtures.group("Г-1", 25);
        SolverConfig config = new SolverConfig(2, 10, 5, 2, 15, 3, 4, 3, 3, 60_000);

        List<TeachingRequirement> requirements = new ArrayList<>();

        // 14 еженедельных пар при пределе 2 в день и 6 днях: помещается только 12
        for (int i = 0; i < 14; i++) {
            TeacherSubject qualification = Fixtures.qualification(
                    Fixtures.teacher("П" + i), Fixtures.subject("Предмет" + i));
            requirements.add(Fixtures.seminar(qualification, group, 16));
        }

        SolveResult result = solve(
                requirements,
                List.of(Fixtures.room("1", ClassroomType.REGULAR, 30),
                        Fixtures.room("2", ClassroomType.REGULAR, 30)),
                List.of(),
                config);

        assertEquals(12, result.placements().size());
        assertEquals(2, result.unplaced().size());
        assertTrue(result.unplaced().get(0).reason().contains("нагрузка дня"),
                result.unplaced().get(0).reason());
    }

    @Test
    void ejectionRepairsADeadEndOfTheGreedyPass() {
        // Совместитель A жадно занимает понедельник, 1 пара, а штатный B может вести только
        // в это время. Единственная аудитория общая. Вытеснение переносит A на 2 пару.
        Teacher flexible = Fixtures.teacher("Гибкий", TeacherCategory.PART_TIME);
        Teacher rigid = Fixtures.teacher("Жёсткий", TeacherCategory.STAFF);

        TeacherSubject flexibleQualification = Fixtures.qualification(
                flexible, Fixtures.subject("Первый"));
        TeacherSubject rigidQualification = Fixtures.qualification(
                rigid, Fixtures.subject("Второй"));

        List<TeacherAvailability> availability = List.of(
                new TeacherAvailability(flexible, WeekDay.MONDAY, 1, 2, WeekType.EVERY_WEEK),
                new TeacherAvailability(rigid, WeekDay.MONDAY, 1, 1, WeekType.EVERY_WEEK));

        SolveResult result = solve(
                List.of(
                        Fixtures.seminar(flexibleQualification, Fixtures.group("Г-1", 25), 16),
                        Fixtures.seminar(rigidQualification, Fixtures.group("Г-2", 25), 16)),
                List.of(Fixtures.room("1", ClassroomType.REGULAR, 30)),
                availability,
                CONFIG);

        assertTrue(result.isComplete());
        assertEquals(1, result.repaired());
        assertEquals(1, result.greedyPlaced());
    }

    @Test
    void solverIsDeterministic() {
        SyntheticUniversity.Data data = SyntheticUniversity.generate(15, 7);

        SolveResult first = solve(data.requirements(), data.rooms(), data.availability(), CONFIG);

        SyntheticUniversity.Data again = SyntheticUniversity.generate(15, 7);
        SolveResult second = solve(again.requirements(), again.rooms(), again.availability(), CONFIG);

        assertEquals(first.placements(), second.placements());
    }

    @Test
    void generatedUniversityOfThirtyGroupsIsSolvedCorrectly() {
        SyntheticUniversity.Data data = SyntheticUniversity.generate(30, 1);

        SolveResult result = solve(data.requirements(), data.rooms(), data.availability(), CONFIG);

        assertNotNull(result);
        assertTrue(result.isComplete(), "Не размещено: " + result.unplaced().size());
    }
}
