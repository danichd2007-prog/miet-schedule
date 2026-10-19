
package schedule.model;

import java.util.Objects;
import schedule.util.TimeSlotUtils;

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

        this.teacher = Objects.requireNonNull(
                teacher, "Teacher cannot be null");

        this.day = Objects.requireNonNull(
                day, "Day cannot be null");

        this.weekType = Objects.requireNonNull(
                weekType, "Week type cannot be null");

        if (fromLesson < 1 || toLesson < fromLesson) {
            throw new IllegalArgumentException(
                    "Invalid lesson range");
        }

        this.fromLesson = fromLesson;
        this.toLesson = toLesson;
    }


    public boolean isAvailable(WeekDay day,
                               int lessonNumber,
                               WeekType weekType) {

        if (day == null || weekType == null) {
            return false;
        }

        boolean sameDay = this.day == day;

        boolean lessonInRange =
                lessonNumber >= fromLesson &&
                        lessonNumber <= toLesson;

        boolean matchingWeek = TimeSlotUtils.weeksCover(
                this.weekType,
                weekType
        );

        return sameDay && lessonInRange && matchingWeek;
    }


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(
                    "ID must be positive");
        }

        if (this.id != null && !this.id.equals(id)) {
            throw new IllegalStateException(
                    "ID cannot be changed");
        }

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TeacherAvailability that = (TeacherAvailability) o;

        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return teacher.getShortName() + ": " +
                day + ", пары " + fromLesson + "-" + toLesson +
                ", " + weekType;
    }
}
