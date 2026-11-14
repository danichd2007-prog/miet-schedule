package ru.miet.schedule.engine.tools;

import ru.miet.schedule.engine.model.Problem;
import ru.miet.schedule.engine.solver.ConstructiveSolver;
import ru.miet.schedule.engine.solver.SolveResult;
import ru.miet.schedule.engine.solver.SolverConfig;
import ru.miet.schedule.engine.synthetic.SyntheticUniversity;
import ru.miet.schedule.engine.verify.ScheduleVerifier;

import java.util.List;

/**
 * Замер скорости и полноты решения на искусственных данных.
 * Запуск: аргументы «число групп», «число групп коэффициент_аудиторий» либо без аргументов
 * (30, 100 и 300 групп). Каждое решение проверяется независимым верификатором.
 */
public final class SolverBenchmark {

    private SolverBenchmark() {
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            for (int groups : new int[]{30, 100, 300}) {
                run(groups, 1.0);
            }
        } else {
            run(Integer.parseInt(args[0]), args.length > 1 ? Double.parseDouble(args[1]) : 1.0);
        }
    }

    private static void run(int groups, double roomScale) {
        SyntheticUniversity.Data data = SyntheticUniversity.generate(groups, 1, roomScale);
        SolverConfig config = SolverConfig.defaults();

        long start = System.nanoTime();
        Problem problem = Problem.of(
                data.requirements(), data.rooms(), data.availability(), data.calendar());
        long buildMillis = (System.nanoTime() - start) / 1_000_000L;

        SolveResult result = new ConstructiveSolver(config).solve(problem);

        long verifyStart = System.nanoTime();
        List<String> violations = ScheduleVerifier.verify(
                data.requirements(), data.availability(), data.calendar(),
                result.toScheduledLessons(), config.maxPairsPerDay(),
                result.unplacedRequirements());
        long verifyMillis = (System.nanoTime() - verifyStart) / 1_000_000L;

        System.out.printf(
                "groups=%d rooms=x%.2f: series=%d teachers=%d rooms=%d | build=%d ms, solve=%d ms, "
                        + "verify=%d ms | greedy=%d, repaired=%d, unplaced=%d | violations=%d%n",
                groups, roomScale, problem.seriesCount(), problem.teacherCount(),
                problem.roomCount(), buildMillis, result.elapsedMillis(), verifyMillis,
                result.greedyPlaced(), result.repaired(), result.unplaced().size(),
                violations.size());

        for (int i = 0; i < Math.min(3, result.unplaced().size()); i++) {
            System.out.println("   не размещено: " + result.unplaced().get(i).reason());
        }
    }
}
