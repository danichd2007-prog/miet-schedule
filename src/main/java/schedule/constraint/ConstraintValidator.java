package schedule.constraint;

import schedule.calendar.AcademicCalendar;
import schedule.model.Schedule;
import schedule.model.ScheduleEntry;
import schedule.model.TeacherAvailability;

import java.util.List;
import java.util.Objects;

public class ConstraintValidator {

    private final List<ScheduleConstraint> constraints;

    public ConstraintValidator(
            List<TeacherAvailability> availabilities,
            AcademicCalendar calendar) {

        Objects.requireNonNull(availabilities);
        Objects.requireNonNull(calendar);

        this.constraints = List.of(
                new TeacherConflictConstraint(),
                new ClassroomConflictConstraint(),
                // new StudentGroupConflictConstraint(),
                new ClassroomTypeConstraint()
                // new LessonDurationConstraint()
                // new TeacherAvailabilityConstraint(availabilities, calendar)
        );

    }

    public boolean canAdd(
            ScheduleEntry entry,
            Schedule schedule) {

        Objects.requireNonNull(entry);
        Objects.requireNonNull(schedule);

        for (ScheduleConstraint constraint : constraints) {
            if (!constraint.isSatisfied(entry, schedule)) {
                return false;
            }
        }

        return true;
    }

    public List<ScheduleConstraint> getConstraints() {
        return constraints;
    }
}
