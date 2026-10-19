package schedule.constraint;

import schedule.model.Schedule;
import schedule.model.ScheduleEntry;

public class LessonWeekConstraint implements ScheduleConstraint {

    @Override
    public boolean isSatisfied(ScheduleEntry entry, Schedule schedule) {

        return entry.getLesson().getWeekType()
                == entry.getTimeSlot().getWeekType();
    }
}
