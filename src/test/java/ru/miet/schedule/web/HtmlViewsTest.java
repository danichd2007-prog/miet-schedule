package ru.miet.schedule.web;

import org.junit.jupiter.api.Test;
import ru.miet.schedule.domain.Classroom;
import ru.miet.schedule.domain.ClassroomType;
import ru.miet.schedule.domain.StudentGroup;
import ru.miet.schedule.domain.Teacher;
import ru.miet.schedule.domain.TeacherSubject;
import ru.miet.schedule.domain.WeekDay;
import ru.miet.schedule.engine.Fixtures;
import ru.miet.schedule.engine.model.ScheduledLesson;
import ru.miet.schedule.web.HtmlViews.Kind;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HtmlViewsTest {

    private final StudentGroup group = Fixtures.group("ПИН-27", 25);
    private final Teacher teacher = Fixtures.teacher("Иванов");
    private final Classroom room = Fixtures.room("3229", ClassroomType.REGULAR, 30);

    private ScheduledLesson seminar(String subject, int week, WeekDay day, int pair) {
        TeacherSubject q = Fixtures.qualification(teacher, Fixtures.subject(subject));
        return new ScheduledLesson(Fixtures.seminar(q, group, 1), week, day, pair, 1, room);
    }

    private ScheduledLesson lab(String subject, int week, WeekDay day, int pair) {
        TeacherSubject q = Fixtures.qualification(teacher, Fixtures.subject(subject));
        return new ScheduledLesson(
                Fixtures.lab(q, group, 2, ClassroomType.REGULAR), week, day, pair, 2, room);
    }

    private String page(int week, ScheduledLesson... lessons) {
        return HtmlViews.timetable(7, Kind.GROUP, 1, "Группа ПИН-27", week, Fixtures.CALENDAR, List.of(lessons));
    }

    private static int count(String text, String part) {
        int n = 0;

        for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + 1)) {
            n++;
        }

        return n;
    }

    @Test
    void escapesAnyTextFromData() {
        String html = page(1, seminar("<script>alert(1)</script>", 1, WeekDay.MONDAY, 1));

        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
        assertFalse(html.contains("<script>"));
    }

    @Test
    void escapeCoversQuotesAndAmpersand() {
        assertEquals("&amp;&lt;&gt;&quot;&#39;", HtmlViews.esc("&<>\"'"));
        assertEquals("", HtmlViews.esc(null));
    }

    @Test
    void onlyTheRequestedWeekIsShown() {
        String html = page(1,
                seminar("Первая неделя", 1, WeekDay.MONDAY, 1),
                seminar("Вторая неделя", 2, WeekDay.MONDAY, 1));

        assertTrue(html.contains("Первая неделя"));
        assertFalse(html.contains("Вторая неделя"));
    }

    @Test
    void blockLessonSpansTwoRowsWithoutExtraCell() {
        String empty = page(1);
        String withBlock = page(1, lab("Лабораторная", 1, WeekDay.TUESDAY, 3));

        assertTrue(withBlock.contains("rowspan=\"2\""));
        // клетка блока заменяет две пустые, поэтому ячеек становится на одну меньше
        assertEquals(count(empty, "<td"), count(withBlock, "<td") + 1);
    }

    @Test
    void cellShowsDifferentDetailsForGroupAndTeacher() {
        ScheduledLesson lesson = seminar("Физика", 1, WeekDay.FRIDAY, 2);

        String forGroup = HtmlViews.timetable(7, Kind.GROUP, 1, "g", 1, Fixtures.CALENDAR, List.of(lesson));
        String forTeacher = HtmlViews.timetable(7, Kind.TEACHER, 1, "t", 1, Fixtures.CALENDAR, List.of(lesson));

        assertTrue(forGroup.contains("Иванов"));
        assertTrue(forTeacher.contains("ПИН-27"));
    }

    @Test
    void headerShowsWeekDatesAndAllWeekTabs() {
        String html = page(2);

        assertTrue(html.contains("Неделя 2: 07.09 – 12.09"));
        assertEquals(16, count(html, "?week="));
    }
}
