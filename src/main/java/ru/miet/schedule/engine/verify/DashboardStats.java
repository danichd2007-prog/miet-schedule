package ru.miet.schedule.engine.verify;

import ru.miet.schedule.domain.Classroom;
import ru.miet.schedule.domain.StudentGroup;
import ru.miet.schedule.domain.Teacher;
import ru.miet.schedule.domain.WeekDay;
import ru.miet.schedule.domain.WeekType;
import ru.miet.schedule.engine.model.ScheduledLesson;
import ru.miet.schedule.engine.model.TimeGrid;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ключевые данные расписания для дашборда. Считаются по типовым неделям (первый цикл из четырёх),
 * все «в неделю» значения — среднее за цикл. Занятие-блок считается по числу пар.
 *
 * @param perDay  пар в неделю по дням (индекс — номер дня с понедельника)
 * @param perPair пар в неделю по номерам пары (индекс 0 — первая пара)
 * @param rooms   загрузка аудиторий, самые загруженные первыми
 * @param teachers загрузка преподавателей, самые загруженные первыми
 * @param groups  загрузка групп, самые загруженные первыми
 */
public record DashboardStats(
        double[] perDay,
        double[] perPair,
        List<Load> rooms,
        List<Load> teachers,
        List<Load> groups) {

    /**
     * @param name    название
     * @param perWeek пар в неделю в среднем
     * @param share   доля от возможного числа пар в неделю (0..1); у групп и преподавателей это доля от недели
     */
    public record Load(String name, double perWeek, double share) {
    }

    public static DashboardStats of(Collection<ScheduledLesson> lessons, Collection<Classroom> allRooms) {
        double cycle = WeekType.PHASES;
        double[] perDay = new double[WeekDay.COUNT];
        double[] perPair = new double[TimeGrid.PAIRS];
        Map<Classroom, Integer> room = new HashMap<>();
        Map<Teacher, Integer> teacher = new HashMap<>();
        Map<StudentGroup, Integer> group = new HashMap<>();

        for (Classroom c : allRooms) {
            room.put(c, 0);
        }

        for (ScheduledLesson l : lessons) {
            if (l.week() > WeekType.PHASES) {
                continue;
            }

            perDay[l.day().ordinal()] += l.duration() / cycle;

            for (int k = 0; k < l.duration(); k++) {
                perPair[l.pair() - 1 + k] += 1 / cycle;
            }

            room.merge(l.room(), l.duration(), Integer::sum);
            teacher.merge(l.requirement().teacher(), l.duration(), Integer::sum);

            for (StudentGroup g : l.requirement().groups()) {
                group.merge(g, l.duration(), Integer::sum);
            }
        }

        int capacity = WeekDay.COUNT * TimeGrid.PAIRS;

        return new DashboardStats(perDay, perPair,
                loads(room, c -> c.getNumber() + ", " + c.getBuilding(), cycle, capacity),
                loads(teacher, Teacher::getShortName, cycle, capacity),
                loads(group, StudentGroup::getName, cycle, capacity));
    }

    private static <T> List<Load> loads(
            Map<T, Integer> slots, java.util.function.Function<T, String> name, double cycle, int capacity) {

        List<Load> result = new ArrayList<>();

        for (Map.Entry<T, Integer> e : slots.entrySet()) {
            double perWeek = e.getValue() / cycle;
            result.add(new Load(name.apply(e.getKey()), perWeek, perWeek / capacity));
        }

        result.sort(Comparator.comparingDouble(Load::perWeek).reversed().thenComparing(Load::name));
        return result;
    }
}
