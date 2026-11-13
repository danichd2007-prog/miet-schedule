package ru.miet.schedule.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Поток: несколько групп, слушающих одну лекцию вместе (раньше класс Stream). */
public final class LectureStream extends Entity {

    private final String name;
    private final Subject subject;
    private final List<StudentGroup> groups = new ArrayList<>();

    public LectureStream(String name, Subject subject) {
        this.name = requireText(name, "Stream name");
        this.subject = Objects.requireNonNull(subject, "Subject cannot be null");
    }

    public void addGroup(StudentGroup group) {
        Objects.requireNonNull(group, "Group cannot be null");

        if (!groups.contains(group)) {
            groups.add(group);
        }
    }

    public List<StudentGroup> getGroups() {
        return Collections.unmodifiableList(groups);
    }

    public int studentCount() {
        int sum = 0;

        for (StudentGroup group : groups) {
            sum += group.getStudentCount();
        }

        return sum;
    }

    public String getName() {
        return name;
    }

    public Subject getSubject() {
        return subject;
    }

    @Override
    public String toString() {
        return name + " — " + subject.getName() + " (" + groups.size() + " групп)";
    }
}
