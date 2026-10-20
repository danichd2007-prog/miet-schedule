package schedule.constraint;

import schedule.model.Schedule;
import schedule.model.ScheduleEntry;

public class LessonDurationConstraint implements ScheduleConstraint {

    @Override
    public boolean isSatisfied(
            ScheduleEntry entry,
            Schedule schedule) {

        int start = entry.getTimeSlot().getLessonNumber();
        int duration = entry.getLesson().getDuration();

        return start >= 1 && start + duration - 1 <= 8;
    }
}
