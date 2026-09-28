package schedule.model;

public class StudentGroup {
    private String name;
    private int course;

    public StudentGroup(String name, int course) {
        this.name = name;
        this.course = course;
    }

    public String getName() {
        return name;
    }

    public int getCourse() {
        return course;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setCourse(int course) {
        this.course = course;
    }

    @Override
    public String toString() {
        return name + ", " + course + " курс";
    }
}