
package schedule.model;

public class Direction {
    private Long id;
    private String name;
    private String code;

    public Direction(String name, String code) {
        setName(name);
        setCode(code);
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
                    "Direction name cannot be empty");
        }

        this.name = name.trim();
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException(
                    "Direction code cannot be empty");
        }

        this.code = code.trim();
    }

    @Override
    public String toString() {
        return code + " — " + name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Direction that = (Direction) o;

        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
