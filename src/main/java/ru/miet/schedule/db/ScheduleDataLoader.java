package ru.miet.schedule.db;

import ru.miet.schedule.domain.AcademicCalendar;
import ru.miet.schedule.domain.Classroom;
import ru.miet.schedule.domain.ClassroomType;
import ru.miet.schedule.domain.Direction;
import ru.miet.schedule.domain.Entity;
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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Читает из базы данные одного семестра и собирает из них доменные объекты.
 *
 * Только чтение. Аудитории с is_available = false пропускаются: так можно «выключить»
 * часть аудиторий и посмотреть, как алгоритм справится с меньшими ресурсами.
 * Некорректные строки не останавливают загрузку, а попадают в warnings.
 */
public final class ScheduleDataLoader {

    private ScheduleDataLoader() {
    }

    public static ScheduleData load(Connection c, long semesterId) throws SQLException {
        List<String> warnings = new ArrayList<>();

        String semesterName;
        AcademicCalendar calendar;

        try (PreparedStatement ps = c.prepareStatement(
                "SELECT name, first_monday, week_count FROM academic_semesters WHERE id = ?")) {
            ps.setLong(1, semesterId);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("Семестр " + semesterId + " не найден");
                }

                semesterName = rs.getString("name");
                calendar = new AcademicCalendar(
                        rs.getDate("first_monday").toLocalDate(), rs.getInt("week_count"));
            }
        }

        Map<Long, Direction> directions = new HashMap<>();

        query(c, "SELECT id, name, code FROM directions ORDER BY id", rs -> {
            Direction d = withId(new Direction(rs.getString("name"), rs.getString("code")), rs.getLong("id"));
            directions.put(d.getId(), d);
        });

        Map<Long, StudentGroup> groups = new LinkedHashMap<>();

        query(c, "SELECT id, name, course, direction_id, student_count FROM student_groups ORDER BY id", rs -> {
            StudentGroup g = withId(new StudentGroup(
                    rs.getString("name"), rs.getInt("course"),
                    directions.get(rs.getLong("direction_id")), rs.getInt("student_count")), rs.getLong("id"));
            groups.put(g.getId(), g);
        });

        List<Classroom> rooms = new ArrayList<>();

        query(c, "SELECT c.id, c.number, c.building, t.code, c.capacity "
                + "FROM classrooms c JOIN classroom_types t ON t.id = c.type_id "
                + "WHERE c.is_available ORDER BY c.id", rs ->
                rooms.add(withId(new Classroom(
                        rs.getString("number"), rs.getString("building"),
                        ClassroomType.valueOf(rs.getString("code")), rs.getInt("capacity")), rs.getLong("id"))));

        Map<Long, Teacher> teachers = new LinkedHashMap<>();

        query(c, "SELECT id, first_name, last_name, middle_name, department, category FROM teachers ORDER BY id", rs -> {
            Teacher t = withId(new Teacher(
                    rs.getString("first_name"), rs.getString("last_name"), rs.getString("middle_name"),
                    rs.getString("department"), TeacherCategory.valueOf(rs.getString("category"))), rs.getLong("id"));
            teachers.put(t.getId(), t);
        });

        Map<Long, Subject> subjects = new HashMap<>();

        query(c, "SELECT id, name FROM subjects ORDER BY id", rs -> {
            Subject s = withId(new Subject(rs.getString("name")), rs.getLong("id"));
            subjects.put(s.getId(), s);
        });

        Map<Long, TeacherSubject> qualifications = loadQualifications(c, teachers, subjects, warnings);
        Map<Long, LectureStream> streams = loadStreams(c, subjects, groups);

        List<TeacherAvailability> availability = new ArrayList<>();

        query(c, "SELECT teacher_id, day_of_week, start_pair, end_pair, week_type "
                + "FROM teacher_availability ORDER BY id", rs ->
                availability.add(new TeacherAvailability(
                        teachers.get(rs.getLong("teacher_id")),
                        WeekDay.valueOf(rs.getString("day_of_week")),
                        rs.getInt("start_pair"), rs.getInt("end_pair"),
                        WeekType.valueOf(rs.getString("week_type")))));

        List<TeachingRequirement> requirements =
                loadRequirements(c, semesterId, qualifications, streams, groups, warnings);

        return new ScheduleData(
                semesterId, semesterName, calendar,
                List.copyOf(groups.values()), List.copyOf(rooms),
                List.copyOf(requirements), List.copyOf(availability), List.copyOf(warnings));
    }

    private static Map<Long, TeacherSubject> loadQualifications(
            Connection c, Map<Long, Teacher> teachers, Map<Long, Subject> subjects, List<String> warnings)
            throws SQLException {

        Map<Long, Set<LessonType>> types = new LinkedHashMap<>();
        Map<Long, long[]> owners = new HashMap<>();

        query(c, "SELECT ts.id, ts.teacher_id, ts.subject_id, l.lesson_type "
                + "FROM teacher_subjects ts "
                + "LEFT JOIN teacher_subject_lesson_types l ON l.teacher_subject_id = ts.id "
                + "ORDER BY ts.id, l.lesson_type", rs -> {
            long id = rs.getLong("id");
            owners.putIfAbsent(id, new long[]{rs.getLong("teacher_id"), rs.getLong("subject_id")});
            Set<LessonType> set = types.computeIfAbsent(id, k -> EnumSet.noneOf(LessonType.class));
            String type = rs.getString("lesson_type");

            if (type != null) {
                set.add(LessonType.valueOf(type));
            }
        });

        Map<Long, TeacherSubject> result = new LinkedHashMap<>();

        for (Map.Entry<Long, Set<LessonType>> entry : types.entrySet()) {
            long[] owner = owners.get(entry.getKey());
            Teacher teacher = teachers.get(owner[0]);
            Subject subject = subjects.get(owner[1]);

            if (entry.getValue().isEmpty()) {
                warnings.add("У преподавателя " + teacher.getShortName() + " по предмету «"
                        + subject.getName() + "» не указано ни одного типа занятий");
                continue;
            }

            result.put(entry.getKey(),
                    withId(new TeacherSubject(teacher, subject, entry.getValue()), entry.getKey()));
        }

        return result;
    }

    private static Map<Long, LectureStream> loadStreams(
            Connection c, Map<Long, Subject> subjects, Map<Long, StudentGroup> groups) throws SQLException {

        Map<Long, LectureStream> streams = new LinkedHashMap<>();

        query(c, "SELECT s.id, s.name, s.subject_id, g.group_id FROM streams s "
                + "LEFT JOIN stream_groups g ON g.stream_id = s.id ORDER BY s.id, g.group_id", rs -> {
            long id = rs.getLong("id");
            LectureStream stream = streams.get(id);

            if (stream == null) {
                stream = withId(new LectureStream(
                        rs.getString("name"), subjects.get(rs.getLong("subject_id"))), id);
                streams.put(id, stream);
            }

            long groupId = rs.getLong("group_id");

            if (!rs.wasNull()) {
                stream.addGroup(groups.get(groupId));
            }
        });

        return streams;
    }

    private static List<TeachingRequirement> loadRequirements(
            Connection c,
            long semesterId,
            Map<Long, TeacherSubject> qualifications,
            Map<Long, LectureStream> streams,
            Map<Long, StudentGroup> groups,
            List<String> warnings) throws SQLException {

        List<TeachingRequirement> result = new ArrayList<>();
        // одна и та же пара «предмет + группа» должна быть одним объектом
        Map<String, SubjectToGroup> links = new HashMap<>();

        try (PreparedStatement ps = c.prepareStatement(
                "SELECT r.id, r.teacher_subject_id, r.group_id, r.stream_id, r.lesson_type, "
                        + "r.total_pairs, r.is_block, t.code "
                        + "FROM teaching_requirements r JOIN classroom_types t ON t.id = r.classroom_type_id "
                        + "WHERE r.semester_id = ? ORDER BY r.id")) {
            ps.setLong(1, semesterId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong("id");
                    TeacherSubject qualification = qualifications.get(rs.getLong("teacher_subject_id"));

                    if (qualification == null) {
                        warnings.add("Нагрузка " + id + " пропущена: у связки преподаватель–предмет нет типов занятий");
                        continue;
                    }

                    try {
                        LessonType lessonType = LessonType.valueOf(rs.getString("lesson_type"));
                        ClassroomType roomType = ClassroomType.valueOf(rs.getString("code"));
                        int pairs = rs.getInt("total_pairs");
                        boolean block = rs.getBoolean("is_block");
                        long streamId = rs.getLong("stream_id");
                        TeachingRequirement requirement;

                        if (!rs.wasNull()) {
                            requirement = TeachingRequirement.forLecture(
                                    qualification, streams.get(streamId), pairs, roomType, block);
                        } else {
                            StudentGroup group = groups.get(rs.getLong("group_id"));
                            SubjectToGroup link = links.computeIfAbsent(
                                    qualification.getSubject().getId() + ":" + group.getId(),
                                    k -> new SubjectToGroup(qualification.getSubject(), group));
                            requirement = TeachingRequirement.forGroup(
                                    qualification, link, lessonType, pairs, roomType, block);
                        }

                        result.add(withId(requirement, id));
                    } catch (RuntimeException e) {
                        warnings.add("Нагрузка " + id + " пропущена: " + e.getMessage());
                    }
                }
            }
        }

        return result;
    }

    private static <T extends Entity> T withId(T entity, long id) {
        entity.setId(id);
        return entity;
    }

    @FunctionalInterface
    private interface RowHandler {
        void handle(ResultSet rs) throws SQLException;
    }

    private static void query(Connection c, String sql, RowHandler handler) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                handler.handle(rs);
            }
        }
    }
}
