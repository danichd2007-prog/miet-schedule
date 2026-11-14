package ru.miet.schedule.engine.verify;

import ru.miet.schedule.domain.AcademicCalendar;
import ru.miet.schedule.domain.Classroom;
import ru.miet.schedule.domain.StudentGroup;
import ru.miet.schedule.domain.Teacher;
import ru.miet.schedule.domain.TeacherAvailability;
import ru.miet.schedule.domain.TeachingRequirement;
import ru.miet.schedule.domain.WeekDay;
import ru.miet.schedule.engine.model.ScheduledLesson;
import ru.miet.schedule.engine.model.TimeGrid;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Независимая проверка расписания.
 *
 * Не использует ни индекс занятости, ни битовые маски решателя: занятия
 * разворачиваются по реальным неделям, а конфликты ищутся обычными таблицами.
 * Поэтому ошибку в решателе эта проверка поймает, а не повторит.
 * Проверяет только жёсткие ограничения.
 */
public final class ScheduleVerifier {

    private static final int MAX_MESSAGES = 100;

    private record Cell(Object resource, int week, WeekDay day, int pair) {
    }

    private record DayKey(StudentGroup group, int week, WeekDay day) {
    }

    private ScheduleVerifier() {
    }

    /**
     * @param incompleteAllowed нагрузка, которую решатель честно не разместил целиком
     * @return сообщения о нарушениях; пустой список = расписание корректно
     */
    public static List<String> verify(
            Collection<TeachingRequirement> requirements,
            Collection<TeacherAvailability> availability,
            AcademicCalendar calendar,
            Collection<ScheduledLesson> lessons,
            int maxPairsPerDay,
            Set<TeachingRequirement> incompleteAllowed) {

        List<String> problems = new ArrayList<>();

        Map<Teacher, List<TeacherAvailability>> rules = new HashMap<>();

        for (TeacherAvailability a : availability) {
            rules.computeIfAbsent(a.getTeacher(), k -> new ArrayList<>()).add(a);
        }

        Map<Cell, ScheduledLesson> teacherCells = new HashMap<>();
        Map<Cell, ScheduledLesson> groupCells = new HashMap<>();
        Map<Cell, ScheduledLesson> roomCells = new HashMap<>();
        Map<DayKey, Integer> groupLoad = new HashMap<>();
        Map<TeachingRequirement, Integer> counts = new IdentityHashMap<>();

        for (TeachingRequirement r : requirements) {
            counts.put(r, 0);
        }

        for (ScheduledLesson lesson : lessons) {
            TeachingRequirement r = lesson.requirement();

            if (!counts.containsKey(r)) {
                add(problems, "Занятие относится к неизвестной нагрузке: " + r);
                continue;
            }

            counts.merge(r, 1, Integer::sum);

            if (lesson.week() < 1 || lesson.week() > calendar.getWeekCount()) {
                add(problems, "Неделя вне семестра: " + describe(lesson));
                continue;
            }

            if (lesson.duration() != r.lessonDuration()) {
                add(problems, "Неверная длительность занятия: " + describe(lesson));
            }

            if (lesson.pair() < 1 || lesson.lastPair() > TimeGrid.PAIRS) {
                add(problems, "Занятие выходит за границы дня: " + describe(lesson));
                continue;
            }

            Classroom room = lesson.room();

            if (room.getType() != r.getRequiredClassroomType()) {
                add(problems, "Неподходящий тип аудитории " + room + ": " + describe(lesson));
            }

            if (room.getSeats() < r.studentCount()) {
                add(problems, "Не хватает мест в " + room + " для "
                        + r.studentCount() + " студентов: " + describe(lesson));
            }

            Teacher teacher = r.teacher();
            List<TeacherAvailability> teacherRules = rules.get(teacher);

            for (int pair = lesson.pair(); pair <= lesson.lastPair(); pair++) {
                if (teacherRules != null && !isAvailable(
                        teacherRules, lesson, pair, calendar)) {
                    add(problems, "Преподаватель " + teacher.getShortName()
                            + " недоступен: " + describe(lesson) + ", пара " + pair);
                }

                claim(problems, teacherCells,
                        new Cell(teacher, lesson.week(), lesson.day(), pair),
                        lesson, "Преподаватель " + teacher.getShortName());

                claim(problems, roomCells,
                        new Cell(room, lesson.week(), lesson.day(), pair),
                        lesson, "Аудитория " + room.getNumber());

                for (StudentGroup group : r.groups()) {
                    claim(problems, groupCells,
                            new Cell(group, lesson.week(), lesson.day(), pair),
                            lesson, "Группа " + group.getName());
                }
            }

            for (StudentGroup group : r.groups()) {
                groupLoad.merge(
                        new DayKey(group, lesson.week(), lesson.day()),
                        lesson.duration(),
                        Integer::sum);
            }
        }

        for (Map.Entry<DayKey, Integer> e : groupLoad.entrySet()) {
            if (e.getValue() > maxPairsPerDay) {
                add(problems, "У группы " + e.getKey().group().getName() + " "
                        + e.getValue() + " пар в день (неделя " + e.getKey().week()
                        + ", " + e.getKey().day() + "), больше допустимых " + maxPairsPerDay);
            }
        }

        for (TeachingRequirement r : requirements) {
            int placed = counts.get(r);
            int expected = r.lessonCount();

            if (placed > expected) {
                add(problems, "Занятий больше нормы (" + placed + " из " + expected + "): " + r);
            } else if (placed < expected && !incompleteAllowed.contains(r)) {
                add(problems, "Размещено " + placed + " занятий из " + expected + ": " + r);
            }
        }

        if (problems.size() >= MAX_MESSAGES) {
            problems.add("... и другие нарушения (показаны первые " + MAX_MESSAGES + ")");
        }

        return problems;
    }

    private static boolean isAvailable(
            List<TeacherAvailability> rules,
            ScheduledLesson lesson,
            int pair,
            AcademicCalendar calendar) {

        for (TeacherAvailability a : rules) {
            if (a.allows(lesson.day(), pair, calendar.getWeekType(lesson.week()))) {
                return true;
            }
        }

        return false;
    }

    private static void claim(
            List<String> problems,
            Map<Cell, ScheduledLesson> cells,
            Cell cell,
            ScheduledLesson lesson,
            String who) {

        ScheduledLesson other = cells.putIfAbsent(cell, lesson);

        if (other != null) {
            add(problems, who + ": два занятия одновременно (неделя " + cell.week()
                    + ", " + cell.day() + ", пара " + cell.pair() + "): "
                    + other.requirement().subject().getName() + " и "
                    + lesson.requirement().subject().getName());
        }
    }

    private static String describe(ScheduledLesson lesson) {
        return lesson.requirement().subject().getName() + ", неделя " + lesson.week()
                + ", " + lesson.day() + ", пара " + lesson.pair();
    }

    private static void add(List<String> problems, String message) {
        if (problems.size() < MAX_MESSAGES) {
            problems.add(message);
        }
    }
}
