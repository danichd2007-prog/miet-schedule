package schedule.constraint;

import schedule.model.Schedule;
import schedule.model.ScheduleEntry;
import schedule.model.TeacherAvailability;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ConstraintValidator {

    private final List<ScheduleConstraint> constraints;

    public ConstraintValidator(
            List<TeacherAvailability> availabilities) {

        this.constraints = new ArrayList<>();

        constraints.add(new TeacherConflictConstraint());
        constraints.add(new ClassroomConflictConstraint());
        constraints.add(new StudentGroupConflictConstraint());
        constraints.add(new ClassroomTypeConstraint());
        constraints.add(new LessonWeekConstraint());
        constraints.add(new TeacherAvailabilityConstraint(availabilities));
    }

    public boolean canAdd(ScheduleEntry entry, Schedule schedule) {
        Objects.requireNonNull(entry, "Entry cannot be null");
        Objects.requireNonNull(schedule, "Schedule cannot be null");

        for (ScheduleConstraint constraint : constraints) {
            if (!constraint.isSatisfied(entry, schedule)) {
                return false;
            }
        }

        return true;
    }

    public List<ScheduleConstraint> getConstraints() {
        return Collections.unmodifiableList(constraints);
    }
}
