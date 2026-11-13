package ru.miet.schedule.domain;

import java.util.List;
import java.util.Objects;

/**
 * Учебная нагрузка: кто, что, для кого, сколько пар за семестр и в какой аудитории.
 * Лекции привязаны к потоку, остальные занятия к группе.
 */
public final class TeachingRequirement extends Entity {

    private final TeacherSubject teacherSubject;
    private final SubjectToGroup subjectToGroup;
    private final LectureStream stream;
    private final LessonType lessonType;
    private final int totalPairs;
    private final ClassroomType requiredClassroomType;
    private final boolean block;

    private TeachingRequirement(
            TeacherSubject teacherSubject,
            SubjectToGroup subjectToGroup,
            LectureStream stream,
            LessonType lessonType,
            int totalPairs,
            ClassroomType requiredClassroomType,
            boolean block) {

        this.teacherSubject = Objects.requireNonNull(
                teacherSubject, "TeacherSubject cannot be null");

        this.lessonType = Objects.requireNonNull(
                lessonType, "LessonType cannot be null");

        this.requiredClassroomType = Objects.requireNonNull(
                requiredClassroomType, "ClassroomType cannot be null");

        if (totalPairs <= 0) {
            throw new IllegalArgumentException("Total pairs must be positive");
        }

        if (block && totalPairs % 2 != 0) {
            throw new IllegalArgumentException(
                    "Block lessons require an even number of pairs");
        }

        if ((stream == null) == (subjectToGroup == null)) {
            throw new IllegalArgumentException(
                    "Exactly one of stream and subjectToGroup is required");
        }

        if ((lessonType == LessonType.LECTURE) != (stream != null)) {
            throw new IllegalArgumentException(
                    "Lectures require stream; other lessons require subjectToGroup");
        }

        if (!teacherSubject.canTeach(lessonType)) {
            throw new IllegalArgumentException(
                    "Teacher cannot teach this lesson type");
        }

        Subject target = stream != null
                ? stream.getSubject()
                : subjectToGroup.getSubject();

        if (!teacherSubject.getSubject().equals(target)) {
            throw new IllegalArgumentException(
                    "TeacherSubject and target must reference the same subject");
        }

        this.subjectToGroup = subjectToGroup;
        this.stream = stream;
        this.totalPairs = totalPairs;
        this.block = block;
    }

    public static TeachingRequirement forLecture(
            TeacherSubject teacherSubject,
            LectureStream stream,
            int totalPairs,
            ClassroomType roomType,
            boolean block) {

        return new TeachingRequirement(
                teacherSubject, null, stream,
                LessonType.LECTURE, totalPairs, roomType, block);
    }

    public static TeachingRequirement forGroup(
            TeacherSubject teacherSubject,
            SubjectToGroup subjectToGroup,
            LessonType lessonType,
            int totalPairs,
            ClassroomType roomType,
            boolean block) {

        return new TeachingRequirement(
                teacherSubject, subjectToGroup, null,
                lessonType, totalPairs, roomType, block);
    }

    public TeacherSubject getTeacherSubject() {
        return teacherSubject;
    }

    public Teacher teacher() {
        return teacherSubject.getTeacher();
    }

    public Subject subject() {
        return teacherSubject.getSubject();
    }

    public SubjectToGroup getSubjectToGroup() {
        return subjectToGroup;
    }

    public LectureStream getStream() {
        return stream;
    }

    public boolean isStreamLecture() {
        return stream != null;
    }

    public LessonType getLessonType() {
        return lessonType;
    }

    public int getTotalPairs() {
        return totalPairs;
    }

    public ClassroomType getRequiredClassroomType() {
        return requiredClassroomType;
    }

    public boolean isBlock() {
        return block;
    }

    /** Длительность одного занятия в парах: 2 для блока, иначе 1. */
    public int lessonDuration() {
        return block ? 2 : 1;
    }

    /** Сколько занятий за семестр. */
    public int lessonCount() {
        return totalPairs / lessonDuration();
    }

    /** Группы, которые присутствуют на занятии. */
    public List<StudentGroup> groups() {
        return stream != null
                ? stream.getGroups()
                : List.of(subjectToGroup.getGroup());
    }

    /** Сколько студентов придёт на занятие (нужно для проверки вместимости). */
    public int studentCount() {
        int sum = 0;

        for (StudentGroup group : groups()) {
            sum += group.getStudentCount();
        }

        return sum;
    }

    @Override
    public String toString() {
        String target = stream != null
                ? "поток " + stream.getName()
                : "группа " + subjectToGroup.getGroup().getName();

        return teacherSubject.getSubject().getName()
                + " — " + lessonType
                + ", " + target
                + ", всего пар: " + totalPairs
                + ", двухпарный блок: " + block;
    }
}
