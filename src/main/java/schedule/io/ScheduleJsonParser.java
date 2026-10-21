package schedule.io;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.List;

public class ScheduleJsonParser {
    public static class RawRow {
        public String subject;
        public String teacher;
        public String type;
        public String requiredClassroomType;
        public boolean isBlock;
    }

    public List<RawRow> loadScheduleFromJson() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("ai_schedule.json")) {
            if (input == null) {
                throw new IllegalArgumentException("Файл ai_schedule.json не найден в ресурсах!");
            }
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(input, mapper.getTypeFactory().constructCollectionType(List.class, RawRow.class));
        } catch (Exception e) {
            throw new RuntimeException("Критическая ошибка десериализации данных Jackson", e);
        }
    }
}
