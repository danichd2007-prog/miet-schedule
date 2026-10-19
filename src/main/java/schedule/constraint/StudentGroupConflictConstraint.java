
package schedule.constraint;

import schedule.model.Lesson;
import schedule.model.Schedule;
import schedule.model.ScheduleEntry;
import schedule.model.StudentGroup;
import schedule.util.TimeSlotUtils;

import java.util.Collections;
import java.util.List;

public class StudentGroupConflictConstraint implements ScheduleConstraint {

    @Override
    public boolean isSatisfied(ScheduleEntry entry, Schedule schedule) {

        List<StudentGroup> newGroups = getLessonGroups(
                entry.getLesson()
        );

        for (ScheduleEntry existingEntry : schedule.getEntries()) {

            if (!TimeSlotUtils.overlaps(
                    existingEntry.getTimeSlot(),
                    entry.getTimeSlot())) {
                continue;
            }

            List<StudentGroup> existingGroups = getLessonGroups(
                    existingEntry.getLesson()
            );

            for (StudentGroup group : newGroups) {
                if (existingGroups.contains(group)) {
                    return false;
                }
            }
        }

        return true;
    }

    private List<StudentGroup> getLessonGroups(Lesson lesson) {

        if (lesson.getStream() != null) {
            return lesson.getStream().getGroups();
        }

        return Collections.singletonList(lesson.getGroup());
    }
}
