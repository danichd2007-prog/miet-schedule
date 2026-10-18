
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

        this.teacherSubject =
                Objects.requireNonNull(teacherSubject, "teacherSubject");
        this.lessonType =
                Objects.requireNonNull(lessonType, "lessonType");
        this.weekType =
                Objects.requireNonNull(weekType, "weekType");
        this.requiredClassroomType =
                Objects.requireNonNull(requiredClassroomType,
                        "requiredClassroomType");

        if (lessonCount <= 0) {
            throw new IllegalArgumentException(
                    "lessonCount must be positive");
        }

        if ((stream == null) == (subjectToGroup == null)) {
            throw new IllegalArgumentException(
                    "Exactly one of stream and subjectToGroup is required");
        }

        if ((lessonType == LessonType.LECTURE) != (stream != null)) {
            throw new IllegalArgumentException(
                    "Lectures need stream; other lessons need subjectToGroup");
        }

        if (!teacherSubject.canTeach(lessonType)) {
            throw new IllegalArgumentException(
                    "Teacher is not allowed to teach this lesson type");
        }

        Subject targetSubject = stream != null
                ? stream.getSubject()
                : subjectToGroup.getSubject();

        if (teacherSubject.getSubject() != targetSubject) {
            throw new IllegalArgumentException(
                    "Subjects must match (use canonical Subject instances)");
        }

        this.subjectToGroup = subjectToGroup;
        this.stream = stream;
        this.lessonCount = lessonCount;
    }

    public static TeachingRequirement forLecture(
            TeacherSubject ts,
            Stream stream,
            int count,
            WeekType weekType,
            ClassroomType roomType) {

        return new TeachingRequirement(
                ts, null, stream, LessonType.LECTURE,
                count, weekType, roomType);
    }

    public static TeachingRequirement forGroup(
            TeacherSubject ts,
            SubjectToGroup stg,
            LessonType type,
            int count,
            WeekType weekType,
            ClassroomType roomType) {

        return new TeachingRequirement(
                ts, stg, null, type,
                count, weekType, roomType);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
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
}
