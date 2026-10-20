
package schedule.model;

public class Subject {
    private Long id;
    private String name;
    private String code;
    private int totalHours;

    public Subject(String name) {
        this(name, "", 0);
    }

    public Subject(String name, String code, int totalHours) {
        setName(name);
        setCode(code);
        setTotalHours(totalHours);
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
                    "Subject name cannot be empty");
        }

        this.name = name.trim();
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code == null ? "" : code.trim();
    }

    public int getTotalHours() {
        return totalHours;
    }

    public void setTotalHours(int totalHours) {
        if (totalHours < 0) {
            throw new IllegalArgumentException(
                    "Total hours cannot be negative");
        }

        this.totalHours = totalHours;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Subject subject = (Subject) o;

        return id != null && id.equals(subject.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : getClass().hashCode();
    }
}
