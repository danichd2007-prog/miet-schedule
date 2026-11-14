package ru.miet.schedule.engine.synthetic;

import ru.miet.schedule.domain.AcademicCalendar;
import ru.miet.schedule.domain.Classroom;
import ru.miet.schedule.domain.ClassroomType;
import ru.miet.schedule.domain.Direction;
import ru.miet.schedule.domain.LectureStream;
import ru.miet.schedule.domain.LessonType;
import ru.miet.schedule.domain.StudentGroup;
import ru.miet.schedule.domain.Subject;
import ru.miet.schedule.domain.SubjectToGroup;
import ru.miet.schedule.domain.Teacher;
import ru.miet.schedule.domain.TeacherAvailability;
import ru.miet.schedule.domain.TeacherCategory;
import ru.miet.schedule.domain.TeacherSubject;
import ru.miet.schedule.domain.TeachingRequirement;
import ru.miet.schedule.domain.WeekDay;
import ru.miet.schedule.domain.WeekType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;

/**
 * Генератор искусственного университета для тестов и замеров скорости.
 *
 * Структура: группы собраны в потоки по три; у каждого потока шесть предметов.
 * Четыре предмета с еженедельной лекцией, два с лекцией раз в две недели;
 * практики, и на двух предметах лабораторные блоки по две пары раз в две недели.
 * Преподавателей меньше, чем предметов, поэтому они ведут по несколько предметов
 * у разных потоков и создают реальные пересечения. Четверть преподавателей совместители,
 * которые работают только три дня в неделю.
 */
public final class SyntheticUniversity {

    public record Data(
            AcademicCalendar calendar,
            List<TeachingRequirement> requirements,
            List<Classroom> rooms,
            List<TeacherAvailability> availability,
            List<StudentGroup> groups) {
    }

    private static final int GROUPS_PER_STREAM = 3;
    private static final int STUDENTS_PER_GROUP = 25;
    private static final int SUBJECTS_PER_STREAM = 6;

    private SyntheticUniversity() {
    }

    public static Data generate(int groupCount, long seed) {
        return generate(groupCount, seed, 1.0);
    }

    /**
     * @param roomScale коэффициент числа аудиторий: 1.0 обычный запас, меньше единицы
     *                  делает задачу теснее
     */
    public static Data generate(int groupCount, long seed, double roomScale) {
        if (groupCount < 1) {
            throw new IllegalArgumentException("Group count must be positive");
        }

        if (roomScale <= 0) {
            throw new IllegalArgumentException("Room scale must be positive");
        }

        Random random = new Random(seed);
        AcademicCalendar calendar = new AcademicCalendar(LocalDate.of(2026, 8, 31), 16);

        long nextId = 1;
        int streamCount = (groupCount + GROUPS_PER_STREAM - 1) / GROUPS_PER_STREAM;
        int directionCount = Math.max(1, streamCount / 4);

        List<Direction> directions = new ArrayList<>();

        for (int d = 0; d < directionCount; d++) {
            Direction direction = new Direction("Направление " + (d + 1), "Н" + (d + 1));
            direction.setId(nextId++);
            directions.add(direction);
        }

        List<StudentGroup> groups = new ArrayList<>();
        List<List<StudentGroup>> cohorts = new ArrayList<>();

        for (int c = 0; c < streamCount; c++) {
            Direction direction = directions.get(c / 4 % directionCount);
            int course = c % 4 + 1;
            List<StudentGroup> cohort = new ArrayList<>();

            for (int k = 0; k < GROUPS_PER_STREAM && groups.size() < groupCount; k++) {
                StudentGroup group = new StudentGroup(
                        direction.getCode() + "-" + course + (c / (4 * directionCount) + 1) + "." + (k + 1),
                        course, direction, STUDENTS_PER_GROUP);
                group.setId(nextId++);
                groups.add(group);
                cohort.add(group);
            }

            cohorts.add(cohort);
        }

        int subjectTotal = streamCount * SUBJECTS_PER_STREAM;
        int teacherPool = Math.max(SUBJECTS_PER_STREAM, subjectTotal / 2);

        List<Teacher> teachers = new ArrayList<>();
        List<TeacherAvailability> availability = new ArrayList<>();

        for (int t = 0; t < teacherPool; t++) {
            boolean partTime = random.nextInt(4) == 0;

            Teacher teacher = new Teacher(
                    "И" + (t + 1), "Преподаватель" + (t + 1), "О", "Кафедра " + (t % 10 + 1),
                    partTime ? TeacherCategory.PART_TIME : TeacherCategory.STAFF);
            teacher.setId(nextId++);
            teachers.add(teacher);

            if (partTime) {
                List<WeekDay> days = new ArrayList<>(List.of(WeekDay.values()));
                java.util.Collections.shuffle(days, random);

                for (WeekDay day : days.subList(0, 3)) {
                    TeacherAvailability a = new TeacherAvailability(
                            teacher, day, 1, 8, WeekType.EVERY_WEEK);
                    a.setId(nextId++);
                    availability.add(a);
                }
            }
        }

        List<TeachingRequirement> requirements = new ArrayList<>();
        int subjectIndex = 0;

        for (int c = 0; c < cohorts.size(); c++) {
            List<StudentGroup> cohort = cohorts.get(c);

            for (int k = 0; k < SUBJECTS_PER_STREAM; k++, subjectIndex++) {
                Subject subject = new Subject("Предмет " + (c + 1) + "." + (k + 1));
                subject.setId(nextId++);

                Teacher teacher = teachers.get(subjectIndex % teacherPool);
                TeacherSubject qualification = new TeacherSubject(
                        teacher, subject,
                        EnumSet.of(LessonType.LECTURE, LessonType.SEMINAR, LessonType.LABORATORY));
                qualification.setId(nextId++);

                LectureStream stream = new LectureStream(
                        "Поток " + (c + 1) + "." + (k + 1), subject);
                stream.setId(nextId++);
                cohort.forEach(stream::addGroup);

                int lecturePairs = k < 4 ? 16 : 8;
                add(requirements, TeachingRequirement.forLecture(
                        qualification, stream, lecturePairs, ClassroomType.LECTURE, false), nextId++);

                for (StudentGroup group : cohort) {
                    SubjectToGroup link = new SubjectToGroup(subject, group);
                    link.setId(nextId++);

                    if (k < 4) {
                        add(requirements, TeachingRequirement.forGroup(
                                qualification, link, LessonType.SEMINAR,
                                k < 2 ? 16 : 8, ClassroomType.REGULAR, false), nextId++);
                    } else {
                        add(requirements, TeachingRequirement.forGroup(
                                qualification, link, LessonType.LABORATORY, 16,
                                k == 4 ? ClassroomType.COMPUTER : ClassroomType.LABORATORY,
                                true), nextId++);
                    }
                }
            }
        }

        List<Classroom> rooms = new ArrayList<>();
        nextId = addRooms(rooms, ClassroomType.LECTURE,
                scaled(groupCount, 0.07, roomScale, 2), 100, 200, random, nextId);
        nextId = addRooms(rooms, ClassroomType.REGULAR,
                scaled(groupCount, 0.18, roomScale, 3), 30, 50, random, nextId);
        nextId = addRooms(rooms, ClassroomType.COMPUTER,
                scaled(groupCount, 0.08, roomScale, 2), 26, 30, random, nextId);
        addRooms(rooms, ClassroomType.LABORATORY,
                scaled(groupCount, 0.07, roomScale, 2), 26, 30, random, nextId);

        return new Data(calendar, requirements, rooms, availability, groups);
    }

    private static void add(List<TeachingRequirement> list, TeachingRequirement r, long id) {
        r.setId(id);
        list.add(r);
    }

    private static int scaled(int groups, double perGroup, double scale, int minimum) {
        return Math.max(minimum, (int) Math.ceil(groups * perGroup * scale));
    }

    private static long addRooms(
            List<Classroom> rooms,
            ClassroomType type,
            int count,
            int minSeats,
            int maxSeats,
            Random random,
            long nextId) {

        for (int i = 0; i < count; i++) {
            int building = rooms.size() / 20 + 1;
            int seats = minSeats + random.nextInt(maxSeats - minSeats + 1);

            Classroom room = new Classroom(
                    String.valueOf(100 + rooms.size()), "Корпус " + building, type, seats);
            room.setId(nextId++);
            rooms.add(room);
        }

        return nextId;
    }
}
