package schedule.model;

public class Teacher extends Person {
    private String department;
    private String position;
    private String email;

    public Teacher(String firstName, String lastName,
                   String middleName, String department) {
        this(firstName, lastName, middleName, department, "", "");
    }

    public Teacher(String firstName, String lastName,
                   String middleName, String department,
                   String position, String email) {
        super(firstName, lastName, middleName);
        setDepartment(department);
        setPosition(position);
        setEmail(email);
    }

    @Override
    public String getRole() {
        return "Преподаватель";
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        if (department == null || department.isBlank()) {
            throw new IllegalArgumentException(
                    "Department cannot be empty");
        }
        this.department = department.trim();
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position == null ? "" : position.trim();
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email == null ? "" : email.trim();
    }
}

