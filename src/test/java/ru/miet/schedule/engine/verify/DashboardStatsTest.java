package ru.miet.schedule.engine.verify;

import org.junit.jupiter.api.Test;
import ru.miet.schedule.engine.model.Problem;
import ru.miet.schedule.engine.model.ScheduledLesson;
import ru.miet.schedule.engine.solver.ConstructiveSolver;
import ru.miet.schedule.engine.solver.SolverConfig;
import ru.miet.schedule.engine.synthetic.SyntheticUniversity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardStatsTest {

    @Test
    void totalsMatchTheLessonsAndLoadsAreSorted() {
        var data = SyntheticUniversity.generate(6, 1, 1.0);
        List<ScheduledLesson> lessons = new ConstructiveSolver(SolverConfig.defaults())
                .solve(Problem.of(data.requirements(), data.rooms(), data.availability(), data.calendar()))
                .toScheduledLessons();

        DashboardStats stats = DashboardStats.of(lessons, data.rooms());

        double slots = lessons.stream().filter(l -> l.week() <= 4).mapToInt(ScheduledLesson::duration).sum() / 4.0;
        double byDay = 0;
        double byPair = 0;

        for (double v : stats.perDay()) {
            byDay += v;
        }

        for (double v : stats.perPair()) {
            byPair += v;
        }

        assertEquals(slots, byDay, 1e-9);
        assertEquals(slots, byPair, 1e-9);
        assertEquals(data.rooms().size(), stats.rooms().size());

        for (var loads : List.of(stats.rooms(), stats.teachers(), stats.groups())) {
            for (int i = 1; i < loads.size(); i++) {
                assertTrue(loads.get(i - 1).perWeek() >= loads.get(i).perWeek());
            }

            assertTrue(loads.stream().allMatch(l -> l.share() >= 0 && l.share() <= 1.0 + 1e-9));
        }
    }
}
