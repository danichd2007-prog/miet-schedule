package ru.miet.schedule.domain;

import java.util.Optional;

/**
 * Тип недели в 4-недельном цикле. Каждый тип задаёт набор фаз цикла
 * (бит 0 = первый числитель, бит 1 = первый знаменатель,
 * бит 2 = второй числитель, бит 3 = второй знаменатель).
 */
public enum WeekType {
    F_NUMERATOR("Первый числитель", 0b0001),
    F_DENOMINATOR("Первый знаменатель", 0b0010),
    S_NUMERATOR("Второй числитель", 0b0100),
    S_DENOMINATOR("Второй знаменатель", 0b1000),
    NUMERATOR("Числитель", 0b0101),
    DENOMINATOR("Знаменатель", 0b1010),
    EVERY_WEEK("Каждую неделю", 0b1111);

    public static final int PHASES = 4;

    private final String russianName;
    private final int phaseMask;

    WeekType(String russianName, int phaseMask) {
        this.russianName = russianName;
        this.phaseMask = phaseMask;
    }

    public String getRussianName() {
        return russianName;
    }

    /** Битовая маска фаз цикла, в которых этот тип недели действует. */
    public int phaseMask() {
        return phaseMask;
    }

    public boolean coversPhase(int phase) {
        if (phase < 0 || phase >= PHASES) {
            throw new IllegalArgumentException("Phase must be 0..3");
        }

        return (phaseMask & (1 << phase)) != 0;
    }

    /** true, если этот тип покрывает все фазы типа {@code other}. */
    public boolean covers(WeekType other) {
        return (phaseMask & other.phaseMask) == other.phaseMask;
    }

    /** Одиночная фаза цикла (0..3) как тип недели. */
    public static WeekType ofPhase(int phase) {
        return switch (phase) {
            case 0 -> F_NUMERATOR;
            case 1 -> F_DENOMINATOR;
            case 2 -> S_NUMERATOR;
            case 3 -> S_DENOMINATOR;
            default -> throw new IllegalArgumentException("Phase must be 0..3");
        };
    }

    /** Тип недели по маске фаз, если такой существует. */
    public static Optional<WeekType> ofMask(int mask) {
        for (WeekType type : values()) {
            if (type.phaseMask == mask) {
                return Optional.of(type);
            }
        }

        return Optional.empty();
    }

    @Override
    public String toString() {
        return russianName;
    }
}
