
package schedule.constraint;

import schedule.model.*;

import java.util.List;

public class SameSubjectDayConstraint implements ScheduleConstraint {

    @Override
    public boolean isSatisfied(
            ScheduleEntry entry,
            Schedule schedule) {

        Lesson newLesson = entry.getLesson();
        TimeSlot newSlot = entry.getTimeSlot();

        if (newLesson.isDoubleBlock()) {
            return true;
        }

        for (ScheduleEntry existing : schedule.getEntries()) {

            Lesson oldLesson = existing.getLesson();
            TimeSlot oldSlot = existing.getTimeSlot();

            if (oldSlot.getWeekNumber()
                    != newSlot.getWeekNumber()) {
                continue;
            }

            if (oldSlot.getDay() != newSlot.getDay()) {
                continue;
            }

            if (oldLesson.isDoubleBlock()) {
                continue;
            }

            if (!oldLesson.getSubject()
                    .equals(newLesson.getSubject())) {
                continue;
            }

            if (oldLesson.getLessonType()
                    != newLesson.getLessonType()) {
                continue;
            }

            if (sharesGroup(newLesson, oldLesson)) {
                return false;
            }
        }

        return true;
    }

    private boolean sharesGroup(
            Lesson first,
            Lesson second) {

        List<StudentGroup> firstGroups = getGroups(first);
        List<StudentGroup> secondGroups = getGroups(second);

        for (StudentGroup group : firstGroups) {
            if (secondGroups.contains(group)) {
                return true;
            }
        }

        return false;
    }

    private List<StudentGroup> getGroups(Lesson lesson) {

        if (lesson.getStream() != null) {
            return lesson.getStream().getGroups();
        }

        return List.of(lesson.getGroup());
    }
}
