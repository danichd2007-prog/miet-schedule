package schedule.model;

public enum ClassroomType {
    LECTURE("Лекционная"),
    LABORATORY("Лабораторная"),
    COMPUTER("Компьютерный класс"),
    REGULAR("Обычная");

    private final String russianName;

    ClassroomType(String russianName) {
        this.russianName = russianName;
    }

    public String getRussianName() {
        return russianName;
    }

    @Override
    public String toString() {
        return russianName;
    }
}