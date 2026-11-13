package ru.miet.schedule.domain;

import java.util.Objects;

public final class StudentGroup extends Entity {

    public static final int DEFAULT_STUDENT_COUNT = 25;

    private final String name;
    private final int course;
    private final Direction direction;
    private final int studentCount;

    public StudentGroup(String name, int course, Direction direction) {
        this(name, course, direction, DEFAULT_STUDENT_COUNT);
    }

    public StudentGroup(
            String name,
            int course,
            Direction direction,
            int studentCount) {

        if (course < 1 || course > 6) {
            throw new IllegalArgumentException(
                    "Course must be between 1 and 6");
        }

        if (studentCount <= 0) {
            throw new IllegalArgumentException(
                    "Student count must be positive");
        }

        this.name = requireText(name, "Group name");
        this.course = course;
        this.direction = Objects.requireNonNull(
                direction, "Direction cannot be null");
        this.studentCount = studentCount;
    }

    public String getName() {
        return name;
    }

    public int getCourse() {
        return course;
    }

    public Direction getDirection() {
        return direction;
    }

    public int getStudentCount() {
        return studentCount;
    }

    @Override
    public String toString() {
        return name + ", " + course + " курс";
    }
}
