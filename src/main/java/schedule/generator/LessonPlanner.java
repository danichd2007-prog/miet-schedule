package schedule.generator;

import schedule.model.Lesson;
import schedule.model.TeachingRequirement;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class LessonPlanner {

    public List<Lesson> createLessons(
            List<TeachingRequirement> requirements) {

        Objects.requireNonNull(
                requirements, "Requirements cannot be null");

        List<Lesson> lessons = new ArrayList<>();

        for (TeachingRequirement requirement : requirements) {

            Objects.requireNonNull(
                    requirement, "TeachingRequirement cannot be null");

            int totalPairs = requirement.getTotalPairs();
            int duration = requirement.isBlock() ? 2 : 1;

            if (totalPairs % duration != 0) {
                throw new IllegalArgumentException(
                        "Total pairs cannot be divided into blocks: "
                                + requirement);
            }

            int lessonCount = totalPairs / duration;

            for (int i = 0; i < lessonCount; i++) {
                lessons.add(new Lesson(requirement, duration));
            }
        }

        return List.copyOf(lessons);
    }
}
