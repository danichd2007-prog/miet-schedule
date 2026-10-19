package schedule.generator;

import schedule.constraint.ConstraintValidator;
import schedule.model.Classroom;
import schedule.model.Lesson;
import schedule.model.Schedule;
import schedule.model.ScheduleEntry;
import schedule.model.TimeSlot;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class ScheduleGenerator {

    private final ConstraintValidator validator;

    public ScheduleGenerator(ConstraintValidator validator) {
        this.validator = Objects.requireNonNull(
                validator, "Validator cannot be null");
    }

    public Optional<Schedule> generate(
            List<Lesson> lessons,
            List<TimeSlot> timeSlots,
            List<Classroom> classrooms) {

        Objects.requireNonNull(lessons, "Lessons cannot be null");
        Objects.requireNonNull(timeSlots, "TimeSlots cannot be null");
        Objects.requireNonNull(classrooms, "Classrooms cannot be null");

        List<Lesson> lessonList = List.copyOf(lessons);
        List<TimeSlot> slotList = List.copyOf(timeSlots);
        List<Classroom> roomList = List.copyOf(classrooms);

        validateLessons(lessonList);

        Schedule schedule = new Schedule(
                "Сгенерированное расписание",
                "Расписание, составленное алгоритмом backtracking"
        );

        boolean success = backtrack(
                0,
                lessonList,
                slotList,
                roomList,
                schedule
        );

        if (success) {
            return Optional.of(schedule);
        }

        return Optional.empty();
    }

    private boolean backtrack(
            int index,
            List<Lesson> lessons,
            List<TimeSlot> timeSlots,
            List<Classroom> classrooms,
            Schedule schedule) {

        if (index == lessons.size()) {
            return true;
        }

        Lesson lesson = lessons.get(index);

        for (TimeSlot slot : timeSlots) {
            if (slot.getWeekType() != lesson.getWeekType()) {
                continue;
            }

            for (Classroom classroom : classrooms) {

                if (classroom.getType()
                        != lesson.getRequiredClassroomType()) {
                    continue;
                }

                ScheduleEntry entry = new ScheduleEntry(
                        lesson,
                        slot,
                        classroom
                );

                if (!validator.canAdd(entry, schedule)) {
                    continue;
                }

                schedule.addEntry(entry);

                if (backtrack(
                        index + 1,
                        lessons,
                        timeSlots,
                        classrooms,
                        schedule)) {
                    return true;
                }

                schedule.removeEntry(entry);
            }
        }

        return false;
    }

    private void validateLessons(List<Lesson> lessons) {

        Set<Lesson> seen = Collections.newSetFromMap(
                new IdentityHashMap<>()
        );

        for (Lesson lesson : lessons) {

            if (!seen.add(lesson)) {
                throw new IllegalArgumentException(
                        "Same Lesson instance appears more than once");
            }

            if (lesson.getStream() != null
                    && lesson.getStream().getGroups().isEmpty()) {
                throw new IllegalArgumentException(
                        "Lecture stream cannot be empty");
            }
        }
    }
}
