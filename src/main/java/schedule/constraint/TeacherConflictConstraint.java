package schedule.constraint;

import schedule.model.Schedule;
import schedule.model.ScheduleEntry;

public class TeacherConflictConstraint implements ScheduleConstraint {

    @Override
    public boolean isSatisfied(ScheduleEntry entry, Schedule schedule) {

        for (ScheduleEntry existingEntry : schedule.getEntries()) {

            boolean sameTeacher =
                    existingEntry.getLesson().getTeacher()
                            .equals(entry.getLesson().getTeacher());

            boolean sameTime =
                    existingEntry.getTimeSlot()
                            .equals(entry.getTimeSlot());

            if (sameTeacher && sameTime) {
                return false;
            }
        }

        return true;
    }
}