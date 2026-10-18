
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
        this.teacher = Objects.requireNonNull(teacher, "teacher");
        this.subject = Objects.requireNonNull(subject, "subject");
        this.allowedTypes.addAll(
                Objects.requireNonNull(allowedTypes, "allowedTypes")
        );
    }

    public boolean canTeach(LessonType type) {
        return allowedTypes.contains(type);
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

    public Subject getSubject() {
        return subject;
    }

    public Set<LessonType> getAllowedTypes() {
        return Collections.unmodifiableSet(allowedTypes);
    }
}
