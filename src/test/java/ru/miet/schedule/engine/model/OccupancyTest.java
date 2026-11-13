package ru.miet.schedule.engine.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OccupancyTest {

    private static final int EVERY = 0b1111;
    private static final int NUMERATOR = 0b0101;
    private static final int DENOMINATOR = 0b1010;
    private static final int MONDAY = 0;

    @Test
    void everythingIsFreeInitially() {
        Occupancy occupancy = new Occupancy(3);

        assertTrue(occupancy.isFree(0, EVERY, MONDAY, 1, 2));
        assertEquals(0, occupancy.busySlots(0));
    }

    @Test
    void occupiedLessonBlocksOverlappingPairs() {
        Occupancy occupancy = new Occupancy(1);
        occupancy.occupy(0, EVERY, MONDAY, 3, 2); // пары 3-4

        assertFalse(occupancy.isFree(0, EVERY, MONDAY, 3, 1));
        assertFalse(occupancy.isFree(0, EVERY, MONDAY, 4, 1));
        assertFalse(occupancy.isFree(0, EVERY, MONDAY, 2, 2)); // 2-3 пересекается с 3
        assertTrue(occupancy.isFree(0, EVERY, MONDAY, 2, 1));
        assertTrue(occupancy.isFree(0, EVERY, MONDAY, 5, 2));
        assertEquals(8, occupancy.busySlots(0)); // 4 фазы * 2 пары
    }

    @Test
    void numeratorAndDenominatorDoNotConflict() {
        Occupancy occupancy = new Occupancy(1);
        occupancy.occupy(0, NUMERATOR, MONDAY, 1, 1);

        assertTrue(occupancy.isFree(0, DENOMINATOR, MONDAY, 1, 1));
        assertFalse(occupancy.isFree(0, NUMERATOR, MONDAY, 1, 1));
        assertFalse(occupancy.isFree(0, EVERY, MONDAY, 1, 1));
        assertFalse(occupancy.isFree(0, 0b0001, MONDAY, 1, 1)); // один из двух числителей
    }

    @Test
    void differentResourcesAndDaysAreIndependent() {
        Occupancy occupancy = new Occupancy(2);
        occupancy.occupy(0, EVERY, MONDAY, 1, 1);

        assertTrue(occupancy.isFree(1, EVERY, MONDAY, 1, 1));
        assertTrue(occupancy.isFree(0, EVERY, 1, 1, 1));
    }

    @Test
    void releaseRestoresFreeState() {
        Occupancy occupancy = new Occupancy(1);
        occupancy.occupy(0, EVERY, 5, 7, 2);
        occupancy.release(0, EVERY, 5, 7, 2);

        assertTrue(occupancy.isFree(0, EVERY, 5, 7, 2));
        assertEquals(0, occupancy.busySlots(0));
    }

    @Test
    void occupyingTwiceFailsAndChangesNothing() {
        Occupancy occupancy = new Occupancy(1);
        occupancy.occupy(0, NUMERATOR, MONDAY, 2, 1);

        assertThrows(IllegalStateException.class,
                () -> occupancy.occupy(0, EVERY, MONDAY, 2, 1));

        // знаменатель не должен был занят неудачной попыткой
        assertTrue(occupancy.isFree(0, DENOMINATOR, MONDAY, 2, 1));
    }

    @Test
    void releasingFreeSlotFails() {
        Occupancy occupancy = new Occupancy(1);

        assertThrows(IllegalStateException.class,
                () -> occupancy.release(0, EVERY, MONDAY, 1, 1));
    }

    @Test
    void lessonMustFitIntoDay() {
        Occupancy occupancy = new Occupancy(1);

        assertThrows(IllegalArgumentException.class,
                () -> occupancy.isFree(0, EVERY, MONDAY, 8, 2));
        assertThrows(IllegalArgumentException.class,
                () -> occupancy.isFree(0, EVERY, MONDAY, 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> occupancy.isFree(0, EVERY, 6, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> occupancy.isFree(0, 0, MONDAY, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> occupancy.isFree(1, EVERY, MONDAY, 1, 1));
    }

    @Test
    void findConflictReturnsBusySlot() {
        Occupancy occupancy = new Occupancy(1);
        occupancy.occupy(0, 0b0100, 2, 5, 1); // второй числитель, среда, 5 пара

        int conflict = occupancy.findConflict(0, EVERY, 2, 5, 1);

        assertEquals(2, TimeGrid.phaseOf(conflict));
        assertEquals(2, TimeGrid.dayOf(conflict));
        assertEquals(5, TimeGrid.pairOf(conflict));
        assertEquals(-1, occupancy.findConflict(0, EVERY, 2, 6, 1));
    }

    @Test
    void lastSlotsOfTheGridWork() {
        Occupancy occupancy = new Occupancy(1);

        // последняя фаза, суббота, 7-8 пары: самые старшие биты третьего слова
        occupancy.occupy(0, 0b1000, 5, 7, 2);

        assertFalse(occupancy.isFree(0, 0b1000, 5, 8, 1));
        assertTrue(occupancy.isFree(0, 0b0111, 5, 7, 2));
        assertEquals(TimeGrid.TOTAL_SLOTS - 1, TimeGrid.slot(3, 5, 8));
    }

    @Test
    void copyIsIndependent() {
        Occupancy original = new Occupancy(1);
        original.occupy(0, EVERY, MONDAY, 1, 1);

        Occupancy copy = original.copy();
        copy.release(0, EVERY, MONDAY, 1, 1);
        copy.occupy(0, EVERY, MONDAY, 2, 1);

        assertFalse(original.isFree(0, EVERY, MONDAY, 1, 1));
        assertTrue(original.isFree(0, EVERY, MONDAY, 2, 1));
    }

    @Test
    void slotEncodingRoundTrips() {
        for (int phase = 0; phase < TimeGrid.PHASES; phase++) {
            for (int day = 0; day < TimeGrid.DAYS; day++) {
                for (int pair = 1; pair <= TimeGrid.PAIRS; pair++) {
                    int slot = TimeGrid.slot(phase, day, pair);

                    assertEquals(phase, TimeGrid.phaseOf(slot));
                    assertEquals(day, TimeGrid.dayOf(slot));
                    assertEquals(pair, TimeGrid.pairOf(slot));
                }
            }
        }
    }
}
