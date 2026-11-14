package ru.miet.schedule.engine.solver;

import ru.miet.schedule.domain.TeachingRequirement;
import ru.miet.schedule.engine.model.Problem;
import ru.miet.schedule.engine.model.ScheduledLesson;
import ru.miet.schedule.engine.model.Series;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Результат решения. Всегда содержит всё, что удалось разместить, и список того,
 * что не поместилось, с объяснением причины.
 */
public final class SolveResult {

    /** Неразмещённая серия и причина. */
    public record Unplaced(Series series, String reason) {
    }

    private final Problem problem;
    private final List<Placement> placements;
    private final List<Unplaced> unplaced;
    private final long elapsedMillis;
    private final int greedyPlaced;
    private final int repaired;

    SolveResult(
            Problem problem,
            List<Placement> placements,
            List<Unplaced> unplaced,
            long elapsedMillis,
            int greedyPlaced,
            int repaired) {

        this.problem = problem;
        this.placements = List.copyOf(placements);
        this.unplaced = List.copyOf(unplaced);
        this.elapsedMillis = elapsedMillis;
        this.greedyPlaced = greedyPlaced;
        this.repaired = repaired;
    }

    public Problem problem() {
        return problem;
    }

    public List<Placement> placements() {
        return placements;
    }

    public List<Unplaced> unplaced() {
        return unplaced;
    }

    public boolean isComplete() {
        return unplaced.isEmpty();
    }

    public long elapsedMillis() {
        return elapsedMillis;
    }

    /** Сколько серий разместил первый (жадный) проход. */
    public int greedyPlaced() {
        return greedyPlaced;
    }

    /** Сколько серий удалось разместить вытеснением после жадного прохода. */
    public int repaired() {
        return repaired;
    }

    /** Нагрузка, у которой размещены не все занятия: верификатору разрешено недосчитаться их. */
    public Set<TeachingRequirement> unplacedRequirements() {
        Set<TeachingRequirement> result =
                Collections.newSetFromMap(new IdentityHashMap<>());

        for (Unplaced u : unplaced) {
            result.add(u.series().requirement());
        }

        return result;
    }

    /** Разворачивает серии в конкретные занятия по неделям семестра. */
    public List<ScheduledLesson> toScheduledLessons() {
        List<ScheduledLesson> lessons = new ArrayList<>();

        for (Placement p : placements) {
            Series series = problem.series(p.series());
            Series.Alternative alternative = series.alternatives().get(p.alternative());

            for (int week : alternative.weeks()) {
                lessons.add(new ScheduledLesson(
                        series.requirement(),
                        week,
                        problem.dayOf(p.day()),
                        p.pair(),
                        series.duration(),
                        problem.room(p.room())));
            }
        }

        return lessons;
    }
}
