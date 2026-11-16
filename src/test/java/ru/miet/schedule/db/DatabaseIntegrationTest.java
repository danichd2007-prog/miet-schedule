package ru.miet.schedule.db;

import org.junit.jupiter.api.Test;
import ru.miet.schedule.domain.Teacher;
import ru.miet.schedule.domain.TeacherCategory;
import ru.miet.schedule.domain.TeachingRequirement;
import ru.miet.schedule.domain.WeekDay;
import ru.miet.schedule.engine.model.Problem;
import ru.miet.schedule.engine.model.ScheduledLesson;
import ru.miet.schedule.engine.solver.ConstructiveSolver;
import ru.miet.schedule.engine.solver.SolveResult;
import ru.miet.schedule.engine.solver.SolverConfig;
import ru.miet.schedule.engine.verify.ScheduleVerifier;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Проверка работы с настоящим PostgreSQL. Если база недоступна, тесты пропускаются.
 *
 * Все изменения выполняются в транзакции, которая в конце откатывается,
 * поэтому данные в базе не меняются (в том числе применение schema.sql).
 */
class DatabaseIntegrationTest {

    private static final long SEMESTER = 1;

    private Connection open() throws SQLException {
        Connection connection = null;

        try {
            connection = DbConfig.fromClasspath().connect();
        } catch (SQLException e) {
            assumeTrue(false, "PostgreSQL недоступен: " + e.getMessage());
        }

        // ошибка в самом скрипте — это провал теста, а не повод его пропустить
        SchemaInstaller.install(connection);
        return connection;
    }

    @Test
    void schemaCanBeInstalledTwice() throws Exception {
        try (Connection c = open()) {
            SchemaInstaller.install(c);
            c.rollback();
        }
    }

    @Test
    void loadsSeedDataWithoutWarnings() throws Exception {
        try (Connection c = open()) {
            ScheduleData data = ScheduleDataLoader.load(c, SEMESTER);

            assertTrue(data.warnings().isEmpty(), () -> "Предупреждения: " + data.warnings());
            assertTrue(data.requirements().size() >= 7);
            assertTrue(data.rooms().size() >= 4);
            assertEquals(16, data.calendar().getWeekCount());

            for (TeachingRequirement r : data.requirements()) {
                assertTrue(r.getId() != null && r.getId() > 0);
            }

            c.rollback();
        }
    }

    @Test
    void partTimeTeacherIsLoadedWithCategoryAndDays() throws Exception {
        try (Connection c = open()) {
            ScheduleData data = ScheduleDataLoader.load(c, SEMESTER);

            Teacher guzhov = data.requirements().stream()
                    .map(TeachingRequirement::teacher)
                    .filter(t -> t.getLastName().equals("Гужов"))
                    .findFirst().orElseThrow();

            assertEquals(TeacherCategory.PART_TIME, guzhov.getCategory());

            long days = data.availability().stream()
                    .filter(a -> a.getTeacher().equals(guzhov))
                    .map(a -> a.getDay())
                    .distinct().count();

            assertEquals(2L, days);
            c.rollback();
        }
    }

    @Test
    void scheduleFromDatabaseIsCompleteAndRespectsPartTimeDays() throws Exception {
        try (Connection c = open()) {
            ScheduleData data = ScheduleDataLoader.load(c, SEMESTER);
            SolverConfig config = SolverConfig.defaults();

            Problem problem = Problem.of(
                    data.requirements(), data.rooms(), data.availability(), data.calendar());
            SolveResult result = new ConstructiveSolver(config).solve(problem);
            List<ScheduledLesson> lessons = result.toScheduledLessons();

            assertTrue(result.unplaced().isEmpty(), () -> "Не размещено: " + result.unplaced());

            List<String> violations = ScheduleVerifier.verify(
                    data.requirements(), data.availability(), data.calendar(),
                    lessons, config.maxPairsPerDay(), result.unplacedRequirements());

            assertTrue(violations.isEmpty(), () -> "Нарушения: " + violations);

            for (ScheduledLesson lesson : lessons) {
                if (lesson.requirement().teacher().getLastName().equals("Гужов")) {
                    assertTrue(lesson.day() == WeekDay.TUESDAY || lesson.day() == WeekDay.THURSDAY,
                            "Совместитель поставлен в недопустимый день: " + lesson.day());
                }
            }

            c.rollback();
        }
    }

    @Test
    void savedRunIsReadBackIdentically() throws Exception {
        try (Connection c = open()) {
            ScheduleData data = ScheduleDataLoader.load(c, SEMESTER);

            Problem problem = Problem.of(
                    data.requirements(), data.rooms(), data.availability(), data.calendar());
            SolveResult result = new ConstructiveSolver(SolverConfig.defaults()).solve(problem);
            List<ScheduledLesson> lessons = result.toScheduledLessons();

            long runId = ScheduleWriter.saveRun(c, SEMESTER, lessons, 0, result.elapsedMillis(), "тест");

            assertEquals(OptionalLong.of(runId), ScheduleWriter.latestRun(c, SEMESTER));

            List<ScheduledLesson> back = ScheduleWriter.loadRun(c, runId, data);

            assertEquals(lessons.size(), back.size());
            assertEquals(lessons, back);

            c.rollback();
        }
    }

    @Test
    void unavailableRoomIsNotLoaded() throws Exception {
        try (Connection c = open()) {
            int before = ScheduleDataLoader.load(c, SEMESTER).rooms().size();

            try (var st = c.createStatement()) {
                st.executeUpdate("UPDATE classrooms SET is_available = FALSE WHERE number = '3229'");
            }

            assertEquals(before - 1, ScheduleDataLoader.load(c, SEMESTER).rooms().size());
            c.rollback();
        }
    }
}
