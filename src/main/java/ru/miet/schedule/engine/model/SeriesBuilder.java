package ru.miet.schedule.engine.model;

import ru.miet.schedule.domain.AcademicCalendar;
import ru.miet.schedule.domain.TeachingRequirement;
import ru.miet.schedule.domain.WeekType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Превращает учебную нагрузку за семестр в серии типовой сетки.
 *
 * Занятия делятся жадно: берётся самый длинный шаблон повторения, который
 * ещё помещается в остаток (каждую неделю, числитель или знаменатель,
 * одна фаза цикла). Так 16 занятий за 16 недель дают одну серию «каждую
 * неделю», 8 занятий дают «числитель или знаменатель», а 4 занятия
 * дают одну фазу цикла (раз в четыре недели).
 * Остаток, который не набирает ни один шаблон, идёт в «хвостовую» серию
 * на первых неделях одной фазы цикла.
 */
public final class SeriesBuilder {

    private SeriesBuilder() {
    }

    public static List<Series> build(
            List<TeachingRequirement> requirements,
            AcademicCalendar calendar) {

        Objects.requireNonNull(requirements, "requirements");
        Objects.requireNonNull(calendar, "calendar");

        Map<WeekType, List<Integer>> weeks = new EnumMap<>(WeekType.class);

        for (WeekType type : WeekType.values()) {
            weeks.put(type, calendar.weeksOf(type));
        }

        List<Series> result = new ArrayList<>();
        Set<TeachingRequirement> seen =
                Collections.newSetFromMap(new IdentityHashMap<>());

        for (TeachingRequirement requirement : requirements) {
            Objects.requireNonNull(requirement, "requirement");

            if (!seen.add(requirement)) {
                throw new IllegalArgumentException(
                        "Repeated TeachingRequirement object: " + requirement);
            }

            if (requirement.groups().isEmpty()) {
                throw new IllegalArgumentException(
                        "Lecture stream cannot be empty: " + requirement);
            }

            int remaining = requirement.lessonCount();

            while (remaining > 0) {
                int best = 0;

                for (WeekType type : WeekType.values()) {
                    int count = weeks.get(type).size();

                    if (count > best && count <= remaining) {
                        best = count;
                    }
                }

                List<Series.Alternative> alternatives = new ArrayList<>();

                if (best > 0) {
                    for (WeekType type : WeekType.values()) {
                        if (weeks.get(type).size() == best) {
                            alternatives.add(new Series.Alternative(
                                    type.phaseMask(), weeks.get(type)));
                        }
                    }

                    remaining -= best;
                } else {
                    for (int phase = 0; phase < WeekType.PHASES; phase++) {
                        List<Integer> phaseWeeks = weeks.get(WeekType.ofPhase(phase));

                        if (phaseWeeks.size() >= remaining) {
                            alternatives.add(new Series.Alternative(
                                    1 << phase,
                                    phaseWeeks.subList(0, remaining)));
                        }
                    }

                    remaining = 0;
                }

                if (alternatives.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Semester is too short for: " + requirement);
                }

                result.add(new Series(result.size(), requirement, alternatives));
            }
        }

        return result;
    }
}
