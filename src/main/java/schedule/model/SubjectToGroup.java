
package schedule.model;

import java.util.Objects;

public class SubjectToGroup {
    private Long id;
    private final Subject subject;
    private final StudentGroup group;

    public SubjectToGroup(Subject subject, StudentGroup group) {
        this.subject = Objects.requireNonNull(subject, "subject");
        this.group = Objects.requireNonNull(group, "group");
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

    public Subject getSubject() {
        return subject;
    }

    public StudentGroup getGroup() {
        return group;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SubjectToGroup that = (SubjectToGroup) o;

        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : getClass().hashCode();
    }

    @Override
    public String toString() {
        return subject.getName() + " — " + group.getName();
    }
}

