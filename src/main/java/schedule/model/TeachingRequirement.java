package schedule.model;

import java.util.Objects;

public class TeachingRequirement {

    private Long id;

    private final TeacherSubject teacherSubject;
    private final SubjectToGroup subjectToGroup;
    private final Stream stream;
    private final LessonType lessonType;
    private final int totalPairs;
    private final ClassroomType requiredClassroomType;
    private final boolean isBlock;

    private TeachingRequirement(
            TeacherSubject teacherSubject,
            SubjectToGroup subjectToGroup,
            Stream stream,
            LessonType lessonType,
            int totalPairs,
            ClassroomType requiredClassroomType,
            boolean isBlock) {

        this.teacherSubject = Objects.requireNonNull(
                teacherSubject, "TeacherSubject cannot be null");

        this.lessonType = Objects.requireNonNull(
                lessonType, "LessonType cannot be null");

        this.requiredClassroomType = Objects.requireNonNull(
                requiredClassroomType,
                "ClassroomType cannot be null");

        if (totalPairs <= 0) {
            throw new IllegalArgumentException(
                    "Total pairs must be positive");
        }

        if (isBlock && totalPairs % 2 != 0) {
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

        Subject targetSubject = stream != null
                ? stream.getSubject()
                : subjectToGroup.getSubject();

        if (!teacherSubject.getSubject().equals(targetSubject)) {
            throw new IllegalArgumentException(
                    "TeacherSubject and target must reference the same subject");
        }

        this.subjectToGroup = subjectToGroup;
        this.stream = stream;
        this.totalPairs = totalPairs;
        this.isBlock = isBlock;
    }

    public static TeachingRequirement forLecture(
            TeacherSubject teacherSubject,
            Stream stream,
            int totalPairs,
            ClassroomType roomType,
            boolean isBlock) {

        return new TeachingRequirement(
                teacherSubject,
                null,
                stream,
                LessonType.LECTURE,
                totalPairs,
                roomType,
                isBlock
        );
    }

    public static TeachingRequirement forGroup(
            TeacherSubject teacherSubject,
            SubjectToGroup subjectToGroup,
            LessonType lessonType,
            int totalPairs,
            ClassroomType roomType,
            boolean isBlock) {

        return new TeachingRequirement(
                teacherSubject,
                subjectToGroup,
                null,
                lessonType,
                totalPairs,
                roomType,
                isBlock
        );
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

    public TeacherSubject getTeacherSubject() {
        return teacherSubject;
    }

    public SubjectToGroup getSubjectToGroup() {
        return subjectToGroup;
    }

    public Stream getStream() {
        return stream;
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
        return isBlock;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TeachingRequirement that = (TeachingRequirement) o;

        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : getClass().hashCode();
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
                + ", двухпарный блок: " + isBlock;
    }
}
