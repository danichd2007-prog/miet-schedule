package schedule.model;

import java.util.Objects;

public class TeacherAvailability {
    private Long id;
    private final Teacher teacher;
    private final WeekDay day;
    private final int fromLesson;
    private final int toLesson;
    private final WeekType weekType;

    public TeacherAvailability(Teacher teacher,
                               WeekDay day,
                               int fromLesson,
                               int toLesson,
                               WeekType weekType) {
        if (fromLesson < 1 || toLesson < fromLesson) {
            throw new IllegalArgumentException("Invalid lesson range");
        }

        this.teacher = Objects.requireNonNull(teacher, "teacher");
        this.day = Objects.requireNonNull(day, "day");
        this.fromLesson = fromLesson;
        this.toLesson = toLesson;
        this.weekType = Objects.requireNonNull(weekType, "weekType");
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Teacher getTeacher() {
        return teacher;
    }

    public WeekDay getDay() {
        return day;
    }

    public int getFromLesson() {
        return fromLesson;
    }

    public int getToLesson() {
        return toLesson;
    }

    public WeekType getWeekType() {
        return weekType;
    }
}
