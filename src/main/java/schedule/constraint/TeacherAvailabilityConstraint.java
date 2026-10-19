package schedule.constraint;

import schedule.model.Schedule;
import schedule.model.ScheduleEntry;
import schedule.model.Teacher;
import schedule.model.TeacherAvailability;
import schedule.model.TimeSlot;
import schedule.model.WeekType;
import schedule.util.TimeSlotUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class TeacherAvailabilityConstraint implements ScheduleConstraint {

    private final List<TeacherAvailability> availabilities;

    public TeacherAvailabilityConstraint(
            List<TeacherAvailability> availabilities) {

        Objects.requireNonNull(
                availabilities, "Availabilities cannot be null");

        this.availabilities = new ArrayList<>(availabilities);
    }

    @Override
    public boolean isSatisfied(ScheduleEntry entry, Schedule schedule) {

        Teacher teacher = entry.getLesson().getTeacher();
        TimeSlot slot = entry.getTimeSlot();

        Set<WeekType> requiredWeeks =
                TimeSlotUtils.getCoveredWeeks(slot.getWeekType());

        Set<WeekType> coveredWeeks =
                java.util.EnumSet.noneOf(WeekType.class);

        for (TeacherAvailability availability : availabilities) {

            if (!availability.getTeacher().equals(teacher)) {
                continue;
            }

            if (availability.getDay() != slot.getDay()) {
                continue;
            }

            if (slot.getLessonNumber() < availability.getFromLesson()
                    || slot.getLessonNumber() > availability.getToLesson()) {
                continue;
            }

            coveredWeeks.addAll(
                    TimeSlotUtils.getCoveredWeeks(
                            availability.getWeekType()
                    )
            );
        }

        return coveredWeeks.containsAll(requiredWeeks);
    }
}
