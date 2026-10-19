
package schedule.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class Stream {
    private Long id;
    private String name;
    private final Subject subject;
    private final List<StudentGroup> groups = new ArrayList<>();

    public Stream(String name, Subject subject) {
        setName(name);
        this.subject = Objects.requireNonNull(
                subject, "Subject cannot be null");
    }

    public void addGroup(StudentGroup group) {
        Objects.requireNonNull(group, "Group cannot be null");

        if (!groups.contains(group)) {
            groups.add(group);
        }
    }

    public void removeGroup(StudentGroup group) {
        groups.remove(group);
    }

    public List<StudentGroup> getGroups() {
        return Collections.unmodifiableList(groups);
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
                    "Stream name cannot be empty");
        }

        this.name = name.trim();
    }

    public Subject getSubject() {
        return subject;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Stream stream = (Stream) o;

        return id != null && id.equals(stream.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return name + " — " + subject.getName() +
                " (" + groups.size() + " групп)";
    }
}

