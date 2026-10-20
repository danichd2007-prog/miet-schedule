package schedule.constraint;

import schedule.model.Schedule;
import schedule.model.ScheduleEntry;
import schedule.util.TimeSlotUtils;

public class ClassroomConflictConstraint implements ScheduleConstraint {

    @Override
    public boolean isSatisfied(
            ScheduleEntry entry,
            Schedule schedule) {

        for (ScheduleEntry existing : schedule.getEntries()) {

            if (existing.getClassroom().equals(entry.getClassroom())
                    && TimeSlotUtils.overlaps(existing, entry)) {
                return false;
            }
        }

        return true;
    }
}
