package schedule.model;

public class Lesson {
    private Subject subject;
    private Teacher teacher;
    private StudentGroup group;
    private ClassroomType requiredClassroomType;

    public Lesson(Subject subject, Teacher teacher, StudentGroup group,
                  ClassroomType requiredClassroomType) {
        this.subject = subject;
        this.teacher = teacher;
        this.group = group;
        this.requiredClassroomType = requiredClassroomType;
    }

    public Subject getSubject() {
        return subject;
    }

    public Teacher getTeacher() {
        return teacher;
    }

    public StudentGroup getGroup() {
        return group;
    }

    public ClassroomType getRequiredClassroomType() {
        return requiredClassroomType;
    }

    public void setSubject(Subject subject) {
        this.subject = subject;
    }

    public void setTeacher(Teacher teacher) {
        this.teacher = teacher;
    }

    public void setGroup(StudentGroup group) {
        this.group = group;
    }

    public void setRequiredClassroomType(ClassroomType requiredClassroomType) {
        this.requiredClassroomType = requiredClassroomType;
    }

    @Override
    public String toString() {
        return subject + " | "
                + teacher.getShortName() + " | "
                + group.getName();
    }
}