package schedule.util;

import schedule.calendar.AcademicCalendar;
import schedule.model.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ScheduleCsvExporter {

    private static final String SEPARATOR = ";";

    public static void export(
            Schedule schedule,
            AcademicCalendar calendar,
            Collection<StudentGroup> groups,
            Path directory) throws IOException {

        Files.createDirectories(directory);

        for (StudentGroup group : groups) {
            exportDetailed(
                    schedule,
                    calendar,
                    List.of(group),
                    directory.resolve(
                            group.getName() + "_detailed.csv"
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
                groups,
                directory.resolve("ALL_detailed.csv")
        );
    }

    private static void exportDetailed(
            Schedule schedule,
            AcademicCalendar calendar,
            Collection<StudentGroup> groups,
            Path file) throws IOException {

        List<String> lines = new ArrayList<>();

        lines.add(
                "Группа;Неделя;Тип недели;Дата;"
                        + "День;Пара;Дисциплина;Тип занятия;"
                        + "Преподаватель;Аудитория"
        );

        List<ScheduleEntry> entries =
                new ArrayList<>(schedule.getEntries());

        entries.sort(Comparator
                .comparingInt(
                        (ScheduleEntry e) ->
                                e.getTimeSlot().getWeekNumber()
                )
                .thenComparingInt(
                        e -> e.getTimeSlot().getDay().ordinal()
                )
                .thenComparingInt(
                        e -> e.getTimeSlot().getLessonNumber()
                ));

        for (ScheduleEntry entry : entries) {

            Lesson lesson = entry.getLesson();
            TimeSlot slot = entry.getTimeSlot();

            for (StudentGroup group : getGroups(lesson)) {

                if (!groups.contains(group)) {
                    continue;
                }

                String pair = lesson.getDuration() == 2
                        ? slot.getLessonNumber() + "-"
                        + (slot.getLessonNumber() + 1)
                        : String.valueOf(slot.getLessonNumber());

                lines.add(String.join(SEPARATOR,
                        csv(group.getName()),
                        String.valueOf(slot.getWeekNumber()),
                        csv(calendar.getWeekType(
                                slot.getWeekNumber()).toString()),
                        csv(calendar.getDate(
                                slot.getWeekNumber(),
                                slot.getDay()).toString()),
                        csv(slot.getDay().toString()),
                        csv(pair),
                        csv(lesson.getSubject().getName()),
                        csv(lesson.getLessonType().toString()),
                        csv(lesson.getTeacher().getShortName()),
                        csv(entry.getClassroom().getNumber())
                ));
            }
        }

        writeCsv(file, lines);
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

                List<String> cells = new ArrayList<>();

                cells.add(day.toString());
                cells.add(String.valueOf(pair));

                for (int phase = 0; phase < 4; phase++) {

                    Map<String, Set<Integer>> lessons =
                            new LinkedHashMap<>();

                    for (ScheduleEntry entry
                            : schedule.getEntries()) {

                        Lesson lesson = entry.getLesson();
                        TimeSlot slot = entry.getTimeSlot();

                        if (!getGroups(lesson).contains(group)) {
                            continue;
                        }

                        if (slot.getDay() != day) {
                            continue;
                        }

                        int start = slot.getLessonNumber();
                        int end = start + lesson.getDuration() - 1;

                        if (pair < start || pair > end) {
                            continue;
                        }

                        if ((slot.getWeekNumber() - 1) % 4
                                != phase) {
                            continue;
                        }

                        String description =
                                lesson.getSubject().getName()
                                        + " (" + lesson.getLessonType()
                                        + "), "
                                        + lesson.getTeacher().getShortName()
                                        + ", ауд. "
                                        + entry.getClassroom().getNumber();

                        lessons.computeIfAbsent(
                                description,
                                k -> new TreeSet<>()
                        ).add(slot.getWeekNumber());
                    }

                    List<String> descriptions =
                            new ArrayList<>();

                    int totalPhaseWeeks = 0;

                    for (int week = 1;
                         week <= calendar.getWeekCount();
                         week++) {

                        if ((week - 1) % 4 == phase) {
                            totalPhaseWeeks++;
                        }
                    }

                    for (var item : lessons.entrySet()) {

                        String value = item.getKey();

                        if (item.getValue().size()
                                != totalPhaseWeeks) {

                            value += " [недели: "
                                    + item.getValue() + "]";
                        }

                        descriptions.add(value);
                    }

                    cells.add(
                            String.join(" | ", descriptions)
                    );
                }

                List<String> escaped = new ArrayList<>();

                for (String cell : cells) {
                    escaped.add(csv(cell));
                }

                lines.add(String.join(SEPARATOR, escaped));
            }
        }

        writeCsv(file, lines);
    }

    private static List<StudentGroup> getGroups(
            Lesson lesson) {

        return lesson.getStream() != null
                ? lesson.getStream().getGroups()
                : List.of(lesson.getGroup());
    }

    private static String csv(String value) {

        if (value == null) {
            return "";
        }

        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static void writeCsv(
            Path file,
            List<String> lines) throws IOException {

        String content =
                "\uFEFF" + String.join("\r\n", lines) + "\r\n";

        Files.writeString(
                file,
                content,
                StandardCharsets.UTF_8
        );
    }
}
