package ru.miet.schedule.domain;

public final class Direction extends Entity {

    private final String name;
    private final String code;

    public Direction(String name, String code) {
        this.name = requireText(name, "Direction name");
        this.code = requireText(code, "Direction code");
    }

    public String getName() {
        return name;
    }

    public String getCode() {
        return code;
    }

    @Override
    public String toString() {
        return code + " — " + name;
    }
}
