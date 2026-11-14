package ru.miet.schedule.engine.solver;

/**
 * Настройки решателя. Веса мягких критериев задают приоритеты:
 * студенты важнее преподавателей, переходы между корпусами пока не учитываются.
 *
 * @param maxPairsPerDay        жёсткий предел пар в день у группы
 * @param studentWindow         штраф за каждое «окно» (пустую пару между занятиями) у группы
 * @param teacherWindow         штраф за окно у преподавателя
 * @param latePair              штраф за каждую пару после четвёртой (для студентов)
 * @param saturday              штраф за занятие в субботу
 * @param roomSlack             штраф за лишние места в аудитории (чтобы не занимать большие залы)
 * @param dayLoad               штраф за каждую пару сверх четырёх в день у группы
 * @param repairRounds          сколько раз пытаться вытеснением разместить оставшиеся серии
 * @param maxEjectionsPerSeries сколько раз одну и ту же серию можно вытеснять
 * @param timeLimitMillis       общий лимит времени на решение
 */
public record SolverConfig(
        int maxPairsPerDay,
        double studentWindow,
        double teacherWindow,
        double latePair,
        double saturday,
        double roomSlack,
        double dayLoad,
        int repairRounds,
        int maxEjectionsPerSeries,
        long timeLimitMillis) {

    public SolverConfig {
        if (maxPairsPerDay < 1 || maxPairsPerDay > 8) {
            throw new IllegalArgumentException("maxPairsPerDay must be 1..8");
        }

        if (repairRounds < 0 || maxEjectionsPerSeries < 0) {
            throw new IllegalArgumentException("Repair limits cannot be negative");
        }

        if (timeLimitMillis <= 0) {
            throw new IllegalArgumentException("Time limit must be positive");
        }

        if (studentWindow < 0 || teacherWindow < 0 || latePair < 0
                || saturday < 0 || roomSlack < 0 || dayLoad < 0) {
            throw new IllegalArgumentException("Weights cannot be negative");
        }
    }

    public static SolverConfig defaults() {
        return new SolverConfig(6, 10, 5, 2, 15, 3, 4, 3, 3, 60_000);
    }
}
