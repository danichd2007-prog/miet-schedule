package ru.miet.schedule.engine.model;

import java.util.Arrays;

/**
 * Индекс занятости ресурсов (преподаватели, группы, аудитории) по сетке времени.
 * Ресурс задаётся номером 0..resources-1; для каждого ресурса хранится 192 бита.
 * Проверка «свободно ли» работает за O(фазы * длительность), то есть не более 8 операций.
 *
 * Размещение описывается так: фазы цикла (битовая маска), день, первая пара и длительность.
 * Например, лекция каждую неделю — маска 0b1111, только по числителю — 0b0101.
 */
public final class Occupancy {

    private static final int WORDS = (TimeGrid.TOTAL_SLOTS + 63) / 64;

    private final int resources;
    private final long[] bits;

    public Occupancy(int resources) {
        if (resources < 0) {
            throw new IllegalArgumentException("Resource count cannot be negative");
        }

        this.resources = resources;
        this.bits = new long[resources * WORDS];
    }

    private Occupancy(int resources, long[] bits) {
        this.resources = resources;
        this.bits = bits;
    }

    public int resourceCount() {
        return resources;
    }

    /** Независимая копия, изменения в одной не влияют на другую. */
    public Occupancy copy() {
        return new Occupancy(resources, Arrays.copyOf(bits, bits.length));
    }

    /**
     * Первый занятый слот среди тех, что требует размещение, либо -1, если всё свободно.
     * Нужен для объяснения причины отказа («в этот слот уже есть занятие»).
     */
    public int findConflict(
            int resource,
            int phaseMask,
            int day,
            int pair,
            int duration) {

        checkResource(resource);
        TimeGrid.requirePhaseMask(phaseMask);
        TimeGrid.requireFits(day, pair, duration);

        for (int phase = 0; phase < TimeGrid.PHASES; phase++) {
            if ((phaseMask & (1 << phase)) == 0) {
                continue;
            }

            for (int k = 0; k < duration; k++) {
                int slot = TimeGrid.slot(phase, day, pair + k);

                if (isBusy(resource, slot)) {
                    return slot;
                }
            }
        }

        return -1;
    }

    public boolean isFree(
            int resource,
            int phaseMask,
            int day,
            int pair,
            int duration) {

        return findConflict(resource, phaseMask, day, pair, duration) < 0;
    }

    /** Занимает слоты; если хоть один занят, бросает исключение и ничего не меняет. */
    public void occupy(
            int resource,
            int phaseMask,
            int day,
            int pair,
            int duration) {

        int conflict = findConflict(resource, phaseMask, day, pair, duration);

        if (conflict >= 0) {
            throw new IllegalStateException(
                    "Slot " + conflict + " of resource " + resource
                            + " is already occupied");
        }

        forEachSlot(resource, phaseMask, day, pair, duration, true);
    }

    /** Освобождает слоты; если какой-то слот не был занят, бросает исключение. */
    public void release(
            int resource,
            int phaseMask,
            int day,
            int pair,
            int duration) {

        checkResource(resource);
        TimeGrid.requirePhaseMask(phaseMask);
        TimeGrid.requireFits(day, pair, duration);

        for (int phase = 0; phase < TimeGrid.PHASES; phase++) {
            if ((phaseMask & (1 << phase)) == 0) {
                continue;
            }

            for (int k = 0; k < duration; k++) {
                int slot = TimeGrid.slot(phase, day, pair + k);

                if (!isBusy(resource, slot)) {
                    throw new IllegalStateException(
                            "Slot " + slot + " of resource " + resource
                                    + " is not occupied");
                }
            }
        }

        forEachSlot(resource, phaseMask, day, pair, duration, false);
    }

    public boolean isBusy(int resource, int slot) {
        checkResource(resource);

        if (slot < 0 || slot >= TimeGrid.TOTAL_SLOTS) {
            throw new IllegalArgumentException("Slot out of range: " + slot);
        }

        return (bits[resource * WORDS + (slot >> 6)] & (1L << (slot & 63))) != 0;
    }

    /**
     * Занятость ресурса в один день одной фазы цикла: бит k = пара k+1 занята.
     * Нужна, чтобы дёшево считать окна и нагрузку дня.
     */
    public int dayMask(int resource, int phase, int day) {
        checkResource(resource);

        if (phase < 0 || phase >= TimeGrid.PHASES) {
            throw new IllegalArgumentException("Phase must be 0.." + (TimeGrid.PHASES - 1));
        }

        if (day < 0 || day >= TimeGrid.DAYS) {
            throw new IllegalArgumentException("Day must be 0.." + (TimeGrid.DAYS - 1));
        }

        int base = (phase * TimeGrid.DAYS + day) * TimeGrid.PAIRS;
        long word = bits[resource * WORDS + (base >> 6)];

        return (int) ((word >>> (base & 63)) & 0xFF);
    }

    /** Сколько слотов занято у ресурса. */
    public int busySlots(int resource) {
        checkResource(resource);

        int count = 0;

        for (int w = 0; w < WORDS; w++) {
            count += Long.bitCount(bits[resource * WORDS + w]);
        }

        return count;
    }

    private void forEachSlot(
            int resource,
            int phaseMask,
            int day,
            int pair,
            int duration,
            boolean set) {

        for (int phase = 0; phase < TimeGrid.PHASES; phase++) {
            if ((phaseMask & (1 << phase)) == 0) {
                continue;
            }

            for (int k = 0; k < duration; k++) {
                int slot = TimeGrid.slot(phase, day, pair + k);
                int index = resource * WORDS + (slot >> 6);
                long mask = 1L << (slot & 63);

                if (set) {
                    bits[index] |= mask;
                } else {
                    bits[index] &= ~mask;
                }
            }
        }
    }

    private void checkResource(int resource) {
        if (resource < 0 || resource >= resources) {
            throw new IllegalArgumentException(
                    "Unknown resource: " + resource);
        }
    }
}
