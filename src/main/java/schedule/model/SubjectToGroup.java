package schedule.model;

import java.util.Objects;

public class SubjectToGroup {
    private Long id;
    private Subject subject;
    private StudentGroup group;

    public SubjectToGroup(Subject subject, StudentGroup group) {
        this.subject = Objects.requireNonNull(subject, "subject");
        this.group = Objects.requireNonNull(group, "group");
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Subject getSubject() {
        return subject;
    }

    public StudentGroup getGroup() {
        return group;
    }
}
