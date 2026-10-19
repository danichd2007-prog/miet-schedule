package schedule.model;

import java.util.Objects;

public class ScheduleEntry {
    private Long id;
    private Lesson lesson;
    private TimeSlot timeSlot;
    private Classroom classroom;

    public ScheduleEntry(Lesson lesson,
                         TimeSlot timeSlot,
                         Classroom classroom) {

        setLesson(lesson);
        setTimeSlot(timeSlot);
        setClassroom(classroom);
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

    public Lesson getLesson() {
        return lesson;
    }

    public void setLesson(Lesson lesson) {
        this.lesson = Objects.requireNonNull(
                lesson, "Lesson cannot be null");
    }

    public TimeSlot getTimeSlot() {
        return timeSlot;
    }

    public void setTimeSlot(TimeSlot timeSlot) {
        this.timeSlot = Objects.requireNonNull(
                timeSlot, "TimeSlot cannot be null");
    }

    public Classroom getClassroom() {
        return classroom;
    }

    public void setClassroom(Classroom classroom) {
        this.classroom = Objects.requireNonNull(
                classroom, "Classroom cannot be null");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ScheduleEntry that = (ScheduleEntry) o;

        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        String target;

        if (lesson.getStream() != null) {
            target = "Поток: " + lesson.getStream().getName();
        } else {
            target = "Группа: " + lesson.getGroup().getName();
        }

        return lesson.getSubject().getName() + " | " +
                lesson.getTeacher().getShortName() + " | " +
                target + " | " +
                timeSlot + " | " +
                classroom.getBuilding() + "-" + classroom.getNumber();
    }
}
