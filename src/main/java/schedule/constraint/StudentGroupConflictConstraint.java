package schedule.constraint;

import schedule.model.Lesson;
import schedule.model.Schedule;
import schedule.model.ScheduleEntry;
import schedule.model.StudentGroup;
import schedule.util.TimeSlotUtils;

import java.util.List;

public class StudentGroupConflictConstraint implements ScheduleConstraint {

    @Override
    public boolean isSatisfied(
            ScheduleEntry entry,
            Schedule schedule) {

        List<StudentGroup> newGroups =
                getGroups(entry.getLesson());

        for (ScheduleEntry existing : schedule.getEntries()) {

            if (!TimeSlotUtils.overlaps(existing, entry)) {
                continue;
            }

            List<StudentGroup> existingGroups =
                    getGroups(existing.getLesson());

            for (StudentGroup group : newGroups) {
                if (existingGroups.contains(group)) {
                    return false;
                }
            }
        }

        return true;
    }

    private List<StudentGroup> getGroups(Lesson lesson) {
        if (lesson.getStream() != null) {
            return lesson.getStream().getGroups();
        }

        return List.of(lesson.getGroup());
    }
}
