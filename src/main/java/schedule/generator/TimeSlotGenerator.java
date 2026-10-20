package schedule.generator;

import schedule.calendar.AcademicCalendar;
import schedule.model.TimeSlot;
import schedule.model.WeekDay;
import schedule.util.BellSchedule;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class TimeSlotGenerator {

    public List<TimeSlot> generateSlots(
            AcademicCalendar calendar,
            List<WeekDay> days,
            int firstLesson,
            int lastLesson) {

        Objects.requireNonNull(
                calendar, "Calendar cannot be null");

        Objects.requireNonNull(
                days, "Days cannot be null");

        if (firstLesson < 1
                || lastLesson < firstLesson
                || lastLesson > 8) {

            throw new IllegalArgumentException(
                    "Invalid lesson range");
        }

        List<TimeSlot> slots = new ArrayList<>();

        for (int week = 1;
             week <= calendar.getWeekCount();
             week++) {

            for (WeekDay day : days) {

                Objects.requireNonNull(
                        day, "Day cannot be null");

                for (int number = firstLesson;
                     number <= lastLesson;
                     number++) {

                    slots.add(BellSchedule.createSlot(
                            week,
                            day,
                            number
                    ));
                }
            }
        }

        return List.copyOf(slots);
    }
}
