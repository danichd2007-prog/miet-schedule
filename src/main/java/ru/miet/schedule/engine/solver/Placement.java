package ru.miet.schedule.engine.solver;

/**
 * Решение для одной серии: номер варианта повторения, день (0..5), первая пара (1..8) и аудитория.
 */
public record Placement(int series, int alternative, int day, int pair, int room) {
}
