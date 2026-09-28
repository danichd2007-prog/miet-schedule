package schedule.constraint;

import schedule.model.Schedule;
import schedule.model.ScheduleEntry;
import schedule.model.Teacher;
import schedule.model.TimeSlot;

public class TeacherConflictConstraint implements ScheduleConstraint {

    @Override
    public boolean isSatisfied(Schedule schedule, ScheduleEntry entry) {
        Teacher newTeacher = entry.getLesson().getTeacher();
        TimeSlot newTimeSlot = entry.getTimeSlot();

        for (ScheduleEntry existingEntry : schedule.getEntries()) {
            Teacher existingTeacher = existingEntry.getLesson().getTeacher();
            TimeSlot existingTimeSlot = existingEntry.getTimeSlot();

            if (existingTeacher.equals(newTeacher) && existingTimeSlot.equals(newTimeSlot)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public String getErrorMessage() {
        return "Преподаватель уже занят в этот временной слот";
    }
}