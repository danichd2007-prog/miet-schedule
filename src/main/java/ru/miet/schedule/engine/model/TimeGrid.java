package ru.miet.schedule.engine.model;

/**
 * Числовая сетка времени типового 4-недельного цикла.
 * Слот = (фаза * 6 + день) * 8 + (пара - 1); всего 4 * 6 * 8 = 192 слота.
 * День: 0..5 (пн..сб), пара: 1..8 (как в расписании звонков).
 */
public final class TimeGrid {

    public static final int PHASES = 4;
    public static final int DAYS = 6;
    public static final int PAIRS = 8;
    public static final int SLOTS_PER_PHASE = DAYS * PAIRS;
    public static final int TOTAL_SLOTS = PHASES * SLOTS_PER_PHASE;
    public static final int ALL_PHASES = (1 << PHASES) - 1;

    private TimeGrid() {
    }

    public static int slot(int phase, int day, int pair) {
        if (phase < 0 || phase >= PHASES) {
            throw new IllegalArgumentException("Phase must be 0.." + (PHASES - 1));
        }

        if (day < 0 || day >= DAYS) {
            throw new IllegalArgumentException("Day must be 0.." + (DAYS - 1));
        }

        if (pair < 1 || pair > PAIRS) {
            throw new IllegalArgumentException("Pair must be 1.." + PAIRS);
        }

        return (phase * DAYS + day) * PAIRS + (pair - 1);
    }

    public static int phaseOf(int slot) {
        return slot / SLOTS_PER_PHASE;
    }

    public static int dayOf(int slot) {
        return (slot % SLOTS_PER_PHASE) / PAIRS;
    }

    public static int pairOf(int slot) {
        return slot % PAIRS + 1;
    }

    /** Проверяет, что занятие длительностью duration, начатое с пары pair, помещается в день. */
    public static void requireFits(int day, int pair, int duration) {
        if (day < 0 || day >= DAYS) {
            throw new IllegalArgumentException("Day must be 0.." + (DAYS - 1));
        }

        if (duration < 1 || duration > 2) {
            throw new IllegalArgumentException("Duration must be 1 or 2");
        }

        if (pair < 1 || pair + duration - 1 > PAIRS) {
            throw new IllegalArgumentException(
                    "Lesson does not fit into the day: pair=" + pair
                            + ", duration=" + duration);
        }
    }

    public static void requirePhaseMask(int phaseMask) {
        if (phaseMask < 1 || phaseMask > ALL_PHASES) {
            throw new IllegalArgumentException("Phase mask must be 1.." + ALL_PHASES);
        }
    }
}
