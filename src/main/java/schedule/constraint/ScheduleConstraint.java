package schedule.constraint;

import schedule.model.Schedule;
import schedule.model.ScheduleEntry;

public interface ScheduleConstraint {

    boolean isSatisfied(ScheduleEntry entry, Schedule schedule);
}