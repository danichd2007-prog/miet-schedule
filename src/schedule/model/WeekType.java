package schedule.model;

public enum WeekType {
    F_NUMERATOR("Первый Числитель"),
    F_DENOMINATOR("Первый Знаменатель"),
    S_NUMERATOR("Второй Числитель"),
    S_DENOMINATOR("Второй Знаменатель"),
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