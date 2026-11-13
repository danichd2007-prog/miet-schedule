package ru.miet.schedule.domain;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Квалификация: какой преподаватель какой предмет и какие типы занятий может вести. */
public final class TeacherSubject extends Entity {

    private final Teacher teacher;
    private final Subject subject;
    private final Set<LessonType> allowedTypes = EnumSet.noneOf(LessonType.class);

    public TeacherSubject(
            Teacher teacher,
            Subject subject,
            Set<LessonType> allowedTypes) {

        this.teacher = Objects.requireNonNull(teacher, "Teacher cannot be null");
        this.subject = Objects.requireNonNull(subject, "Subject cannot be null");

        Objects.requireNonNull(allowedTypes, "Allowed types cannot be null");

        if (allowedTypes.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one lesson type is required");
        }

        this.allowedTypes.addAll(allowedTypes);
    }

    public boolean canTeach(LessonType type) {
        return type != null && allowedTypes.contains(type);
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
    public String toString() {
        return teacher.getShortName() + " — " + subject.getName()
                + " " + allowedTypes;
    }
}
