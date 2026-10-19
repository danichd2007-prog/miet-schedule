package schedule.model;

import java.util.Objects;

public class TeachingRequirement {
    private Long id;
    private final TeacherSubject teacherSubject;
    private final SubjectToGroup subjectToGroup;
    private final Stream stream;
    private final LessonType lessonType;
    private final int lessonCount;
    private final WeekType weekType;
    private final ClassroomType requiredClassroomType;

    private TeachingRequirement(
            TeacherSubject teacherSubject,
            SubjectToGroup subjectToGroup,
            Stream stream,
            LessonType lessonType,
            int lessonCount,
            WeekType weekType,
            ClassroomType requiredClassroomType) {

        this.teacherSubject = Objects.requireNonNull(
                teacherSubject, "TeacherSubject cannot be null");

        this.lessonType = Objects.requireNonNull(
                lessonType, "LessonType cannot be null");

        this.weekType = Objects.requireNonNull(
                weekType, "WeekType cannot be null");

        this.requiredClassroomType = Objects.requireNonNull(
                requiredClassroomType, "ClassroomType cannot be null");

        if (lessonCount <= 0) {
            throw new IllegalArgumentException(
                    "Lesson count must be positive");
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
        this.lessonCount = lessonCount;
    }

    public static TeachingRequirement forLecture(
            TeacherSubject teacherSubject,
            Stream stream,
            int count,
            WeekType weekType,
            ClassroomType roomType) {

        return new TeachingRequirement(
                teacherSubject,
                null,
                stream,
                LessonType.LECTURE,
                count,
                weekType,
                roomType
        );
    }

    public static TeachingRequirement forGroup(
            TeacherSubject teacherSubject,
            SubjectToGroup subjectToGroup,
            LessonType lessonType,
            int count,
            WeekType weekType,
            ClassroomType roomType) {

        return new TeachingRequirement(
                teacherSubject,
                subjectToGroup,
                null,
                lessonType,
                count,
                weekType,
                roomType
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

    public int getLessonCount() {
        return lessonCount;
    }

    public WeekType getWeekType() {
        return weekType;
    }

    public ClassroomType getRequiredClassroomType() {
        return requiredClassroomType;
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
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        String target = stream != null
                ? "поток " + stream.getName()
                : "группа " + subjectToGroup.getGroup().getName();

        return teacherSubject.getSubject().getName() +
                " — " + lessonType +
                ", " + target +
                ", занятий: " + lessonCount +
                ", неделя: " + weekType;
    }
}
