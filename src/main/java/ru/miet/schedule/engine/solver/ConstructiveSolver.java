package ru.miet.schedule.engine.solver;

import ru.miet.schedule.engine.model.Problem;
import ru.miet.schedule.engine.model.Series;
import ru.miet.schedule.engine.model.TimeGrid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Конструктивный решатель: расставляет серии по очереди (самые ограниченные первыми),
 * выбирая самое удобное допустимое место, а оставшиеся пытается вставить вытеснением.
 *
 * Порядок размещения:
 * 1. Совместители раньше штатных (они сами указывают дни работы).
 * 2. Внутри категории серии с меньшим числом вариантов времени раньше.
 * 3. При равенстве блоки раньше одиночных пар, потоки раньше одной группы.
 *
 * Вытеснение: если серию нельзя поставить, ищется время, где ей мешает ровно одна
 * другая серия. Мешающая убирается, новая ставится, а убранная пересаживается.
 * Если пересадить не получилось, всё возвращается как было.
 *
 * Решатель детерминирован: один и тот же вход даёт один и тот же результат.
 * Он не бросает исключений при невозможности расписания: возвращает лучшее найденное
 * и список неразмещённого с причинами.
 */
public final class ConstructiveSolver {

    private final SolverConfig config;

    public ConstructiveSolver() {
        this(SolverConfig.defaults());
    }

    public ConstructiveSolver(SolverConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public SolveResult solve(Problem problem) {
        Objects.requireNonNull(problem, "problem");

        long start = System.nanoTime();
        long deadline = start + config.timeLimitMillis() * 1_000_000L;

        Board board = new Board(problem, config);
        List<Integer> unplaced = new ArrayList<>();

        for (int s : order(problem)) {
            List<Board.Candidate> candidates = board.candidates(s);

            if (candidates.isEmpty()) {
                unplaced.add(s);
            } else {
                board.place(candidates.get(0).placement());
            }
        }

        int greedyPlaced = problem.seriesCount() - unplaced.size();
        int repaired = 0;
        int[] ejections = new int[problem.seriesCount()];

        for (int round = 0; round < config.repairRounds() && !unplaced.isEmpty(); round++) {
            boolean progress = false;

            for (Integer s : new ArrayList<>(unplaced)) {
                if (System.nanoTime() > deadline) {
                    break;
                }

                if (tryEject(problem, board, s, ejections)) {
                    unplaced.remove(s);
                    repaired++;
                    progress = true;
                }
            }

            if (!progress || System.nanoTime() > deadline) {
                break;
            }
        }

        List<Placement> placements = new ArrayList<>();

        for (int s = 0; s < problem.seriesCount(); s++) {
            if (board.isPlaced(s)) {
                placements.add(board.placement(s));
            }
        }

        List<SolveResult.Unplaced> unplacedResult = new ArrayList<>();

        for (int s : unplaced) {
            unplacedResult.add(new SolveResult.Unplaced(
                    problem.series(s), board.diagnose(s)));
        }

        long elapsed = (System.nanoTime() - start) / 1_000_000L;

        return new SolveResult(
                problem, placements, unplacedResult, elapsed, greedyPlaced, repaired);
    }

    // ------------------------------------------------------------------

    private int[] order(Problem problem) {
        int n = problem.seriesCount();
        int[] freedom = new int[n];

        for (int s = 0; s < n; s++) {
            Series series = problem.series(s);
            int duration = series.duration();
            int starts = 0;

            for (int alt = 0; alt < series.alternatives().size(); alt++) {
                int mask = series.alternatives().get(alt).phaseMask();

                for (int day = 0; day < TimeGrid.DAYS; day++) {
                    for (int pair = 1; pair + duration - 1 <= TimeGrid.PAIRS; pair++) {
                        if (problem.isTeacherAllowed(
                                problem.teacherOf(s), mask, day, pair, duration)) {
                            starts++;
                        }
                    }
                }
            }

            freedom[s] = starts * Math.max(1, problem.roomsOf(s).length);
        }

        Integer[] boxed = new Integer[n];

        for (int i = 0; i < n; i++) {
            boxed[i] = i;
        }

        Arrays.sort(boxed, Comparator
                .comparingInt((Integer s) ->
                        problem.series(s).requirement().teacher().getCategory().priority())
                .thenComparingInt(s -> freedom[s])
                .thenComparingInt(s -> -problem.series(s).duration())
                .thenComparingInt(s -> -problem.groupsOf(s).length)
                .thenComparingInt(s -> s));

        int[] result = new int[n];

        for (int i = 0; i < n; i++) {
            result[i] = boxed[i];
        }

        return result;
    }

    /** Пытается поставить серию u, вытеснив ровно одну мешающую. */
    private boolean tryEject(
            Problem problem,
            Board board,
            int u,
            int[] ejections) {

        Series series = problem.series(u);
        int duration = series.duration();
        Board.IntSet blockers = new Board.IntSet();
        Board.IntSet roomBlockers = new Board.IntSet();

        for (int alt = 0; alt < series.alternatives().size(); alt++) {
            for (int day = 0; day < TimeGrid.DAYS; day++) {
                for (int pair = 1; pair + duration - 1 <= TimeGrid.PAIRS; pair++) {
                    if (!board.allowedByRules(u, alt, day, pair)) {
                        continue;
                    }

                    blockers.clear();
                    board.peopleBlockers(u, alt, day, pair, blockers);

                    if (blockers.size() > 1) {
                        continue;
                    }

                    int room = -1;
                    int victim = blockers.size() == 1 ? blockers.get(0) : -1;

                    for (int candidate : problem.roomsOf(u)) {
                        roomBlockers.clear();
                        board.roomBlockers(u, alt, day, pair, candidate, roomBlockers);

                        if (victim >= 0) {
                            if (roomBlockers.size() == 0
                                    || (roomBlockers.size() == 1
                                    && roomBlockers.get(0) == victim)) {
                                room = candidate;
                                break;
                            }
                        } else if (roomBlockers.size() == 1) {
                            room = candidate;
                            victim = roomBlockers.get(0);
                            break;
                        }
                    }

                    if (room < 0 || victim < 0 || victim == u) {
                        continue;
                    }

                    if (ejections[victim] >= config.maxEjectionsPerSeries()) {
                        continue;
                    }

                    ejections[victim]++;

                    Placement old = board.placement(victim);
                    Placement fresh = new Placement(u, alt, day, pair, room);

                    board.remove(victim);

                    if (board.isFeasible(fresh)) {
                        board.place(fresh);

                        List<Board.Candidate> moved = board.candidates(victim);

                        if (!moved.isEmpty()) {
                            board.place(moved.get(0).placement());
                            return true;
                        }

                        board.remove(u);
                    }

                    board.place(old);
                }
            }
        }

        return false;
    }
}
