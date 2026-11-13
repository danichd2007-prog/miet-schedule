package ru.miet.schedule.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeekTypeAndCalendarTest {

    private final AcademicCalendar calendar =
            new AcademicCalendar(LocalDate.of(2026, 8, 31), 16);

    @Test
    void masksMatchPhases() {
        assertEquals(0b0001, WeekType.F_NUMERATOR.phaseMask());
        assertEquals(0b0101, WeekType.NUMERATOR.phaseMask());
        assertEquals(0b1010, WeekType.DENOMINATOR.phaseMask());
        assertEquals(0b1111, WeekType.EVERY_WEEK.phaseMask());
    }

    @Test
    void coverageIsSetInclusion() {
        assertTrue(WeekType.EVERY_WEEK.covers(WeekType.S_DENOMINATOR));
        assertTrue(WeekType.NUMERATOR.covers(WeekType.S_NUMERATOR));
        assertFalse(WeekType.NUMERATOR.covers(WeekType.F_DENOMINATOR));
        assertFalse(WeekType.F_NUMERATOR.covers(WeekType.NUMERATOR));
    }

    @Test
    void ofMaskFindsTypeOrNothing() {
        assertEquals(WeekType.DENOMINATOR, WeekType.ofMask(0b1010).orElseThrow());
        assertTrue(WeekType.ofMask(0b0011).isEmpty());
    }

    @Test
    void calendarMapsWeeksToPhases() {
        assertEquals(WeekType.F_NUMERATOR, calendar.getWeekType(1));
        assertEquals(WeekType.F_DENOMINATOR, calendar.getWeekType(2));
        assertEquals(WeekType.S_NUMERATOR, calendar.getWeekType(3));
        assertEquals(WeekType.S_DENOMINATOR, calendar.getWeekType(4));
        assertEquals(WeekType.F_NUMERATOR, calendar.getWeekType(5));
        assertEquals(WeekType.S_DENOMINATOR, calendar.getWeekType(16));
    }

    @Test
    void weeksOfTypeAreExpandedByCalendar() {
        assertEquals(List.of(1, 5, 9, 13), calendar.weeksOf(WeekType.F_NUMERATOR));
        assertEquals(List.of(1, 3, 5, 7, 9, 11, 13, 15),
                calendar.weeksOf(WeekType.NUMERATOR));
        assertEquals(16, calendar.weeksOf(WeekType.EVERY_WEEK).size());
    }

    @Test
    void datesAreCountedFromFirstMonday() {
        assertEquals(LocalDate.of(2026, 8, 31),
                calendar.getDate(1, WeekDay.MONDAY));
        assertEquals(LocalDate.of(2026, 9, 5),
                calendar.getDate(1, WeekDay.SATURDAY));
        assertEquals(LocalDate.of(2026, 9, 7),
                calendar.getDate(2, WeekDay.MONDAY));
    }

    @Test
    void invalidCalendarIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new AcademicCalendar(LocalDate.of(2026, 9, 1), 16)); // вторник
        assertThrows(IllegalArgumentException.class,
                () -> new AcademicCalendar(LocalDate.of(2026, 8, 31), 0));
        assertThrows(IllegalArgumentException.class,
                () -> calendar.getWeekType(17));
    }

    @Test
    void availabilityUsesWeekTypeCoverage() {
        Teacher teacher = new Teacher("Н.", "Воронова", "М.", "Кафедра МИЭТ");
        TeacherAvailability availability = new TeacherAvailability(
                teacher, WeekDay.TUESDAY, 2, 5, WeekType.NUMERATOR);

        assertTrue(availability.allows(WeekDay.TUESDAY, 3, WeekType.F_NUMERATOR));
        assertFalse(availability.allows(WeekDay.TUESDAY, 3, WeekType.F_DENOMINATOR));
        assertFalse(availability.allows(WeekDay.TUESDAY, 6, WeekType.F_NUMERATOR));
        assertFalse(availability.allows(WeekDay.MONDAY, 3, WeekType.F_NUMERATOR));
    }
}
