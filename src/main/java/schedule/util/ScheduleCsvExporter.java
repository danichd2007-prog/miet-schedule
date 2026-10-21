    package schedule.util;

import schedule.calendar.AcademicCalendar;
import schedule.model.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ScheduleCsvExporter {

    private record SeriesKey(
            StudentGroup group,
            TeachingRequirement requirement,
            WeekDay day,
            int firstPair,
            int duration) {
    }

    private record SeriesData(
            Set<Integer> weeks,
            Set<String> rooms) {
    }

    public static void export(
            Schedule schedule,
            AcademicCalendar calendar,
            Collection<StudentGroup> groups,
            Path directory) throws IOException {

        Files.createDirectories(directory);

        Map<SeriesKey, SeriesData> series =
                buildSeries(schedule);

        for (StudentGroup group : groups) {

            exportDetailed(
                    schedule,
                    calendar,
                    Set.of(group),
                    series,
                    directory.resolve(
                            group.getName() + "_detailed.csv"
                    )
            );

            exportSeries(
                    series,
                    calendar,
                    Set.of(group),
                    directory.resolve(
                            group.getName() + "_series.csv"
                    )
            );

            exportGrid(
                    schedule,
                    calendar,
                    group,
                    directory.resolve(
                            group.getName() + "_timetable.csv"
                    )
            );
        }

        exportDetailed(
                schedule,
                calendar,
                new HashSet<>(groups),
                series,
                directory.resolve("ALL_detailed.csv")
        );

        exportSeries(
                series,
                calendar,
                new HashSet<>(groups),
                directory.resolve("ALL_series.csv")
        );
    }

    private static Map<SeriesKey, SeriesData> buildSeries(
            Schedule schedule) {

        Map<SeriesKey, SeriesData> result =
                new LinkedHashMap<>();

        for (ScheduleEntry entry : schedule.getEntries()) {

            Lesson lesson = entry.getLesson();
            TimeSlot slot = entry.getTimeSlot();

            for (StudentGroup group : getGroups(lesson)) {

                SeriesKey key = new SeriesKey(
                        group,
                        lesson.getRequirement(),
                        slot.getDay(),
                        slot.getLessonNumber(),
                        lesson.getDuration()
                );

                SeriesData data = result.computeIfAbsent(
                        key,
                        ignored -> new SeriesData(
                                new TreeSet<>(),
                                new TreeSet<>()
                        )
                );

                data.weeks().add(
                        slot.getWeekNumber()
                );

                data.rooms().add(
                        entry.getClassroom().getBuilding()
                                + "-"
                                + entry.getClassroom().getNumber()
                );
            }
        }

        return result;
    }

    private static String pattern(
            Set<Integer> weeks,
            AcademicCalendar calendar) {

        for (WeekType type : List.of(
                WeekType.EVERY_WEEK,
                WeekType.NUMERATOR,
                WeekType.DENOMINATOR,
                WeekType.F_NUMERATOR,
                WeekType.F_DENOMINATOR,
                WeekType.S_NUMERATOR,
                WeekType.S_DENOMINATOR)) {

            Set<Integer> expected = new HashSet<>();

            for (int week = 1;
                 week <= calendar.getWeekCount();
                 week++) {

                int phase = (week - 1) % 4;

                boolean applies = switch (type) {

                    case EVERY_WEEK -> true;

                    case NUMERATOR ->
                            phase == 0 || phase == 2;

                    case DENOMINATOR ->
                            phase == 1 || phase == 3;

                    case F_NUMERATOR ->
                            phase == 0;

                    case F_DENOMINATOR ->
                            phase == 1;

                    case S_NUMERATOR ->
                            phase == 2;

                    case S_DENOMINATOR ->
                            phase == 3;
                };

                if (applies) {
                    expected.add(week);
                }
            }

            if (weeks.equals(expected)) {
                return type.getRussianName();
            }
        }

        return "Нестандартно (" + weeks + ")";
    }

    private static List<SeriesKey> sortedKeys(
            Map<SeriesKey, SeriesData> series,
            Set<StudentGroup> groups) {

        return series.keySet().stream()
                .filter(key -> groups.contains(key.group()))
                .sorted(
                        Comparator
                                .comparing(
                                        (SeriesKey key) ->
                                                key.group().getName()
                                )
                                .thenComparingInt(
                                        key -> key.day().ordinal()
                                )
                                .thenComparingInt(
                                        SeriesKey::firstPair
                                )
                                .thenComparing(
                                        key -> key.requirement()
                                                .getTeacherSubject()
                                                .getSubject()
                                                .getName()
                                )
                )
                .toList();
    }

    private static void exportSeries(
            Map<SeriesKey, SeriesData> series,
            AcademicCalendar calendar,
            Set<StudentGroup> groups,
            Path file) throws IOException {

        List<String> lines = new ArrayList<>();

        lines.add(
                "Группа;День;Пара;Периодичность;Дисциплина;"
                        + "Тип занятия;Преподаватель;Аудитория;Недели"
        );

        for (SeriesKey key : sortedKeys(series, groups)) {

            SeriesData data = series.get(key);

            LessonType type =
                    key.requirement().getLessonType();

            lines.add(
                    row(
                            key.group().getName(),
                            key.day().toString(),
                            pair(
                                    key.firstPair(),
                                    key.duration()
                            ),
                            pattern(
                                    data.weeks(),
                                    calendar
                            ),
                            key.requirement()
                                    .getTeacherSubject()
                                    .getSubject()
                                    .getName(),
                            type.toString(),
                            key.requirement()
                                    .getTeacherSubject()
                                    .getTeacher()
                                    .getShortName(),
                            String.join(
                                    " | ",
                                    data.rooms()
                            ),
                            data.weeks().toString()
                    )
            );
        }

        write(file, lines);
    }

    private static void exportDetailed(
            Schedule schedule,
            AcademicCalendar calendar,
            Set<StudentGroup> groups,
            Map<SeriesKey, SeriesData> series,
            Path file) throws IOException {

        List<String> lines = new ArrayList<>();

        lines.add(
                "Группа;Неделя;Тип недели;Дата;День;Пара;"
                        + "Периодичность занятия;Дисциплина;"
                        + "Тип занятия;Преподаватель;Аудитория"
        );

        List<ScheduleEntry> entries =
                new ArrayList<>(schedule.getEntries());

        entries.sort(
                Comparator
                        .comparingInt(
                                (ScheduleEntry e) ->
                                        e.getTimeSlot()
                                                .getWeekNumber()
                        )
                        .thenComparingInt(
                                e -> e.getTimeSlot()
                                        .getDay()
                                        .ordinal()
                        )
                        .thenComparingInt(
                                e -> e.getTimeSlot()
                                        .getLessonNumber()
                        )
        );

        for (ScheduleEntry entry : entries) {

            Lesson lesson = entry.getLesson();
            TimeSlot slot = entry.getTimeSlot();

            for (StudentGroup group : getGroups(lesson)) {

                if (!groups.contains(group)) {
                    continue;
                }

                SeriesKey key = new SeriesKey(
                        group,
                        lesson.getRequirement(),
                        slot.getDay(),
                        slot.getLessonNumber(),
                        lesson.getDuration()
                );

                lines.add(
                        row(
                                group.getName(),
                                String.valueOf(
                                        slot.getWeekNumber()
                                ),
                                calendar.getWeekType(
                                        slot.getWeekNumber()
                                ).getRussianName(),
                                calendar.getDate(
                                        slot.getWeekNumber(),
                                        slot.getDay()
                                ).toString(),
                                slot.getDay().toString(),
                                pair(
                                        slot.getLessonNumber(),
                                        lesson.getDuration()
                                ),
                                pattern(
                                        series.get(key).weeks(),
                                        calendar
                                ),
                                lesson.getSubject()
                                        .getName(),
                                lesson.getLessonType()
                                        .toString(),
                                lesson.getTeacher()
                                        .getShortName(),
                                entry.getClassroom()
                                        .getBuilding()
                                        + "-"
                                        + entry.getClassroom()
                                        .getNumber()
                        )
                );
            }
        }

        write(file, lines);
    }

    private static void exportGrid(
            Schedule schedule,
            AcademicCalendar calendar,
            StudentGroup group,
            Path file) throws IOException {

        List<String> lines = new ArrayList<>();

        lines.add(
                "День;Пара;Первый числитель;"
                        + "Первый знаменатель;"
                        + "Второй числитель;"
                        + "Второй знаменатель"
        );

        for (WeekDay day : WeekDay.values()) {

            for (int pair = 1; pair <= 8; pair++) {

                List<String> cells =
                        new ArrayList<>(
                                List.of(
                                        day.toString(),
                                        String.valueOf(pair)
                                )
                        );

                for (int phase = 0; phase < 4; phase++) {

                    Map<String, Set<Integer>> descriptions =
                            new LinkedHashMap<>();

                    for (ScheduleEntry entry :
                            schedule.getEntries()) {

                        Lesson lesson = entry.getLesson();
                        TimeSlot slot = entry.getTimeSlot();

                        if (!getGroups(lesson).contains(group)
                                || slot.getDay() != day
                                || (slot.getWeekNumber() - 1) % 4
                                != phase
                                || pair < slot.getLessonNumber()
                                || pair >= slot.getLessonNumber()
                                + lesson.getDuration()) {

                            continue;
                        }

                        String value =
                                lesson.getSubject().getName()
                                        + " ("
                                        + lesson.getLessonType()
                                        + "), "
                                        + lesson.getTeacher()
                                        .getShortName()
                                        + ", ауд. "
                                        + entry.getClassroom()
                                        .getNumber();

                        descriptions.computeIfAbsent(
                                value,
                                ignored -> new TreeSet<>()
                        ).add(slot.getWeekNumber());
                    }

                    int totalPhaseWeeks = 0;

                    for (int week = 1;
                         week <= calendar.getWeekCount();
                         week++) {

                        if ((week - 1) % 4 == phase) {
                            totalPhaseWeeks++;
                        }
                    }

                    List<String> values =
                            new ArrayList<>();

                    for (var item : descriptions.entrySet()) {

                        String value = item.getKey();

                        if (item.getValue().size()
                                != totalPhaseWeeks) {

                            value += " [недели: "
                                    + item.getValue()
                                    + "]";
                        }

                        values.add(value);
                    }

                    cells.add(
                            String.join(" | ", values)
                    );
                }

                lines.add(
                        row(
                                cells.toArray(String[]::new)
                        )
                );
            }
        }

        write(file, lines);
    }

    private static String pair(
            int start,
            int duration) {

        return duration == 2
                ? start + "-" + (start + 1)
                : String.valueOf(start);
    }

    private static List<StudentGroup> getGroups(
            Lesson lesson) {

        return lesson.getStream() == null
                ? List.of(lesson.getGroup())
                : lesson.getStream().getGroups();
    }

    private static String row(String... cells) {

        List<String> escaped = new ArrayList<>();

        for (String cell : cells) {

            escaped.add(
                    "\"" + cell.replace("\"", "\"\"") + "\""
            );
        }

        return String.join(";", escaped);
    }

    private static void write(
            Path file,
            List<String> lines) throws IOException {

        Files.writeString(
                file,
                "\uFEFF"
                        + String.join("\r\n", lines)
                        + "\r\n",
                StandardCharsets.UTF_8
        );
    }
}
