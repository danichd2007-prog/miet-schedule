package schedule.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class Schedule {
    private Long id;
    private String name;
    private String description;
    private final List<ScheduleEntry> entries = new ArrayList<>();

    public Schedule() {
        this("Новое расписание", "");
    }

    public Schedule(String name, String description) {
        setName(name);
        setDescription(description);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(
                    "ID must be positive");
        }

        if (this.id != null && !this.id.equals(id)) {
            throw new IllegalStateException(
                    "ID cannot be changed");
        }

        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Schedule name cannot be empty");
        }

        this.name = name.trim();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description =
                description == null ? "" : description.trim();
    }

    public void addEntry(ScheduleEntry entry) {
        Objects.requireNonNull(entry, "Entry cannot be null");

        if (!entries.contains(entry)) {
            entries.add(entry);
        }
    }

    public void removeEntry(ScheduleEntry entry) {
        entries.remove(entry);
    }

    public List<ScheduleEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Schedule schedule = (Schedule) o;

        return id != null && id.equals(schedule.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return name + " (" + entries.size() + " занятий)";
    }
}
