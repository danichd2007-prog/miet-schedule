package schedule.constraint;

import schedule.model.Schedule;
import schedule.model.ScheduleEntry;

public class ClassroomTypeConstraint implements ScheduleConstraint {

    @Override
    public boolean isSatisfied(ScheduleEntry entry, Schedule schedule) {
        return entry.getLesson().getRequiredClassroomType()
                == entry.getClassroom().getType();
    }
}