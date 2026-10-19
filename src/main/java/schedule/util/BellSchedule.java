package schedule.util;

import schedule.model.TimeSlot;
import schedule.model.WeekDay;
import schedule.model.WeekType;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

public final class BellSchedule {

    public record BellTime(
            LocalTime start,
            LocalTime end
    ) {}

    private static final Map<Integer, BellTime> TIMES = Map.of(
            1, bell(9, 0, 10, 20),
            2, bell(10, 30, 11, 50),
            3, bell(12, 0, 13, 20),
            4, bell(14, 0, 15, 20),
            5, bell(15, 30, 16, 50),
            6, bell(17, 0, 18, 20),
            7, bell(18, 30, 19, 50),
            8, bell(20, 0, 21, 20)
    );

    private BellSchedule() {
    }

    private static BellTime bell(
            int startHour,
            int startMinute,
            int endHour,
            int endMinute) {

        return new BellTime(
                LocalTime.of(startHour, startMinute),
                LocalTime.of(endHour, endMinute)
        );
    }

    public static BellTime getTime(int lessonNumber) {
        BellTime result = TIMES.get(lessonNumber);

        if (result == null) {
            throw new IllegalArgumentException(
                    "Unknown lesson number: " + lessonNumber
            );
        }

        return result;
    }


    public static TimeSlot createSlot(
            WeekDay day,
            int lessonNumber,
            WeekType weekType) {

        BellTime time = getTime(lessonNumber);

        return new TimeSlot(
                day,
                lessonNumber,
                weekType,
                time.start(),
                time.end()
        );
    }


    public static List<TimeSlot> createDaySlots(
            WeekDay day,
            WeekType weekType) {

        return TIMES.keySet().stream()
                .sorted()
                .map(number -> createSlot(
                        day, number, weekType
                ))
                .toList();
    }
}
