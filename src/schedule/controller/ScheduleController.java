package schedule.controller;

import schedule.constraint.ScheduleConstraint;
import schedule.model.Schedule;
import schedule.model.ScheduleEntry;

public class ScheduleController {

    private Schedule schedule;
    private ScheduleConstraint[] constraints;

    public ScheduleController(Schedule schedule, ScheduleConstraint[] constraints) {
        this.schedule = schedule;
        this.constraints = constraints;
    }

    public boolean addEntry(ScheduleEntry entry) {

        for (ScheduleConstraint constraint : constraints) {
            if (!constraint.isSatisfied(entry, schedule)) {
                return false;
            }
        }

        schedule.addEntry(entry);
        return true;
    }

    public Schedule getSchedule() {
        return schedule;
    }
}