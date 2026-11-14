package ru.miet.schedule.web;

import ru.miet.schedule.db.DbConfig;
import ru.miet.schedule.db.ScheduleData;
import ru.miet.schedule.db.ScheduleDataLoader;
import ru.miet.schedule.db.ScheduleWriter;
import ru.miet.schedule.db.ScheduleWriter.RunInfo;
import ru.miet.schedule.domain.Classroom;
import ru.miet.schedule.domain.WeekDay;
import ru.miet.schedule.engine.edit.LessonMover;
import ru.miet.schedule.engine.model.Problem;
import ru.miet.schedule.engine.model.ScheduledLesson;
import ru.miet.schedule.engine.solver.ConstructiveSolver;
import ru.miet.schedule.engine.solver.SolveResult;
import ru.miet.schedule.engine.solver.SolverConfig;
import ru.miet.schedule.engine.verify.ScheduleVerifier;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Слой данных для веб-интерфейса (модель в схеме MVC): всё, что страницам нужно от базы
 * и от алгоритма. Страницы и контроллер ничего не знают про JDBC.
 * Каждое обращение открывает своё соединение: нагрузка маленькая, пул пока не нужен.
 */
public class ScheduleService {

    /** Загруженный запуск вместе с данными семестра, к которому он относится. */
    public record RunView(ScheduleData data, RunInfo info, List<ScheduledLesson> lessons) {
    }

    /** Итог составления нового расписания. */
    public record GenerateResult(long runId, int placed, int unplaced, int violations, long elapsedMs) {
    }

    private final DbConfig config;
    private final long semesterId;
    private final SolverConfig solver;

    public ScheduleService(DbConfig config, long semesterId) {
        this(config, semesterId, SolverConfig.defaults());
    }

    public ScheduleService(DbConfig config, long semesterId, SolverConfig solver) {
        this.config = config;
        this.semesterId = semesterId;
        this.solver = solver;
    }

    public ScheduleData semesterData() throws SQLException {
        try (Connection c = config.connect()) {
            return ScheduleDataLoader.load(c, semesterId);
        }
    }

    public List<RunInfo> runs() throws SQLException {
        try (Connection c = config.connect()) {
            return ScheduleWriter.listRuns(c, semesterId);
        }
    }

    /** Данные для главной страницы одним соединением. */
    public record Home(ScheduleData data, List<RunInfo> runs) {
    }

    public Home home() throws SQLException {
        try (Connection c = config.connect()) {
            return new Home(
                    ScheduleDataLoader.load(c, semesterId),
                    ScheduleWriter.listRuns(c, semesterId));
        }
    }

    public Optional<RunView> run(long runId) throws SQLException {
        try (Connection c = config.connect()) {
            return loadRun(c, runId);
        }
    }

    private static Optional<RunView> loadRun(Connection c, long runId) throws SQLException {
        Optional<RunInfo> info = ScheduleWriter.findRun(c, runId);

        if (info.isEmpty()) {
            return Optional.empty();
        }

        ScheduleData data = ScheduleDataLoader.load(c, info.get().semesterId());
        List<ScheduledLesson> lessons = ScheduleWriter.loadRun(c, runId, data);

        return Optional.of(new RunView(data, info.get(), lessons));
    }

    /** Показатели одной версии для таблицы сравнения. */
    public record RunMetrics(RunInfo info, ru.miet.schedule.engine.verify.ScheduleMetrics metrics) {
    }

    /** Показатели последних версий (новые первыми) для сравнения. */
    public List<RunMetrics> compare(int limit) throws SQLException {
        try (Connection c = config.connect()) {
            List<RunMetrics> result = new java.util.ArrayList<>();

            for (RunInfo info : ScheduleWriter.listRuns(c, semesterId)) {
                if (result.size() >= limit) {
                    break;
                }

                result.add(new RunMetrics(info, ru.miet.schedule.engine.verify.ScheduleMetrics.of(
                        loadRun(c, info.id()).orElseThrow().lessons())));
            }

            return result;
        }
    }

    /** Модуль ручного переноса с правилами этого семестра. */
    public LessonMover mover(RunView view) {
        return new LessonMover(new LessonMover.Context(
                view.data().calendar(), view.data().availability(),
                solver.maxPairsPerDay()));
    }

    /** Итог ручного переноса: план (с нарушениями и предупреждениями) и был ли он применён. */
    public record EditResult(LessonMover.Plan plan, boolean applied) {
    }

    /**
     * Проверяет перенос занятия и, если разрешено и нарушений нет, записывает его в базу.
     * Перед записью расписание загружается заново, поэтому устаревшая страница не испортит данные,
     * а независимый верификатор подтверждает, что после переноса новых нарушений не появилось.
     */
    public synchronized EditResult editLesson(
            long runId,
            LessonMover.Key key,
            LessonMover.Scope scope,
            WeekDay day,
            int pair,
            long roomId,
            boolean apply) throws SQLException {

        try (Connection c = config.connect()) {
            RunView view = loadRun(c, runId)
                    .orElseThrow(() -> new IllegalArgumentException("Расписание № " + runId + " не найдено"));

            Classroom room = view.data().rooms().stream()
                    .filter(r -> r.getId() == roomId).findFirst().orElse(null);

            if (room == null) {
                return new EditResult(new LessonMover.Plan(
                        List.of(), List.of("Аудитория не найдена или выключена"), List.of()), false);
            }

            LessonMover.Plan plan = mover(view).plan(view.lessons(), key, scope, day, pair, room);

            if (!apply || !plan.valid()) {
                return new EditResult(plan, false);
            }

            List<ScheduledLesson> moved = LessonMover.applied(view.lessons(), plan);
            SolverConfig solverConfig = solver;
            Set<ru.miet.schedule.domain.TeachingRequirement> incomplete =
                    view.info().unplaced() > 0 ? new HashSet<>(view.data().requirements()) : Set.of();

            int before = ScheduleVerifier.verify(
                    view.data().requirements(), view.data().availability(), view.data().calendar(),
                    view.lessons(), solverConfig.maxPairsPerDay(), incomplete).size();
            List<String> after = ScheduleVerifier.verify(
                    view.data().requirements(), view.data().availability(), view.data().calendar(),
                    moved, solverConfig.maxPairsPerDay(), incomplete);

            if (after.size() > before) {
                throw new IllegalStateException(
                        "Проверка расписания после переноса нашла нарушения, перенос отменён: " + after.get(0));
            }

            for (LessonMover.Move move : plan.moves()) {
                ScheduleWriter.updateLesson(c, runId, move.from(), move.to());
            }

            ScheduleWriter.markEdited(c, runId);
            c.commit();

            return new EditResult(plan, true);
        }
    }

    /** Копирует расписание, чтобы править копию и не трогать исходное. */
    public synchronized long copyRun(long runId) throws SQLException {
        try (Connection c = config.connect()) {
            long copyId = ScheduleWriter.copyRun(c, runId);
            c.commit();
            return copyId;
        }
    }

    /** Составляет расписание заново и сохраняет как новый запуск. Одновременно выполняется один запуск. */
    public synchronized GenerateResult generate() throws SQLException {
        try (Connection c = config.connect()) {
            ScheduleData data = ScheduleDataLoader.load(c, semesterId);

            if (data.requirements().isEmpty()) {
                throw new IllegalStateException(
                        "Для семестра нет учебной нагрузки: нечего размещать");
            }

            SolverConfig solverConfig = solver;
            Problem problem = Problem.of(
                    data.requirements(), data.rooms(), data.availability(), data.calendar());
            SolveResult result = new ConstructiveSolver(solverConfig).solve(problem);
            List<ScheduledLesson> lessons = result.toScheduledLessons();

            List<String> violations = ScheduleVerifier.verify(
                    data.requirements(), data.availability(), data.calendar(),
                    lessons, solverConfig.maxPairsPerDay(), result.unplacedRequirements());

            String note = violations.isEmpty()
                    ? "веб"
                    : "веб, нарушений: " + violations.size();

            long runId = ScheduleWriter.saveRun(
                    c, semesterId, lessons, result.unplaced().size(), result.elapsedMillis(), note);
            c.commit();

            return new GenerateResult(
                    runId, lessons.size(), result.unplaced().size(),
                    violations.size(), result.elapsedMillis());
        }
    }
}
