package schedule.model;

public class Teacher extends Person {
    private String department;

    public Teacher(String firstName, String lastName, String middleName,
                   String department) {
        super(firstName, lastName, middleName);
        this.department = department;
    }

    @Override
    public String getRole() {
        return "Преподаватель";
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }
}