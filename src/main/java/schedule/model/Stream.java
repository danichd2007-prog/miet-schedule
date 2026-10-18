
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
        this.name = Objects.requireNonNull(name, "name");
        this.subject = Objects.requireNonNull(subject, "subject");
    }

    public void addGroup(StudentGroup group) {
        Objects.requireNonNull(group, "group");

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
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    public Subject getSubject() {
        return subject;
    }
}
