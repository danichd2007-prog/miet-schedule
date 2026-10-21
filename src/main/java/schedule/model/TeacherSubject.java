package schedule.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public class TeacherSubject {
    private Long id;
    private final Teacher teacher;
    private final Subject subject;
    private final Set<LessonType> allowedTypes =
            EnumSet.noneOf(LessonType.class);

    public TeacherSubject(Teacher teacher,
                          Subject subject,
                          Set<LessonType> allowedTypes) {

        this.teacher = Objects.requireNonNull(
                teacher, "Teacher cannot be null");

        this.subject = Objects.requireNonNull(
                subject, "Subject cannot be null");

        Objects.requireNonNull(
                allowedTypes, "Allowed types cannot be null");

        if (allowedTypes.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one lesson type is required");
        }

        this.allowedTypes.addAll(allowedTypes);
    }

    public boolean canTeach(LessonType type) {
        return type != null && allowedTypes.contains(type);
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

    public Subject getSubject() {
        return subject;
    }

    public Set<LessonType> getAllowedTypes() {
        return Collections.unmodifiableSet(allowedTypes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TeacherSubject that = (TeacherSubject) o;

        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : getClass().hashCode();
    }

    @Override
    public String toString() {
        return teacher.getShortName() + " — " +
                subject.getName() + " " + allowedTypes;
    }
}
