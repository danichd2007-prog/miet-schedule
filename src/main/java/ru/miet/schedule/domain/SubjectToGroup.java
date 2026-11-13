package ru.miet.schedule.domain;

import java.util.Objects;

public final class SubjectToGroup extends Entity {

    private final Subject subject;
    private final StudentGroup group;

    public SubjectToGroup(Subject subject, StudentGroup group) {
        this.subject = Objects.requireNonNull(subject, "subject");
        this.group = Objects.requireNonNull(group, "group");
    }

    public Subject getSubject() {
        return subject;
    }

    public StudentGroup getGroup() {
        return group;
    }

    @Override
    public String toString() {
        return subject.getName() + " — " + group.getName();
    }
}
