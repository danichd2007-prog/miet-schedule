package ru.miet.schedule.domain;

public enum ClassroomType {
    LECTURE("Лекционная"),
    LABORATORY("Лабораторная"),
    COMPUTER("Компьютерный класс"),
    REGULAR("Обычная"),
    SPORT("Спортивный зал");

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
