package ru.miet.schedule.domain;

public final class Subject extends Entity {

    private final String name;

    public Subject(String name) {
        this.name = requireText(name, "Subject name");
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }
}
