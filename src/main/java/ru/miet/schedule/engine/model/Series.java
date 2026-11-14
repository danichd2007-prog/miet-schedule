package ru.miet.schedule.engine.model;

import ru.miet.schedule.domain.TeachingRequirement;

import java.util.List;
import java.util.Objects;

/**
 * Серия: неделимая для решателя часть нагрузки. Все занятия серии стоят
 * в один и тот же день и на одну и ту же пару, повторяясь по неделям.
 *
 * У серии может быть несколько равноценных вариантов повторения
 * (например, «по числителю» или «по знаменателю»). Решатель выбирает один.
 */
public final class Series {

    /**
     * Вариант повторения: маска фаз цикла и номера недель, в которые идут занятия.
     * Если недель меньше, чем во всех неделях этих фаз («хвост» нагрузки),
     * индекс занятости блокирует фазы целиком. Это консервативно: расписание
     * остаётся корректным, а лишних конфликтов не пропускается.
     */
    public record Alternative(int phaseMask, List<Integer> weeks) {

        public Alternative {
            TimeGrid.requirePhaseMask(phaseMask);
            Objects.requireNonNull(weeks, "weeks");

            if (weeks.isEmpty()) {
                throw new IllegalArgumentException("Alternative must have weeks");
            }

            weeks = List.copyOf(weeks);
        }
    }

    private final int index;
    private final TeachingRequirement requirement;
    private final List<Alternative> alternatives;

    public Series(
            int index,
            TeachingRequirement requirement,
            List<Alternative> alternatives) {

        if (index < 0) {
            throw new IllegalArgumentException("Index cannot be negative");
        }

        this.index = index;
        this.requirement = Objects.requireNonNull(requirement, "requirement");
        Objects.requireNonNull(alternatives, "alternatives");

        if (alternatives.isEmpty()) {
            throw new IllegalArgumentException("Series needs an alternative");
        }

        int size = alternatives.get(0).weeks().size();

        for (Alternative alternative : alternatives) {
            if (alternative.weeks().size() != size) {
                throw new IllegalArgumentException(
                        "All alternatives must have the same lesson count");
            }
        }

        this.alternatives = List.copyOf(alternatives);
    }

    public int index() {
        return index;
    }

    public TeachingRequirement requirement() {
        return requirement;
    }

    public List<Alternative> alternatives() {
        return alternatives;
    }

    public int lessonCount() {
        return alternatives.get(0).weeks().size();
    }

    public int duration() {
        return requirement.lessonDuration();
    }

    @Override
    public String toString() {
        return "Серия #" + index + " (" + lessonCount() + " зан.): " + requirement;
    }
}
