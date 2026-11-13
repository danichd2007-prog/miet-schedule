package ru.miet.schedule.domain;

import java.util.Objects;

public final class Classroom extends Entity {

    private final String number;
    private final String building;
    private final ClassroomType type;
    private final int seats;

    public Classroom(
            String number,
            String building,
            ClassroomType type,
            int seats) {

        if (seats <= 0) {
            throw new IllegalArgumentException("Seats must be positive");
        }

        this.number = requireText(number, "Classroom number");
        this.building = requireText(building, "Building");
        this.type = Objects.requireNonNull(type, "Classroom type cannot be null");
        this.seats = seats;
    }

    public String getNumber() {
        return number;
    }

    public String getBuilding() {
        return building;
    }

    public ClassroomType getType() {
        return type;
    }

    public int getSeats() {
        return seats;
    }

    @Override
    public String toString() {
        return "Аудитория " + number + ", корпус " + building
                + " (" + type + ", " + seats + " мест)";
    }
}
