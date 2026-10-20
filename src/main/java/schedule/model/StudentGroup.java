package schedule.model;

import java.util.Objects;

public class StudentGroup {
    private Long id;
    private String name;
    private int course;
    private Direction direction;

    public StudentGroup(String name, int course) {
        this(name, course, null);
    }

    public StudentGroup(String name, int course, Direction direction) {
        setName(name);
        setCourse(course);
        this.direction = direction;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("ID must be positive");
        }

        if (this.id != null && !this.id.equals(id)) {
            throw new IllegalStateException("ID cannot be changed");
        }

        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Group name cannot be empty");
        }

        this.name = name.trim();
    }

    public int getCourse() {
        return course;
    }

    public void setCourse(int course) {
        if (course < 1 || course > 6) {
            throw new IllegalArgumentException(
                    "Course must be between 1 and 6");
        }

        this.course = course;
    }

    public Direction getDirection() {
        return direction;
    }

    public void setDirection(Direction direction) {
        this.direction = Objects.requireNonNull(
                direction, "Direction cannot be null");
    }

    @Override
    public String toString() {
        return name + ", " + course + " курс";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        StudentGroup that = (StudentGroup) o;

        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : getClass().hashCode();
    }

}
