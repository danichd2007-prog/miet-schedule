package ru.miet.schedule.engine.model;

import org.junit.jupiter.api.Test;
import ru.miet.schedule.domain.ClassroomType;
import ru.miet.schedule.domain.StudentGroup;
import ru.miet.schedule.domain.TeacherSubject;
import ru.miet.schedule.domain.TeachingRequirement;
import ru.miet.schedule.engine.Fixtures;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeriesBuilderTest {

    private final StudentGroup group = Fixtures.group("Г-1", 25);
    private final TeacherSubject qualification = Fixtures.qualification(
            Fixtures.teacher("Иванов"), Fixtures.subject("Предмет"));

    private List<Series> build(TeachingRequirement... requirements) {
        return SeriesBuilder.build(List.of(requirements), Fixtures.CALENDAR);
    }

    @Test
    void weeklyLectureIsOneEveryWeekSeries() {
        List<Series> series = build(Fixtures.lecture(qualification, 16, group));

        assertEquals(1, series.size());
        assertEquals(1, series.get(0).alternatives().size());
        assertEquals(0b1111, series.get(0).alternatives().get(0).phaseMask());
        assertEquals(16, series.get(0).lessonCount());
    }

    @Test
    void everyOtherWeekGivesNumeratorOrDenominator() {
        List<Series> series = build(Fixtures.seminar(qualification, group, 8));

        assertEquals(1, series.size());
        assertEquals(List.of(0b0101, 0b1010),
                series.get(0).alternatives().stream()
                        .map(Series.Alternative::phaseMask).toList());
        assertEquals(8, series.get(0).lessonCount());
    }

    @Test
    void fourLessonsGiveOneCyclePhase() {
        // блок по две пары: 8 пар = 4 занятия = одно занятие за цикл
        List<Series> series = build(
                Fixtures.lab(qualification, group, 8, ClassroomType.COMPUTER));

        assertEquals(1, series.size());
        assertEquals(4, series.get(0).alternatives().size());
        assertEquals(4, series.get(0).lessonCount());
        assertEquals(2, series.get(0).duration());
    }

    @Test
    void remainderBecomesSecondSeries() {
        List<Series> series = build(Fixtures.seminar(qualification, group, 12));

        assertEquals(2, series.size());
        assertEquals(8, series.get(0).lessonCount());
        assertEquals(4, series.get(1).lessonCount());
    }

    @Test
    void tailLessonsUseFirstWeeksOfOnePhase() {
        List<Series> series = build(Fixtures.seminar(qualification, group, 6));

        assertEquals(2, series.size());
        assertEquals(4, series.get(0).lessonCount());
        assertEquals(2, series.get(1).lessonCount());

        Series.Alternative tail = series.get(1).alternatives().get(0);

        assertEquals(1, tail.phaseMask());
        assertEquals(List.of(1, 5), tail.weeks());
    }

    @Test
    void seriesAlwaysAddUpToTheRequiredLessonCount() {
        for (int pairs = 1; pairs <= 40; pairs++) {
            TeachingRequirement requirement = Fixtures.seminar(qualification, group, pairs);
            List<Series> series = build(requirement);

            int total = 0;

            for (Series s : series) {
                total += s.lessonCount();
            }

            assertEquals(pairs, total, "pairs=" + pairs);
        }
    }

    @Test
    void indexesFollowPositionInTheList() {
        List<Series> series = build(
                Fixtures.seminar(qualification, group, 12),
                Fixtures.lecture(qualification, 16, group));

        for (int i = 0; i < series.size(); i++) {
            assertEquals(i, series.get(i).index());
        }

        assertTrue(series.size() >= 3);
    }

    @Test
    void sameRequirementObjectTwiceIsRejected() {
        TeachingRequirement requirement = Fixtures.seminar(qualification, group, 8);

        assertThrows(IllegalArgumentException.class,
                () -> build(requirement, requirement));
    }
}
