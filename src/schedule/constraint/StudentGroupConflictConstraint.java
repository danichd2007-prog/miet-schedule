package schedule.constraint;

import schedule.model.Schedule;
import schedule.model.ScheduleEntry;

public class StudentGroupConflictConstraint implements ScheduleConstraint {

    @Override
    public boolean isSatisfied(ScheduleEntry entry, Schedule schedule) {

        for (ScheduleEntry existingEntry : schedule.getEntries()) {

            boolean sameGroup =
                    existingEntry.getLesson().getGroup()
                            .equals(entry.getLesson().getGroup());

            boolean sameTime =
                    existingEntry.getTimeSlot()
                            .equals(entry.getTimeSlot());

            if (sameGroup && sameTime) {
                return false;
            }
        }

        return true;
    }
}