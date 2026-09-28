package schedule.model;

public enum WeekType {
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