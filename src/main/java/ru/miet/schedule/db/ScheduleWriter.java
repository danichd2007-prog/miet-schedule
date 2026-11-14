package ru.miet.schedule.db;

import ru.miet.schedule.domain.Classroom;
import ru.miet.schedule.domain.TeachingRequirement;
import ru.miet.schedule.domain.WeekDay;
import ru.miet.schedule.engine.model.ScheduledLesson;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Сохраняет готовое расписание в базу и читает его обратно.
 * Каждое сохранение — новый запуск (schedule_runs), старые версии остаются.
 * Фиксацию транзакции выполняет вызывающий код.
 */
public final class ScheduleWriter {

    private ScheduleWriter() {
    }

    /** Сводка о сохранённом запуске. */
    public record RunInfo(
            long id,
            long semesterId,
            OffsetDateTime createdAt,
            int placed,
            int unplaced,
            long elapsedMs,
            String note) {
    }

    private static final String RUN_COLUMNS =
            "SELECT id, semester_id, created_at, placed, unplaced, elapsed_ms, note FROM schedule_runs ";

    private static RunInfo runInfo(ResultSet rs) throws SQLException {
        return new RunInfo(
                rs.getLong("id"), rs.getLong("semester_id"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getInt("placed"), rs.getInt("unplaced"),
                rs.getLong("elapsed_ms"), rs.getString("note"));
    }

    /** Запуски семестра, новые первыми. */
    public static List<RunInfo> listRuns(Connection c, long semesterId) throws SQLException {
        List<RunInfo> result = new ArrayList<>();

        try (PreparedStatement ps = c.prepareStatement(
                RUN_COLUMNS + "WHERE semester_id = ? ORDER BY id DESC")) {
            ps.setLong(1, semesterId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(runInfo(rs));
                }
            }
        }

        return result;
    }

    public static Optional<RunInfo> findRun(Connection c, long runId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(RUN_COLUMNS + "WHERE id = ?")) {
            ps.setLong(1, runId);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(runInfo(rs)) : Optional.empty();
            }
        }
    }

    /**
     * Меняет положение одного сохранённого занятия. Занятие ищется по нагрузке, неделе, дню
     * и паре начала; если такого нет (расписание уже изменили), бросает исключение.
     */
    public static void updateLesson(Connection c, long runId, ScheduledLesson from, ScheduledLesson to)
            throws SQLException {

        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE schedule_lessons SET week = ?, day_of_week = ?, start_pair = ?, classroom_id = ? "
                        + "WHERE id = (SELECT id FROM schedule_lessons WHERE run_id = ? AND requirement_id = ? "
                        + "AND week = ? AND day_of_week = ? AND start_pair = ? LIMIT 1)")) {
            ps.setInt(1, to.week());
            ps.setString(2, to.day().name());
            ps.setInt(3, to.pair());
            ps.setLong(4, to.room().getId());
            ps.setLong(5, runId);
            ps.setLong(6, from.requirement().getId());
            ps.setInt(7, from.week());
            ps.setString(8, from.day().name());
            ps.setInt(9, from.pair());

            if (ps.executeUpdate() != 1) {
                throw new IllegalStateException(
                        "Занятие не найдено в базе: возможно, расписание уже изменили");
            }
        }
    }

    /** Помечает запуск как правленный вручную (в примечании). */
    public static void markEdited(Connection c, long runId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE schedule_runs SET note = CASE WHEN note LIKE '%ручные правки%' THEN note "
                        + "ELSE left(CASE WHEN note = '' THEN '' ELSE note || ', ' END || 'ручные правки', 200) END "
                        + "WHERE id = ?")) {
            ps.setLong(1, runId);
            ps.executeUpdate();
        }
    }

    /** Копирует запуск со всеми занятиями и возвращает номер копии. */
    public static long copyRun(Connection c, long runId) throws SQLException {
        long copyId;

        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO schedule_runs (semester_id, placed, unplaced, elapsed_ms, note) "
                        + "SELECT semester_id, placed, unplaced, elapsed_ms, left('копия № ' || id, 200) "
                        + "FROM schedule_runs WHERE id = ?", Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, runId);

            if (ps.executeUpdate() != 1) {
                throw new IllegalArgumentException("Расписание № " + runId + " не найдено");
            }

            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                copyId = keys.getLong("id");
            }
        }

        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO schedule_lessons "
                        + "(run_id, requirement_id, week, day_of_week, start_pair, duration, classroom_id) "
                        + "SELECT ?, requirement_id, week, day_of_week, start_pair, duration, classroom_id "
                        + "FROM schedule_lessons WHERE run_id = ? ORDER BY id")) {
            ps.setLong(1, copyId);
            ps.setLong(2, runId);
            ps.executeUpdate();
        }

        return copyId;
    }

    /** Удаляет запуск вместе с его занятиями. */
    public static void deleteRun(Connection c, long runId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM schedule_runs WHERE id = ?")) {
            ps.setLong(1, runId);
            ps.executeUpdate();
        }
    }

    /** Сохраняет занятия как новый запуск и возвращает его номер. */
    public static long saveRun(
            Connection c,
            long semesterId,
            List<ScheduledLesson> lessons,
            int unplaced,
            long elapsedMillis,
            String note) throws SQLException {

        long runId;

        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO schedule_runs (semester_id, placed, unplaced, elapsed_ms, note) "
                        + "VALUES (?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, semesterId);
            ps.setInt(2, lessons.size());
            ps.setInt(3, unplaced);
            ps.setLong(4, elapsedMillis);
            ps.setString(5, note == null ? "" : note);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                runId = keys.getLong("id");
            }
        }

        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO schedule_lessons "
                        + "(run_id, requirement_id, week, day_of_week, start_pair, duration, classroom_id) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            for (ScheduledLesson lesson : lessons) {
                Long requirementId = lesson.requirement().getId();
                Long roomId = lesson.room().getId();

                if (requirementId == null || roomId == null) {
                    throw new IllegalArgumentException(
                            "Занятие нельзя сохранить: нагрузка или аудитория не из базы данных");
                }

                ps.setLong(1, runId);
                ps.setLong(2, requirementId);
                ps.setInt(3, lesson.week());
                ps.setString(4, lesson.day().name());
                ps.setInt(5, lesson.pair());
                ps.setInt(6, lesson.duration());
                ps.setLong(7, roomId);
                ps.addBatch();
            }

            ps.executeBatch();
        }

        return runId;
    }

    /** Номер последнего запуска для семестра, если он есть. */
    public static OptionalLong latestRun(Connection c, long semesterId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT MAX(id) FROM schedule_runs WHERE semester_id = ?")) {
            ps.setLong(1, semesterId);

            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                long id = rs.getLong(1);
                return rs.wasNull() ? OptionalLong.empty() : OptionalLong.of(id);
            }
        }
    }

    /** Читает сохранённый запуск, сопоставляя строки с объектами из data. */
    public static List<ScheduledLesson> loadRun(Connection c, long runId, ScheduleData data)
            throws SQLException {

        Map<Long, TeachingRequirement> requirements = new HashMap<>();
        Map<Long, Classroom> rooms = new HashMap<>();

        for (TeachingRequirement r : data.requirements()) {
            requirements.put(r.getId(), r);
        }

        for (Classroom room : data.rooms()) {
            rooms.put(room.getId(), room);
        }

        List<ScheduledLesson> result = new ArrayList<>();

        try (PreparedStatement ps = c.prepareStatement(
                "SELECT requirement_id, week, day_of_week, start_pair, duration, classroom_id "
                        + "FROM schedule_lessons WHERE run_id = ? ORDER BY id")) {
            ps.setLong(1, runId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    TeachingRequirement requirement = requirements.get(rs.getLong("requirement_id"));
                    Classroom room = rooms.get(rs.getLong("classroom_id"));

                    if (requirement == null || room == null) {
                        throw new IllegalStateException(
                                "Запуск " + runId + " ссылается на нагрузку или аудиторию, "
                                        + "которых нет в данных семестра (возможно, аудитория выключена)");
                    }

                    result.add(new ScheduledLesson(
                            requirement, rs.getInt("week"),
                            WeekDay.valueOf(rs.getString("day_of_week")),
                            rs.getInt("start_pair"), rs.getInt("duration"), room));
                }
            }
        }

        return result;
    }
}
