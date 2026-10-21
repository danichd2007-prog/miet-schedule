package schedule.generator;

import schedule.calendar.AcademicCalendar;
import schedule.constraint.ConstraintValidator;
import schedule.model.*;

import java.util.*;

public class ScheduleGenerator {

    private static final int MAX_SEARCH_STEPS = 200_000;

    private final ConstraintValidator validator;
    private final LessonPlanner lessonPlanner;
    private final TimeSlotGenerator slotGenerator;

    private int searchSteps;

    public ScheduleGenerator(ConstraintValidator validator) {
        this.validator = Objects.requireNonNull(validator);
        this.lessonPlanner = new LessonPlanner();
        this.slotGenerator = new TimeSlotGenerator();
    }

    public Optional<Schedule> generate(
            List<TeachingRequirement> requirements,
            List<Classroom> classrooms,
            AcademicCalendar calendar) {

        Objects.requireNonNull(requirements);
        Objects.requireNonNull(classrooms);
        Objects.requireNonNull(calendar);

        List<Lesson> lessons =
                new ArrayList<>(lessonPlanner.createLessons(requirements));

        List<Classroom> rooms = List.copyOf(classrooms);

        List<TimeSlot> slots = slotGenerator.generateSlots(
                calendar,
                Arrays.asList(WeekDay.values()),
                1,
                8
        );

        validateLessons(lessons);

        lessons.sort(Comparator
                .comparing(Lesson::isDoubleBlock).reversed()
                .thenComparing(
                        lesson -> lesson.getStream() == null ? 0 : 1,
                        Comparator.reverseOrder()
                ));

        Schedule schedule = new Schedule(
                "Сгенерированное расписание",
                "Расписание на весь семестр"
        );

        searchSteps = 0;

        boolean success = backtrack(
                0,
                lessons,
                slots,
                rooms,
                schedule,
                calendar.getWeekCount()
        );

        return success
                ? Optional.of(schedule)
                : Optional.empty();
    }

    private boolean backtrack(
            int index,
            List<Lesson> lessons,
            List<TimeSlot> slots,
            List<Classroom> rooms,
            Schedule schedule,
            int weekCount) {

        if (index == lessons.size()) {
            return true;
        }

        if (++searchSteps > MAX_SEARCH_STEPS) {
            return false;
        }

        Lesson lesson = lessons.get(index);

        List<ScheduleEntry> candidates =
                createCandidates(lesson, slots, rooms, schedule);

        candidates.sort(Comparator.comparingDouble(
                entry -> calculateCandidateScore(
                        entry,
                        schedule,
                        weekCount
                )
        ));

        for (ScheduleEntry candidate : candidates) {

            schedule.addEntry(candidate);

            if (backtrack(
                    index + 1,
                    lessons,
                    slots,
                    rooms,
                    schedule,
                    weekCount)) {
                return true;
            }

            schedule.removeEntry(candidate);
        }

        return false;
    }

    private List<ScheduleEntry> createCandidates(
            Lesson lesson,
            List<TimeSlot> slots,
            List<Classroom> rooms,
            Schedule schedule) {

        List<ScheduleEntry> candidates = new ArrayList<>();

        for (TimeSlot slot : slots) {

            if (slot.getLessonNumber()
                    + lesson.getDuration() - 1 > 8) {
                continue;
            }

            for (Classroom room : rooms) {

                if (room.getType()
                        != lesson.getRequiredClassroomType()) {
                    continue;
                }

                ScheduleEntry entry =
                        new ScheduleEntry(lesson, slot, room);

                if (validator.canAdd(entry, schedule)) {
                    candidates.add(entry);
                }
            }
        }

        return candidates;
    }

    private double calculateCandidateScore(
            ScheduleEntry candidate,
            Schedule schedule,
            int weekCount) {

        Lesson lesson = candidate.getLesson();
        TimeSlot slot = candidate.getTimeSlot();

        int sameRequirementWeek = 0;
        int sameGroupWeekPairs = 0;
        int sameSlotCount = 0;
        int dayPairs = 0;

        for (ScheduleEntry existing : schedule.getEntries()) {

            Lesson other = existing.getLesson();
            TimeSlot otherSlot = existing.getTimeSlot();

            if (other.getRequirement()
                    == lesson.getRequirement()) {

                if (otherSlot.getWeekNumber()
                        == slot.getWeekNumber()) {
                    sameRequirementWeek++;
                }

                if (otherSlot.getDay() == slot.getDay()
                        && otherSlot.getLessonNumber()
                        == slot.getLessonNumber()) {
                    sameSlotCount++;
                }
            }

            if (sharesGroup(lesson, other)) {

                if (otherSlot.getWeekNumber()
                        == slot.getWeekNumber()) {

                    sameGroupWeekPairs += other.getDuration();

                    if (otherSlot.getDay() == slot.getDay()) {
                        dayPairs += other.getDuration();
                    }
                }
            }
        }

        double score = 0;

        // Равномерность одной дисциплины по неделям
        int totalPairs = lesson.getRequirement().getTotalPairs();

        double averageBlocks =
                (double) totalPairs
                        / lesson.getDuration()
                        / weekCount;

        score += 15.0 * Math.max(
                0,
                sameRequirementWeek + 1 - averageBlocks
        );

        // Общая недельная нагрузка группы
        score += sameGroupWeekPairs * 2.0;

        // Предпочтение постоянного дня и времени
        score -= Math.min(sameSlotCount, 10) * 4.0;

        // Приблизительная оценка дневной нагрузки
        int newDayPairs = dayPairs + lesson.getDuration();

        if (newDayPairs > 4) {
            score += (newDayPairs - 4) * 5.0;
        }

        // Суббота допустима, но менее желательна
        if (slot.getDay() == WeekDay.SATURDAY) {
            score += 3;
        }

        // Поздние занятия
        if (slot.getLessonNumber() >= 7) {
            score += 5;
        }

        // Небольшое предпочтение более ранних пар
        score += slot.getLessonNumber() * 0.1;

        return score;
    }

    private boolean sharesGroup(
            Lesson first,
            Lesson second) {

        Set<StudentGroup> firstGroups = getGroups(first);
        Set<StudentGroup> secondGroups = getGroups(second);

        for (StudentGroup group : firstGroups) {
            if (secondGroups.contains(group)) {
                return true;
            }
        }

        return false;
    }

    private Set<StudentGroup> getGroups(Lesson lesson) {

        if (lesson.getStream() != null) {
            return new HashSet<>(
                    lesson.getStream().getGroups()
            );
        }

        return Set.of(lesson.getGroup());
    }

    private void validateLessons(List<Lesson> lessons) {

        for (Lesson lesson : lessons) {

            if (lesson.getStream() != null
                    && lesson.getStream().getGroups().isEmpty()) {

                throw new IllegalArgumentException(
                        "Lecture stream cannot be empty");
            }
        }
    }
}
