package schedule.model;

public enum WeekType {
    F_NUMERATOR("Первый числитель"),
    F_DENOMINATOR("Первый знаменатель"),
    S_NUMERATOR("Второй числитель"),
    S_DENOMINATOR("Второй знаменатель"),
    NUMERATOR("Числитель"),
    DENOMINATOR("Знаменатель"),
    EVERY_WEEK("Каждую неделю");

    private final String russianName;

    WeekType(String russianName) {
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
