package ru.miet.schedule.domain;

import java.util.Objects;

/** Интервал пар в определённый день и тип недели, когда преподаватель может вести занятия. */
public final class TeacherAvailability extends Entity {

    private final Teacher teacher;
    private final WeekDay day;
    private final int fromPair;
    private final int toPair;
    private final WeekType weekType;

    public TeacherAvailability(
            Teacher teacher,
            WeekDay day,
            int fromPair,
            int toPair,
            WeekType weekType) {

        this.teacher = Objects.requireNonNull(teacher, "Teacher cannot be null");
        this.day = Objects.requireNonNull(day, "Day cannot be null");
        this.weekType = Objects.requireNonNull(weekType, "Week type cannot be null");

        if (fromPair < 1 || toPair < fromPair || toPair > 8) {
            throw new IllegalArgumentException("Invalid pair range");
        }

        this.fromPair = fromPair;
        this.toPair = toPair;
    }

    /** Доступен ли преподаватель в этот день на эту пару для указанного типа недели. */
    public boolean allows(WeekDay day, int pair, WeekType required) {
        return this.day == day
                && pair >= fromPair
                && pair <= toPair
                && weekType.covers(required);
    }

    public Teacher getTeacher() {
        return teacher;
    }

    public WeekDay getDay() {
        return day;
    }

    public int getFromPair() {
        return fromPair;
    }

    public int getToPair() {
        return toPair;
    }

    public WeekType getWeekType() {
        return weekType;
    }

    @Override
    public String toString() {
        return teacher.getShortName() + ": " + day
                + ", пары " + fromPair + "-" + toPair + ", " + weekType;
    }
}
