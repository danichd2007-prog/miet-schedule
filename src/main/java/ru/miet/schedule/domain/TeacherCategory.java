package ru.miet.schedule.domain;

/**
 * Категория преподавателя. Чем меньше priority, тем раньше размещаются
 * занятия: совместители сами задают дни работы, поэтому идут первыми.
 */
public enum TeacherCategory {
    PART_TIME("Совместитель", 1, true),
    STAFF("Штатный сотрудник", 2, false);

    private final String russianName;
    private final int priority;
    private final boolean availabilityIsHard;

    TeacherCategory(String russianName, int priority, boolean availabilityIsHard) {
        this.russianName = russianName;
        this.priority = priority;
        this.availabilityIsHard = availabilityIsHard;
    }

    public String getRussianName() {
        return russianName;
    }

    public int priority() {
        return priority;
    }

    /** true, если указанная преподавателем доступность обязательна к соблюдению. */
    public boolean availabilityIsHard() {
        return availabilityIsHard;
    }

    @Override
    public String toString() {
        return russianName;
    }
}
