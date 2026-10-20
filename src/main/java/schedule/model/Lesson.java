package schedule.model;

import java.util.Objects;

public class Lesson {

    private Long id;
    private final TeachingRequirement requirement;
    private final int duration;

    public Lesson(TeachingRequirement requirement, int duration) {
        this.requirement = Objects.requireNonNull(
                requirement, "Requirement cannot be null");

        if (duration != 1 && duration != 2) {
            throw new IllegalArgumentException(
                    "Duration must be 1 or 2 pairs");
        }

        if (duration != (requirement.isBlock() ? 2 : 1)) {
            throw new IllegalArgumentException(
                    "Duration does not match TeachingRequirement");
        }

        this.duration = duration;
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

    public TeachingRequirement getRequirement() {
        return requirement;
    }

    public int getDuration() {
        return duration;
    }

    public boolean isDoubleBlock() {
        return duration == 2;
    }

    public Subject getSubject() {
        return requirement.getTeacherSubject().getSubject();
    }

    public Teacher getTeacher() {
        return requirement.getTeacherSubject().getTeacher();
    }

    public StudentGroup getGroup() {
        SubjectToGroup link = requirement.getSubjectToGroup();
        return link == null ? null : link.getGroup();
    }

    public Stream getStream() {
        return requirement.getStream();
    }

    public ClassroomType getRequiredClassroomType() {
        return requirement.getRequiredClassroomType();
    }

    public LessonType getLessonType() {
        return requirement.getLessonType();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Lesson lesson = (Lesson) o;

        return id != null && id.equals(lesson.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        String target = getStream() != null
                ? "поток " + getStream().getName()
                : "группа " + getGroup().getName();

        return getSubject().getName()
                + " | " + getTeacher().getShortName()
                + " | " + target
                + " | " + getLessonType()
                + " | " + duration + " пар";
    }
}
