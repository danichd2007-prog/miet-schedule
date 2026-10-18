package schedule.constraint;

import schedule.model.Schedule;
import schedule.model.ScheduleEntry;

public class ClassroomConflictConstraint implements ScheduleConstraint {

    @Override
    public boolean isSatisfied(ScheduleEntry entry, Schedule schedule) {

        for (ScheduleEntry existingEntry : schedule.getEntries()) {

            boolean sameClassroom =
                    existingEntry.getClassroom().equals(entry.getClassroom());

            boolean sameTime =
                    existingEntry.getTimeSlot().equals(entry.getTimeSlot());

            if (sameClassroom && sameTime) {
                return false;
            }
        }

        return true;
    }
}