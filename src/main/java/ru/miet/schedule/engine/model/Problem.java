package ru.miet.schedule.engine.model;

import ru.miet.schedule.domain.AcademicCalendar;
import ru.miet.schedule.domain.Classroom;
import ru.miet.schedule.domain.StudentGroup;
import ru.miet.schedule.domain.Teacher;
import ru.miet.schedule.domain.TeacherAvailability;
import ru.miet.schedule.domain.TeachingRequirement;
import ru.miet.schedule.domain.WeekDay;
import ru.miet.schedule.domain.WeekType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Задача для решателя в компактном виде: преподаватели, группы и аудитории
 * заменены номерами, доступность преподавателей развёрнута в битовые маски.
 *
 * Правило доступности: если у преподавателя есть хотя бы одна запись о доступности,
 * он может вести занятия только в описанные ими интервалы. Если записей нет,
 * доступен всегда.
 */
public final class Problem {

    private static final int WORDS = (TimeGrid.TOTAL_SLOTS + 63) / 64;

    private final AcademicCalendar calendar;
    private final List<Series> series;
    private final List<Teacher> teachers;
    private final List<StudentGroup> groups;
    private final List<Classroom> rooms;
    private final int[] seriesTeacher;
    private final int[][] seriesGroups;
    private final int[][] seriesRooms;
    private final long[][] teacherAllowed;

    private Problem(
            AcademicCalendar calendar,
            List<Series> series,
            List<Teacher> teachers,
            List<StudentGroup> groups,
            List<Classroom> rooms,
            int[] seriesTeacher,
            int[][] seriesGroups,
            int[][] seriesRooms,
            long[][] teacherAllowed) {

        this.calendar = calendar;
        this.series = series;
        this.teachers = teachers;
        this.groups = groups;
        this.rooms = rooms;
        this.seriesTeacher = seriesTeacher;
        this.seriesGroups = seriesGroups;
        this.seriesRooms = seriesRooms;
        this.teacherAllowed = teacherAllowed;
    }

    public static Problem of(
            List<TeachingRequirement> requirements,
            List<Classroom> classrooms,
            Collection<TeacherAvailability> availability,
            AcademicCalendar calendar) {

        Objects.requireNonNull(requirements, "requirements");
        Objects.requireNonNull(classrooms, "classrooms");
        Objects.requireNonNull(availability, "availability");
        Objects.requireNonNull(calendar, "calendar");

        List<Series> series = SeriesBuilder.build(requirements, calendar);
        List<Classroom> rooms = List.copyOf(classrooms);

        Map<Teacher, Integer> teacherIndex = new LinkedHashMap<>();
        Map<StudentGroup, Integer> groupIndex = new LinkedHashMap<>();

        for (Series s : series) {
            teacherIndex.computeIfAbsent(
                    s.requirement().teacher(), k -> teacherIndex.size());

            for (StudentGroup group : s.requirement().groups()) {
                groupIndex.computeIfAbsent(group, k -> groupIndex.size());
            }
        }

        int[] seriesTeacher = new int[series.size()];
        int[][] seriesGroups = new int[series.size()][];
        int[][] seriesRooms = new int[series.size()][];

        for (Series s : series) {
            TeachingRequirement r = s.requirement();
            seriesTeacher[s.index()] = teacherIndex.get(r.teacher());

            List<StudentGroup> groups = r.groups();
            int[] g = new int[groups.size()];

            for (int i = 0; i < g.length; i++) {
                g[i] = groupIndex.get(groups.get(i));
            }

            seriesGroups[s.index()] = g;

            List<Integer> suitable = new ArrayList<>();

            for (int i = 0; i < rooms.size(); i++) {
                Classroom room = rooms.get(i);

                if (room.getType() == r.getRequiredClassroomType()
                        && room.getSeats() >= r.studentCount()) {
                    suitable.add(i);
                }
            }

            // Вместимость по возрастанию: первая свободная аудитория подходит лучше всего,
            // а большие залы остаются потокам.
            suitable.sort(Comparator
                    .comparingInt((Integer i) -> rooms.get(i).getSeats())
                    .thenComparingInt(i -> i));

            seriesRooms[s.index()] =
                    suitable.stream().mapToInt(Integer::intValue).toArray();
        }

        long[][] allowed = new long[teacherIndex.size()][];

        for (TeacherAvailability a : availability) {
            Integer t = teacherIndex.get(a.getTeacher());

            if (t == null) {
                continue;
            }

            if (allowed[t] == null) {
                allowed[t] = new long[WORDS];
            }

            WeekType type = a.getWeekType();

            for (int phase = 0; phase < TimeGrid.PHASES; phase++) {
                if (!type.coversPhase(phase)) {
                    continue;
                }

                for (int pair = a.getFromPair(); pair <= a.getToPair(); pair++) {
                    int slot = TimeGrid.slot(phase, a.getDay().ordinal(), pair);
                    allowed[t][slot >> 6] |= 1L << (slot & 63);
                }
            }
        }

        return new Problem(
                calendar,
                series,
                List.copyOf(teacherIndex.keySet()),
                List.copyOf(groupIndex.keySet()),
                rooms,
                seriesTeacher,
                seriesGroups,
                seriesRooms,
                allowed);
    }

    public AcademicCalendar calendar() {
        return calendar;
    }

    public List<Series> series() {
        return series;
    }

    public Series series(int index) {
        return series.get(index);
    }

    public int seriesCount() {
        return series.size();
    }

    public int teacherCount() {
        return teachers.size();
    }

    public int groupCount() {
        return groups.size();
    }

    public int roomCount() {
        return rooms.size();
    }

    public Teacher teacher(int index) {
        return teachers.get(index);
    }

    public StudentGroup group(int index) {
        return groups.get(index);
    }

    public Classroom room(int index) {
        return rooms.get(index);
    }

    public int teacherOf(int series) {
        return seriesTeacher[series];
    }

    public int[] groupsOf(int series) {
        return seriesGroups[series];
    }

    /** Подходящие аудитории серии, по возрастанию вместимости. */
    public int[] roomsOf(int series) {
        return seriesRooms[series];
    }

    public boolean hasAvailabilityRules(int teacher) {
        return teacherAllowed[teacher] != null;
    }

    /** Может ли преподаватель вести занятие длительностью duration в указанных фазах, день и с пары pair. */
    public boolean isTeacherAllowed(
            int teacher,
            int phaseMask,
            int day,
            int pair,
            int duration) {

        long[] bits = teacherAllowed[teacher];

        if (bits == null) {
            return true;
        }

        for (int phase = 0; phase < TimeGrid.PHASES; phase++) {
            if ((phaseMask & (1 << phase)) == 0) {
                continue;
            }

            for (int k = 0; k < duration; k++) {
                int slot = TimeGrid.slot(phase, day, pair + k);

                if ((bits[slot >> 6] & (1L << (slot & 63))) == 0) {
                    return false;
                }
            }
        }

        return true;
    }

    /** День недели по индексу сетки (0 = понедельник). */
    public WeekDay dayOf(int day) {
        return WeekDay.values()[day];
    }
}
