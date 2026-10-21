
package schedule;

import schedule.calendar.AcademicCalendar;
import schedule.constraint.ConstraintValidator;
import schedule.generator.ScheduleGenerator;
import schedule.generator.ScheduleQualityEvaluator;
import schedule.model.*;
import schedule.util.DatabaseConfig;
import schedule.util.ScheduleCsvExporter;

import java.nio.file.Path;
import java.sql.*;
import java.util.*;

public class DatabaseGeneratorTest {

    private record Input(
            AcademicCalendar calendar,
            List<TeachingRequirement> requirements,
            List<Classroom> rooms,
            List<TeacherAvailability> availability,
            Collection<StudentGroup> groups) {
    }

    public static void main(String[] args) throws Exception {

        String mode = args.length == 0 ? "ALL" : args[0];

        try (Connection connection = DatabaseConfig.getConnection()) {

            Input input = load(connection, mode);

            int expected = input.requirements().stream()
                    .mapToInt(TeachingRequirement::getTotalPairs)
                    .sum();

            System.out.printf(
                    "Режим: %s; групп: %d; требований: %d; " +
                            "пар: %d; аудиторий: %d%n",
                    mode,
                    input.groups().size(),
                    input.requirements().size(),
                    expected,
                    input.rooms().size()
            );

            if (expected == 0) {
                throw new IllegalStateException(
                        "В БД нет учебной нагрузки для выбранного режима"
                );
            }

            ConstraintValidator validator =
                    new ConstraintValidator(
                            input.availability(),
                            input.calendar()
                    );

            ScheduleGenerator generator =
                    new ScheduleGenerator(validator);

            long start = System.nanoTime();

            Optional<Schedule> result = generator.generate(
                    input.requirements(),
                    input.rooms(),
                    input.calendar()
            );

            System.out.printf(
                    "Время: %.2f с%n",
                    (System.nanoTime() - start) / 1e9
            );

            if (result.isEmpty()) {
                System.out.println(
                        "Расписание не найдено; лимит поиска: "
                                + generator.wasSearchLimitReached()
                );
                return;
            }

            Schedule schedule = result.get();

            int actual = schedule.getEntries().stream()
                    .mapToInt(e -> e.getLesson().getDuration())
                    .sum();

            Schedule checked = new Schedule(
                    "Проверка",
                    "Валидация результата"
            );

            int conflicts = 0;

            for (ScheduleEntry entry : schedule.getEntries()) {

                if (!validator.canAdd(entry, checked)) {
                    conflicts++;
                }

                checked.addEntry(entry);
            }

            System.out.printf(
                    "Размещено: %d/%d; нарушений: %d%n",
                    actual,
                    expected,
                    conflicts
            );

            if (actual != expected || conflicts != 0) {
                throw new AssertionError(
                        "Некорректное расписание"
                );
            }

            System.out.println("TEST PASSED");

            System.out.printf(
                    "Штраф до: %.1f; после: %.1f; улучшение: %.1f%n",
                    generator.getQualityBeforeOptimization(),
                    generator.getQualityAfterOptimization(),
                    generator.getQualityBeforeOptimization()
                            - generator.getQualityAfterOptimization()
            );

            System.out.printf(
                    "Проверок качества: %d; улучшений: %d; проходов: %d%n",
                    generator.getOptimizationQualityChecks(),
                    generator.getAcceptedImprovements(),
                    generator.getOptimizationPasses()
            );

            new ScheduleQualityEvaluator(
                    input.calendar().getWeekCount()
            ).evaluateDetailed(schedule).print();

            Path out = Path.of(
                    "generated-schedules",
                    "from-db"
            );

            ScheduleCsvExporter.export(
                    schedule,
                    input.calendar(),
                    input.groups(),
                    out
            );

            System.out.println(
                    "CSV: " + out.toAbsolutePath()
            );
        }
    }

    private static Input load(
            Connection c,
            String mode) throws SQLException {

        Set<String> selected = switch (mode) {
            case "ALL" ->
                    Set.of("ИБ-22", "ПИН-27", "ПМ-22");
            case "ИБ-22", "ПИН-27", "ПМ-22" ->
                    Set.of(mode);
            default ->
                    throw new IllegalArgumentException(
                            "Используй ALL, ИБ-22, ПИН-27 или ПМ-22"
                    );
        };

        AcademicCalendar calendar;

        try (PreparedStatement ps = c.prepareStatement(
                "SELECT first_monday, week_count " +
                        "FROM academic_semesters WHERE name = ?")) {

            ps.setString(1, "Тест: осень 2026");

            try (ResultSet rs = ps.executeQuery()) {

                if (!rs.next()) {
                    throw new IllegalStateException(
                            "Нет семестра 'Тест: осень 2026'"
                    );
                }

                calendar = new AcademicCalendar(
                        rs.getDate(1).toLocalDate(),
                        rs.getInt(2)
                );
            }
        }

        Map<Long, Direction> directions = new HashMap<>();

        try (
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT id, name, code FROM directions"
                )
        ) {
            while (rs.next()) {
                directions.put(
                        rs.getLong("id"),
                        new Direction(
                                rs.getString("name"),
                                rs.getString("code")
                        )
                );
            }
        }

        Map<Long, StudentGroup> allGroups = new HashMap<>();
        Map<String, StudentGroup> chosen = new LinkedHashMap<>();

        try (
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT id, name, course, direction_id " +
                                "FROM student_groups"
                )
        ) {
            while (rs.next()) {

                StudentGroup group = new StudentGroup(
                        rs.getString("name"),
                        rs.getInt("course"),
                        required(
                                directions,
                                rs.getLong("direction_id"),
                                "direction"
                        )
                );

                allGroups.put(rs.getLong("id"), group);

                if (selected.contains(group.getName())) {
                    chosen.put(group.getName(), group);
                }
            }
        }

        if (chosen.size() != selected.size()) {
            throw new IllegalStateException(
                    "Не все выбранные группы существуют в БД"
            );
        }

        Map<Long, Teacher> teachers = new HashMap<>();

        try (
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT id, first_name, last_name, " +
                                "middle_name, department FROM teachers"
                )
        ) {
            while (rs.next()) {
                teachers.put(
                        rs.getLong("id"),
                        new Teacher(
                                rs.getString("first_name"),
                                rs.getString("last_name"),
                                rs.getString("middle_name"),
                                rs.getString("department")
                        )
                );
            }
        }

        Map<Long, Subject> subjects = new HashMap<>();

        try (
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT id, name FROM subjects"
                )
        ) {
            while (rs.next()) {
                subjects.put(
                        rs.getLong("id"),
                        new Subject(rs.getString("name"))
                );
            }
        }

        Map<Long, Stream> streams = new HashMap<>();

        try (
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT id, name, subject_id FROM streams"
                )
        ) {
            while (rs.next()) {
                streams.put(
                        rs.getLong("id"),
                        new Stream(
                                rs.getString("name"),
                                required(
                                        subjects,
                                        rs.getLong("subject_id"),
                                        "subject"
                                )
                        )
                );
            }
        }

        try (
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT stream_id, group_id FROM stream_groups"
                )
        ) {
            while (rs.next()) {

                required(
                        streams,
                        rs.getLong("stream_id"),
                        "stream"
                ).addGroup(
                        required(
                                allGroups,
                                rs.getLong("group_id"),
                                "group"
                        )
                );
            }
        }

        Map<Long, Set<LessonType>> allowed = new HashMap<>();

        try (
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT teacher_subject_id, lesson_type " +
                                "FROM teacher_subject_lesson_types"
                )
        ) {
            while (rs.next()) {

                allowed.computeIfAbsent(
                        rs.getLong(1),
                        x -> EnumSet.noneOf(LessonType.class)
                ).add(
                        LessonType.valueOf(rs.getString(2))
                );
            }
        }

        Map<Long, TeacherSubject> teacherSubjects =
                new HashMap<>();

        try (
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT id, teacher_id, subject_id " +
                                "FROM teacher_subjects"
                )
        ) {
            while (rs.next()) {

                teacherSubjects.put(
                        rs.getLong("id"),
                        new TeacherSubject(
                                required(
                                        teachers,
                                        rs.getLong("teacher_id"),
                                        "teacher"
                                ),
                                required(
                                        subjects,
                                        rs.getLong("subject_id"),
                                        "subject"
                                ),
                                required(
                                        allowed,
                                        rs.getLong("id"),
                                        "allowed lesson types"
                                )
                        )
                );
            }
        }

        List<Classroom> rooms = new ArrayList<>();

        try (
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT c.number, c.building, c.capacity, " +
                                "c.is_available, t.code AS type " +
                                "FROM classrooms c " +
                                "JOIN classroom_types t ON t.id = c.type_id " +
                                "WHERE c.is_available"
                )
        ) {
            while (rs.next()) {

                rooms.add(
                        new Classroom(
                                rs.getString("number"),
                                rs.getString("building"),
                                ClassroomType.valueOf(
                                        rs.getString("type")
                                ),
                                rs.getInt("capacity"),
                                true
                        )
                );
            }
        }

        List<TeacherAvailability> availability =
                new ArrayList<>();

        try (
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT teacher_id, day_of_week, " +
                                "start_pair, end_pair, week_type " +
                                "FROM teacher_availability"
                )
        ) {
            while (rs.next()) {

                availability.add(
                        new TeacherAvailability(
                                required(
                                        teachers,
                                        rs.getLong("teacher_id"),
                                        "teacher"
                                ),
                                WeekDay.valueOf(
                                        rs.getString("day_of_week")
                                ),
                                rs.getInt("start_pair"),
                                rs.getInt("end_pair"),
                                WeekType.valueOf(
                                        rs.getString("week_type")
                                )
                        )
                );
            }
        }

        List<TeachingRequirement> requirements =
                new ArrayList<>();

        try (PreparedStatement ps = c.prepareStatement(
                "SELECT r.teacher_subject_id, r.group_id, " +
                        "r.stream_id, r.lesson_type, r.total_pairs, " +
                        "r.is_block, t.code AS room_type " +
                        "FROM teaching_requirements r " +
                        "JOIN academic_semesters sem " +
                        "ON sem.id = r.semester_id " +
                        "JOIN classroom_types t " +
                        "ON t.id = r.classroom_type_id " +
                        "WHERE sem.name = ?"
        )) {

            ps.setString(1, "Тест: осень 2026");

            try (ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {

                    TeacherSubject ts = required(
                            teacherSubjects,
                            rs.getLong("teacher_subject_id"),
                            "teacher subject"
                    );

                    ClassroomType roomType =
                            ClassroomType.valueOf(
                                    rs.getString("room_type")
                            );

                    int pairs = rs.getInt("total_pairs");
                    boolean block = rs.getBoolean("is_block");

                    long streamId = rs.getLong("stream_id");

                    if (!rs.wasNull()) {

                        Stream stream = required(
                                streams,
                                streamId,
                                "stream"
                        );

                        boolean intersects = stream.getGroups()
                                .stream()
                                .anyMatch(chosen.values()::contains);

                        if (intersects) {

                            requirements.add(
                                    TeachingRequirement.forLecture(
                                            ts,
                                            stream,
                                            pairs,
                                            roomType,
                                            block
                                    )
                            );
                        }

                    } else {

                        long groupId = rs.getLong("group_id");

                        StudentGroup group = required(
                                allGroups,
                                groupId,
                                "group"
                        );

                        if (chosen.containsValue(group)) {

                            requirements.add(
                                    TeachingRequirement.forGroup(
                                            ts,
                                            new SubjectToGroup(
                                                    ts.getSubject(),
                                                    group
                                            ),
                                            LessonType.valueOf(
                                                    rs.getString("lesson_type")
                                            ),
                                            pairs,
                                            roomType,
                                            block
                                    )
                            );
                        }
                    }
                }
            }
        }

        return new Input(
                calendar,
                requirements,
                rooms,
                availability,
                chosen.values()
        );
    }

    private static <T> T required(
            Map<Long, T> objects,
            long id,
            String kind) {

        T value = objects.get(id);

        if (value == null) {
            throw new IllegalStateException(
                    "Нет " + kind + " для id=" + id
            );
        }

        return value;
    }
}
