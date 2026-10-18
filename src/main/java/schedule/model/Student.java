package schedule.model;

public class Student extends Person {
    private StudentGroup group;

    public Student(String firstName, String lastName, String middleName,
                   StudentGroup group) {
        super(firstName, lastName, middleName);
        this.group = group;
    }

    @Override
    public String getRole() {
        return "Студент";
    }

    public StudentGroup getGroup() {
        return group;
    }

    public void setGroup(StudentGroup group) {
        this.group = group;
    }
}