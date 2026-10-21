package schedule.repository;

import schedule.model.Classroom;
import schedule.model.ClassroomType;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class JdbcClassroomRepository implements CrudRepository<Classroom, Long> {
    private final Connection connection;

    public JdbcClassroomRepository(Connection connection) {
        this.connection = connection;
    }

    @Override
    public void save(Classroom room) {
        String typeSql = "INSERT INTO classroom_types (code, name) VALUES (?, ?) ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name RETURNING id";
        long typeId;
        try (PreparedStatement stmt = connection.prepareStatement(typeSql)) {
            stmt.setString(1, room.getType().name());
            stmt.setString(2, room.getType().getRussianName());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) { typeId = rs.getLong(1); }
                else { throw new SQLException("Не удалось получить ID типа аудитории"); }
            }
        } catch (SQLException e) { throw new RuntimeException(e); }

        String roomSql = "INSERT INTO classrooms (number, building, type_id, capacity, is_available) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(roomSql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, room.getNumber());
            stmt.setString(2, room.getBuilding());
            stmt.setLong(3, typeId);
            stmt.setInt(4, room.getSeats());
            stmt.setBoolean(5, room.isAvailableForExams());
            stmt.executeUpdate();
            try (ResultSet gk = stmt.getGeneratedKeys()) {
                if (gk.next()) { room.setId(gk.getLong(1)); }
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    @Override
    public List<Classroom> findAll() {
        List<Classroom> rooms = new ArrayList<>();
        String sql = "SELECT c.id, c.number, c.building, c.capacity, c.is_available, ct.code AS type_code FROM classrooms c INNER JOIN classroom_types ct ON c.type_id = ct.id";
        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Classroom room = new Classroom(rs.getString("number"), rs.getString("building"), ClassroomType.valueOf(rs.getString("type_code")), rs.getInt("capacity"), rs.getBoolean("is_available"));
                room.setId(rs.getLong("id"));
                rooms.add(room);
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
        return rooms;
    }
}
