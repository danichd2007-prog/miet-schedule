package ru.miet.schedule.console;

import ru.miet.schedule.domain.AcademicCalendar;
import ru.miet.schedule.domain.StudentGroup;
import ru.miet.schedule.domain.WeekDay;
import ru.miet.schedule.engine.model.ScheduledLesson;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Текстовое представление расписания: сетка недели для консоли и CSV для Excel. */
public final class Timetables {

    private static final int CELL_WIDTH = 18;
    private static final int PAIRS = 8;

    private Timetables() {
    }

    /**
     * Сетка «пара × день». Каждая пара занимает две строки: сверху предмет (длинное название
     * обрезается с «…»), снизу тип и аудитория, поэтому номер аудитории никогда не теряется.
     */
    public static String weekGrid(
            String title, List<ScheduledLesson> lessons, int week, Predicate<ScheduledLesson> filter) {

        Map<String, String[]> cells = new HashMap<>();

        for (ScheduledLesson lesson : lessons) {
            if (lesson.week() != week || !filter.test(lesson)) {
                continue;
            }

            for (int k = 0; k < lesson.duration(); k++) {
                String[] text = k == 0
                        ? new String[]{lesson.requirement().subject().getName(),
                        kind(lesson) + " " + lesson.room().getNumber()}
                        : new String[]{"  ^ продолжение", ""};

                String key = lesson.day().ordinal() + ":" + (lesson.pair() + k);
                String[] old = cells.get(key);
                cells.put(key, old == null ? text : new String[]{old[0], old[1] + " +ещё"});
            }
        }

        StringBuilder out = new StringBuilder(title).append('\n');
        StringBuilder header = new StringBuilder("пара ");

        for (WeekDay day : WeekDay.values()) {
            header.append(pad(day.getRussianName()));
        }

        String line = "-".repeat(header.toString().stripTrailing().length());
        out.append(header.toString().stripTrailing()).append('\n').append(line).append('\n');

        for (int pair = 1; pair <= PAIRS; pair++) {
            StringBuilder top = new StringBuilder(String.format("%-5d", pair));
            StringBuilder bottom = new StringBuilder("     ");

            for (WeekDay day : WeekDay.values()) {
                String[] cell = cells.getOrDefault(day.ordinal() + ":" + pair, new String[]{"", ""});
                top.append(pad(cell[0]));
                bottom.append(pad(cell[1]));
            }

            out.append(top.toString().stripTrailing()).append('\n');
            out.append(bottom.toString().stripTrailing()).append('\n');
            out.append(line).append('\n');
        }

        return out.toString();
    }

    /** Список занятий недели с группами, по порядку: преподаватель, аудитория и группы; удобно для расписания преподавателя и аудитории. */
    public static String lessonList(
            List<ScheduledLesson> lessons, int week, Predicate<ScheduledLesson> filter) {

        List<ScheduledLesson> mine = lessons.stream()
                .filter(l -> l.week() == week && filter.test(l))
                .sorted(Comparator.comparingInt((ScheduledLesson l) -> l.day().ordinal())
                        .thenComparingInt(ScheduledLesson::pair))
                .toList();

        if (mine.isEmpty()) {
            return "Занятий на этой неделе нет.\n";
        }

        StringBuilder out = new StringBuilder("Занятия подробно:\n");

        for (ScheduledLesson l : mine) {
            String pairs = l.duration() == 1 ? String.valueOf(l.pair()) : l.pair() + "-" + l.lastPair();
            String groups = String.join(", ",
                    l.requirement().groups().stream().map(StudentGroup::getName).toList());

            out.append(String.format("  %-11s пара %-3s %s (%s), %s, ауд. %s, группы: %s%n",
                    l.day().getRussianName(), pairs, l.requirement().subject().getName(),
                    kind(l), l.requirement().teacher().getShortName(), l.room().getNumber(), groups));
        }

        return out.toString();
    }

    /** Короткая подпись типа занятия. */
    public static String kind(ScheduledLesson lesson) {
        return switch (lesson.requirement().getLessonType()) {
            case LECTURE -> "лек";
            case SEMINAR -> "сем";
            case LABORATORY -> "лаб";
            default -> lesson.requirement().getLessonType().name().toLowerCase();
        };
    }

    /** CSV с разделителем «;» и BOM, чтобы Excel сразу открыл русский текст. */
    public static String csv(List<ScheduledLesson> lessons, AcademicCalendar calendar) {
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
                    .append(cell(groups)).append(';')
                    .append(cell(lesson.requirement().subject().getName())).append(';')
                    .append(kind(lesson)).append(';')
                    .append(cell(lesson.requirement().teacher().getShortName())).append(';')
                    .append(cell(lesson.room().getNumber())).append(';')
                    .append(cell(lesson.room().getBuilding())).append('\n');
        }

        return out.toString();
    }

    public static void writeCsv(Path path, List<ScheduledLesson> lessons, AcademicCalendar calendar)
            throws IOException {
        Path parent = path.toAbsolutePath().getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }

        Files.writeString(path, csv(lessons, calendar), StandardCharsets.UTF_8);
    }

    /** Экранирует значение для CSV и не даёт Excel принять текст за формулу. */
    static String cell(String value) {
        String text = value == null ? "" : value;

        if (!text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }

        return text.contains(";") || text.contains("\"") || text.contains("\n")
                ? "\"" + text.replace("\"", "\"\"") + "\""
                : text;
    }

    private static String pad(String text) {
        String cut = text.length() > CELL_WIDTH - 1 ? text.substring(0, CELL_WIDTH - 2) + "…" : text;
        return String.format("%-" + CELL_WIDTH + "s", cut);
    }
}
