package schedule.model;

import java.util.Objects;

public class Classroom {
    private Long id;
    private String number;
    private String building;
    private ClassroomType type;
    private int seats;
    private boolean isAvailableForExams;

    public Classroom(String number,
                     String building,
                     ClassroomType type,
                     int seats,
                     boolean isAvailableForExams) {

        setNumber(number);
        setBuilding(building);
        setType(type);
        setSeats(seats);
        setAvailableForExams(isAvailableForExams);
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

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        if (number == null || number.isBlank()) {
            throw new IllegalArgumentException(
                    "Classroom number cannot be empty");
        }

        this.number = number.trim();
    }

    public String getBuilding() {
        return building;
    }

    public void setBuilding(String building) {
        if (building == null || building.isBlank()) {
            throw new IllegalArgumentException(
                    "Building cannot be empty");
        }

        this.building = building.trim();
    }

    public ClassroomType getType() {
        return type;
    }

    public void setType(ClassroomType type) {
        this.type = Objects.requireNonNull(
                type, "Classroom type cannot be null");
    }

    public int getSeats() {
        return seats;
    }

    public void setSeats(int seats) {
        if (seats <= 0) {
            throw new IllegalArgumentException(
                    "Seats must be positive");
        }

        this.seats = seats;
    }

    public boolean isAvailableForExams() {
        return isAvailableForExams;
    }

    public void setAvailableForExams(boolean availableForExams) {
        this.isAvailableForExams = availableForExams;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Classroom classroom = (Classroom) o;

        return id != null && id.equals(classroom.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "Аудитория " + number +
                ", корпус " + building +
                " (" + type + ", " + seats + " мест)";
    }
}
