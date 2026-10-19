package schedule.generator;

import schedule.model.TimeSlot;
import schedule.model.WeekDay;
import schedule.model.WeekType;
import schedule.util.BellSchedule;

import java.util.ArrayList;
import java.util.List;

public class TimeSlotGenerator {

    public List<TimeSlot> generateSlots(
            List<WeekDay> days,
            List<WeekType> weekTypes,
            int firstLesson,
            int lastLesson) {

        if (firstLesson < 1 || lastLesson < firstLesson
                || lastLesson > 8) {
            throw new IllegalArgumentException(
                    "Invalid lesson range");
        }

        List<TimeSlot> slots = new ArrayList<>();

        for (WeekDay day : days) {
            for (WeekType weekType : weekTypes) {
                for (int number = firstLesson;
                     number <= lastLesson;
                     number++) {

                    slots.add(BellSchedule.createSlot(
                            day,
                            number,
                            weekType
                    ));
                }
            }
        }

        return slots;
    }
}
