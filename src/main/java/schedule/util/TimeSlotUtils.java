package schedule.util;

import schedule.model.TimeSlot;
import schedule.model.WeekType;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public final class TimeSlotUtils {

    private TimeSlotUtils() {
    }

    public static boolean overlaps(TimeSlot first, TimeSlot second) {
        Objects.requireNonNull(first, "First time slot cannot be null");
        Objects.requireNonNull(second, "Second time slot cannot be null");

        if (first.getDay() != second.getDay()) {
            return false;
        }

        boolean timeOverlap =
                first.getStartTime().isBefore(second.getEndTime())
                        && second.getStartTime().isBefore(first.getEndTime());

        return timeOverlap &&
                weeksOverlap(first.getWeekType(), second.getWeekType());
    }

    public static boolean weeksOverlap(WeekType first, WeekType second) {
        Objects.requireNonNull(first, "First week type cannot be null");
        Objects.requireNonNull(second, "Second week type cannot be null");

        return !Collections.disjoint(
                getCoveredWeeks(first),
                getCoveredWeeks(second)
        );
    }

    public static boolean weeksCover(WeekType available, WeekType required) {
        Objects.requireNonNull(available, "Available week type cannot be null");
        Objects.requireNonNull(required, "Required week type cannot be null");

        return getCoveredWeeks(available)
                .containsAll(getCoveredWeeks(required));
    }

    public static Set<WeekType> getCoveredWeeks(WeekType type) {
        Objects.requireNonNull(type, "Week type cannot be null");

        return switch (type) {
            case F_NUMERATOR ->
                    EnumSet.of(WeekType.F_NUMERATOR);

            case F_DENOMINATOR ->
                    EnumSet.of(WeekType.F_DENOMINATOR);

            case S_NUMERATOR ->
                    EnumSet.of(WeekType.S_NUMERATOR);

            case S_DENOMINATOR ->
                    EnumSet.of(WeekType.S_DENOMINATOR);

            case NUMERATOR ->
                    EnumSet.of(
                            WeekType.F_NUMERATOR,
                            WeekType.S_NUMERATOR
                    );

            case DENOMINATOR ->
                    EnumSet.of(
                            WeekType.F_DENOMINATOR,
                            WeekType.S_DENOMINATOR
                    );

            case EVERY_WEEK ->
                    EnumSet.of(
                            WeekType.F_NUMERATOR,
                            WeekType.F_DENOMINATOR,
                            WeekType.S_NUMERATOR,
                            WeekType.S_DENOMINATOR
                    );
        };
    }
}
