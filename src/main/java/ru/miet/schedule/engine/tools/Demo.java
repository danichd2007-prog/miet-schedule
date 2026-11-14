package ru.miet.schedule.engine.tools;

import ru.miet.schedule.domain.AcademicCalendar;
import ru.miet.schedule.domain.StudentGroup;
import ru.miet.schedule.domain.WeekDay;
import ru.miet.schedule.engine.model.Problem;
import ru.miet.schedule.engine.model.ScheduledLesson;
import ru.miet.schedule.engine.solver.ConstructiveSolver;
import ru.miet.schedule.engine.solver.SolveResult;
import ru.miet.schedule.engine.solver.SolverConfig;
import ru.miet.schedule.engine.synthetic.SyntheticUniversity;
import ru.miet.schedule.engine.verify.ScheduleMetrics;
import ru.miet.schedule.engine.verify.ScheduleVerifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Демонстрация без базы данных: строит искусственный университет, составляет расписание,
 * проверяет его независимым верификатором, печатает показатели удобства и сетку недели
 * для одной группы, а всё расписание сохраняет в CSV (открывается в Excel).
 *
 * Аргументы (все необязательны): число групп (20), зерно случайности (1),
 * коэффициент числа аудиторий (1.0; меньше единицы делает задачу теснее), имя группы для сетки.
 */
public final class Demo {

    private static final int CELL_WIDTH = 17;

    private Demo() {
    }

    public static void main(String[] args) throws IOException {
        int groupCount = args.length > 0 ? Integer.parseInt(args[0]) : 20;
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 1;
        double roomScale = args.length > 2 ? Double.parseDouble(args[2]) : 1.0;
        String wantedGroup = args.length > 3 ? args[3] : null;

        SyntheticUniversity.Data data = SyntheticUniversity.generate(groupCount, seed, roomScale);
        SolverConfig config = SolverConfig.defaults();

        Problem problem = Problem.of(
                data.requirements(), data.rooms(), data.availability(), data.calendar());
        SolveResult result = new ConstructiveSolver(config).solve(problem);
        List<ScheduledLesson> lessons = result.toScheduledLessons();

        List<String> violations = ScheduleVerifier.verify(
                data.requirements(), data.availability(), data.calendar(),
                lessons, config.maxPairsPerDay(), result.unplacedRequirements());

        System.out.println("=== Университет: " + data.groups().size() + " групп, "
                + problem.teacherCount() + " преподавателей, " + problem.roomCount() + " аудиторий");
        System.out.println("=== Серий занятий: " + problem.seriesCount()
                + ", размещено: " + result.placements().size()
                + ", не размещено: " + result.unplaced().size()
                + ", время решения: " + result.elapsedMillis() + " мс");
        System.out.println("=== Проверка независимым верификатором: "
                + (violations.isEmpty() ? "нарушений нет" : violations.size() + " нарушений"));

        for (String violation : violations.stream().limit(5).toList()) {
            System.out.println("    ! " + violation);
        }

        for (SolveResult.Unplaced unplaced : result.unplaced().stream().limit(5).toList()) {
            System.out.println("    не размещено: " + unplaced.reason());
        }

        ScheduleMetrics metrics = ScheduleMetrics.of(lessons);

        System.out.printf("=== Удобство (4 типовые недели): окон у студентов %d "
                        + "(%.2f на группу в неделю), окон у преподавателей %d, "
                        + "занятий в субботу %d, после 4 пары %d из %d%n",
                metrics.studentWindows(), metrics.studentWindowsPerGroupWeek(),
                metrics.teacherWindows(), metrics.saturdayLessons(),
                metrics.lateLessons(), metrics.lessons());

        StudentGroup group = pickGroup(data.groups(), wantedGroup);

        System.out.println();
        printWeek(group, lessons, 1, "первая неделя (числитель)");
        printWeek(group, lessons, 2, "вторая неделя (знаменатель)");

        Path csv = Path.of("demo-schedule.csv");
        writeCsv(csv, lessons, data.calendar());
        System.out.println("Всё расписание сохранено: " + csv.toAbsolutePath()
                + " (" + lessons.size() + " занятий)");
    }

    static StudentGroup pickGroup(List<StudentGroup> groups, String name) {
        if (name != null) {
            for (StudentGroup group : groups) {
                if (group.getName().equals(name)) {
                    return group;
                }
            }

            System.out.println("Группы «" + name + "» нет, показываю первую. Примеры имён: "
                    + groups.stream().limit(5).map(StudentGroup::getName).toList());
        }

        return groups.get(0);
    }

    static void printWeek(
            StudentGroup group, List<ScheduledLesson> lessons, int week, String title) {

        Map<String, String> cells = new HashMap<>();

        for (ScheduledLesson lesson : lessons) {
            if (lesson.week() != week || !lesson.requirement().groups().contains(group)) {
                continue;
            }

            for (int k = 0; k < lesson.duration(); k++) {
                String text = k == 0
                        ? shorten(lesson.requirement().subject().getName()) + " "
                        + kind(lesson) + " " + lesson.room().getNumber()
                        : "  ^ блок";

                cells.put(lesson.day().ordinal() + ":" + (lesson.pair() + k), text);
            }
        }

        System.out.println("Группа " + group.getName() + ", " + title);

        StringBuilder header = new StringBuilder("пара ");

        for (WeekDay day : WeekDay.values()) {
            header.append(pad(day.getRussianName()));
        }

        System.out.println(header);
        System.out.println("-".repeat(header.length()));

        for (int pair = 1; pair <= 8; pair++) {
            StringBuilder row = new StringBuilder(String.format("%-5d", pair));

            for (WeekDay day : WeekDay.values()) {
                row.append(pad(cells.getOrDefault(day.ordinal() + ":" + pair, "")));
            }

            System.out.println(row);
        }

        System.out.println();
    }

    private static String kind(ScheduledLesson lesson) {
        return switch (lesson.requirement().getLessonType()) {
            case LECTURE -> "лек";
            case SEMINAR -> "сем";
            case LABORATORY -> "лаб";
            default -> lesson.requirement().getLessonType().name().toLowerCase();
        };
    }

    private static String shorten(String text) {
        return text.length() <= 11 ? text : text.substring(0, 11);
    }

    private static String pad(String text) {
        String cut = text.length() > CELL_WIDTH - 1 ? text.substring(0, CELL_WIDTH - 1) : text;
        return String.format("%-" + CELL_WIDTH + "s", cut);
    }

    static void writeCsv(
            Path path, List<ScheduledLesson> lessons, AcademicCalendar calendar)
            throws IOException {

        List<ScheduledLesson> sorted = new ArrayList<>(lessons);

        sorted.sort(Comparator
                .comparingInt(ScheduledLesson::week)
                .thenComparingInt(l -> l.day().ordinal())
                .thenComparingInt(ScheduledLesson::pair));

        StringBuilder out = new StringBuilder("﻿");
        out.append("Неделя;Дата;День;Пара;Группы;Предмет;Тип;Преподаватель;Аудитория;Корпус\n");

        for (ScheduledLesson lesson : sorted) {
            String groups = String.join(" + ",
                    lesson.requirement().groups().stream().map(StudentGroup::getName).toList());

            String pairs = lesson.duration() == 1
                    ? String.valueOf(lesson.pair())
                    : lesson.pair() + "-" + lesson.lastPair();

            out.append(lesson.week()).append(';')
                    .append(lesson.date(calendar)).append(';')
                    .append(lesson.day().getRussianName()).append(';')
                    .append(pairs).append(';')
                    .append(csv(groups)).append(';')
                    .append(csv(lesson.requirement().subject().getName())).append(';')
                    .append(kind(lesson)).append(';')
                    .append(csv(lesson.requirement().teacher().getShortName())).append(';')
                    .append(csv(lesson.room().getNumber())).append(';')
                    .append(csv(lesson.room().getBuilding())).append('\n');
        }

        Files.writeString(path, out, StandardCharsets.UTF_8);
    }

    private static String csv(String value) {
        return value.contains(";") || value.contains("\"")
                ? "\"" + value.replace("\"", "\"\"") + "\""
                : value;
    }
}
