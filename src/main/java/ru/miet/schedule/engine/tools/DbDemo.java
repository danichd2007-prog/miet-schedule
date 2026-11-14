package ru.miet.schedule.engine.tools;

import ru.miet.schedule.db.DbConfig;
import ru.miet.schedule.db.ScheduleData;
import ru.miet.schedule.db.ScheduleDataLoader;
import ru.miet.schedule.db.ScheduleWriter;
import ru.miet.schedule.db.SchemaInstaller;
import ru.miet.schedule.domain.StudentGroup;
import ru.miet.schedule.engine.model.Problem;
import ru.miet.schedule.engine.model.ScheduledLesson;
import ru.miet.schedule.engine.solver.ConstructiveSolver;
import ru.miet.schedule.engine.solver.SolveResult;
import ru.miet.schedule.engine.solver.SolverConfig;
import ru.miet.schedule.engine.verify.ScheduleMetrics;
import ru.miet.schedule.engine.verify.ScheduleVerifier;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;

/**
 * Составляет расписание по данным из PostgreSQL и сохраняет результат обратно в базу.
 *
 * Аргументы (все необязательны):
 *   init            сначала применить db/schema.sql (создать таблицы и тестовые данные)
 *   semester=N      номер семестра (по умолчанию 1)
 *   group=ИМЯ       группа, для которой напечатать сетку недели
 *   nosave          не сохранять результат в базу
 *
 * Параметры подключения берутся из src/main/resources/database.properties.
 */
public final class DbDemo {

    private DbDemo() {
    }

    public static void main(String[] args) throws Exception {
        boolean init = false;
        boolean save = true;
        long semesterId = 1;
        String wantedGroup = null;

        for (String arg : args) {
            if (arg.equals("init")) {
                init = true;
            } else if (arg.equals("nosave")) {
                save = false;
            } else if (arg.startsWith("semester=")) {
                semesterId = Long.parseLong(arg.substring("semester=".length()));
            } else if (arg.startsWith("group=")) {
                wantedGroup = arg.substring("group=".length());
            } else {
                System.out.println("Неизвестный аргумент: " + arg);
            }
        }

        DbConfig config = DbConfig.fromClasspath();
        System.out.println("=== База: " + config.url());

        if (init && config.createDatabaseIfMissing()) {
            System.out.println("=== База данных создана");
        }

        try (Connection connection = config.connect()) {
            if (init) {
                SchemaInstaller.install(connection);
                connection.commit();
                System.out.println("=== Схема и тестовые данные применены");
            }

            ScheduleData data = ScheduleDataLoader.load(connection, semesterId);

            System.out.println("=== Семестр: " + data.semesterName() + ", недель: " + data.calendar().getWeekCount());
            System.out.println("=== Загружено: групп " + data.groups().size()
                    + ", аудиторий " + data.rooms().size()
                    + ", нагрузок " + data.requirements().size()
                    + ", записей доступности " + data.availability().size());

            for (String warning : data.warnings()) {
                System.out.println("    ! " + warning);
            }

            if (data.requirements().isEmpty()) {
                System.out.println("Нет нагрузки для семестра " + semesterId
                        + ". Запустите с аргументом init, чтобы создать тестовые данные.");
                return;
            }

            SolverConfig solverConfig = SolverConfig.defaults();
            Problem problem = Problem.of(
                    data.requirements(), data.rooms(), data.availability(), data.calendar());
            SolveResult result = new ConstructiveSolver(solverConfig).solve(problem);
            List<ScheduledLesson> lessons = result.toScheduledLessons();

            List<String> violations = ScheduleVerifier.verify(
                    data.requirements(), data.availability(), data.calendar(),
                    lessons, solverConfig.maxPairsPerDay(), result.unplacedRequirements());

            System.out.println("=== Серий: " + problem.seriesCount()
                    + ", размещено: " + result.placements().size()
                    + ", не размещено: " + result.unplaced().size()
                    + ", время: " + result.elapsedMillis() + " мс");
            System.out.println("=== Верификатор: "
                    + (violations.isEmpty() ? "нарушений нет" : violations.size() + " нарушений"));

            violations.stream().limit(5).forEach(v -> System.out.println("    ! " + v));
            result.unplaced().stream().limit(5)
                    .forEach(u -> System.out.println("    не размещено: " + u.reason()));

            ScheduleMetrics metrics = ScheduleMetrics.of(lessons);
            System.out.printf("=== Удобство: окон у студентов %d, у преподавателей %d, "
                            + "в субботу %d, после 4 пары %d из %d%n",
                    metrics.studentWindows(), metrics.teacherWindows(),
                    metrics.saturdayLessons(), metrics.lateLessons(), metrics.lessons());

            if (save) {
                long runId = ScheduleWriter.saveRun(
                        connection, semesterId, lessons, result.unplaced().size(),
                        result.elapsedMillis(), "DbDemo");
                connection.commit();
                System.out.println("=== Сохранено в базу: запуск № " + runId
                        + " (" + lessons.size() + " занятий, таблицы schedule_runs и schedule_lessons)");
            }

            if (!data.groups().isEmpty()) {
                StudentGroup group = Demo.pickGroup(data.groups(), wantedGroup);
                System.out.println();
                Demo.printWeek(group, lessons, 1, "первая неделя (числитель)");
                Demo.printWeek(group, lessons, 2, "вторая неделя (знаменатель)");
            }

            Path csv = Path.of("db-schedule.csv");
            Demo.writeCsv(csv, lessons, data.calendar());
            System.out.println("Всё расписание сохранено: " + csv.toAbsolutePath());
        }
    }
}
