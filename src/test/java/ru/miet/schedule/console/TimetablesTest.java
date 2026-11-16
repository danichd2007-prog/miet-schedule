package ru.miet.schedule.console;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimetablesTest {

    @Test
    void csvCellsAreEscaped() {
        assertEquals("abc", Timetables.cell("abc"));
        assertEquals("\"a;b\"", Timetables.cell("a;b"));
        assertEquals("\"a\"\"b\"", Timetables.cell("a\"b"));
        assertEquals("'=1+1", Timetables.cell("=1+1"));
        assertEquals("", Timetables.cell(null));
    }
}
