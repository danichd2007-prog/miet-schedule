
package schedule.constraint;

import schedule.calendar.AcademicCalendar;
import schedule.model.Schedule;
import schedule.model.ScheduleEntry;
import schedule.model.Teacher;
import schedule.model.TeacherAvailability;
import schedule.model.TimeSlot;
import schedule.model.WeekType;
import schedule.util.TimeSlotUtils;

import java.util.List;
import java.util.Objects;

public class TeacherAvailabilityConstraint implements ScheduleConstraint {

    private final List<TeacherAvailability> availabilities;
    private final AcademicCalendar calendar;

    public TeacherAvailabilityConstraint(
            List<TeacherAvailability> availabilities,
            AcademicCalendar calendar) {

        this.availabilities = List.copyOf(
                Objects.requireNonNull(availabilities));

        this.calendar = Objects.requireNonNull(calendar);
    }

    @Override
    public boolean isSatisfied(
            ScheduleEntry entry,
            Schedule schedule) {

        Teacher teacher = entry.getLesson().getTeacher();
        TimeSlot slot = entry.getTimeSlot();

        WeekType requiredWeek =
                calendar.getWeekType(slot.getWeekNumber());

        int firstPair = slot.getLessonNumber();
        int lastPair =
                firstPair + entry.getLesson().getDuration() - 1;

        for (int pair = firstPair; pair <= lastPair; pair++) {

            boolean available = false;

            for (TeacherAvailability availability : availabilities) {

                if (!availability.getTeacher().equals(teacher)) {
                    continue;
                }

                if (availability.getDay() != slot.getDay()) {
                    continue;
                }

                if (pair < availability.getFromLesson()
                        || pair > availability.getToLesson()) {
                    continue;
                }

                if (!TimeSlotUtils.weeksCover(
                        availability.getWeekType(),
                        requiredWeek)) {
                    continue;
                }

                available = true;
                break;
            }

            if (!available) {
                return false;
            }
        }

        return true;
    }
}
