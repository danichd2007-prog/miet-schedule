
package schedule.generator;

import schedule.calendar.AcademicCalendar;
import schedule.constraint.ConstraintValidator;
import schedule.model.*;
import schedule.util.BellSchedule;
import schedule.util.TimeSlotUtils;

import java.util.*;

public class ScheduleGenerator {

    private static final int MAX_SEARCH_STEPS = 100_000;
    private static final int MAX_OPTIMIZATION_PASSES = 3;
    private static final int MAX_QUALITY_CHECKS = 5_000;

    private final ConstraintValidator validator;
    private final LessonPlanner lessonPlanner = new LessonPlanner();

    private int searchSteps;
    private boolean searchLimitReached;

    private double qualityBeforeOptimization;
    private double qualityAfterOptimization;
    private int optimizationQualityChecks;
    private int acceptedImprovements;
    private int optimizationPasses;

    public ScheduleGenerator(ConstraintValidator validator) {
        this.validator = Objects.requireNonNull(validator);
    }

    public double getQualityBeforeOptimization() {
        return qualityBeforeOptimization;
    }

    public double getQualityAfterOptimization() {
        return qualityAfterOptimization;
    }

    public int getOptimizationQualityChecks() {
        return optimizationQualityChecks;
    }

    public int getAcceptedImprovements() {
        return acceptedImprovements;
    }

    public int getOptimizationPasses() {
        return optimizationPasses;
    }

    public boolean wasSearchLimitReached() {
        return searchLimitReached;
    }

    private record Series(
            List<Lesson> lessons,
            List<WeekType> patterns) {
    }

    private record Choice(
            List<ScheduleEntry> entries,
            double score) {
    }

    public Optional<Schedule> generate(
            List<TeachingRequirement> requirements,
            List<Classroom> classrooms,
            AcademicCalendar calendar) {

        Objects.requireNonNull(requirements);
        Objects.requireNonNull(classrooms);
        Objects.requireNonNull(calendar);

        List<Classroom> rooms = List.copyOf(classrooms);

        List<Series> series = buildSeries(
                requirements, calendar
        );

        series.sort(Comparator
                .comparingInt(
                        (Series s) -> s.lessons().size()
                ).reversed()
                .thenComparingInt(
                        s -> s.lessons().get(0)
                                .isDoubleBlock() ? 0 : 1
                )
                .thenComparingInt(
                        s -> s.lessons().get(0)
                                .getStream() != null ? 0 : 1
                ));

        Schedule schedule = new Schedule(
                "Сгенерированное расписание",
                "Типовое расписание семестра"
        );

        searchSteps = 0;
        searchLimitReached = false;

        qualityBeforeOptimization = 0;
        qualityAfterOptimization = 0;
        optimizationQualityChecks = 0;
        acceptedImprovements = 0;
        optimizationPasses = 0;

        boolean success = backtrack(
                series, 0, rooms, calendar, schedule
        );

        if (!success) {
            return Optional.empty();
        }

        ScheduleQualityEvaluator evaluator =
                new ScheduleQualityEvaluator(
                        calendar.getWeekCount()
                );

        qualityBeforeOptimization =
                evaluator.evaluate(schedule);

        improveSchedule(
                series,
                rooms,
                calendar,
                schedule,
                evaluator
        );

        qualityAfterOptimization =
                evaluator.evaluate(schedule);

        return Optional.of(schedule);
    }

    private List<Series> buildSeries(
            List<TeachingRequirement> requirements,
            AcademicCalendar calendar) {

        List<Series> result = new ArrayList<>();

        Set<TeachingRequirement> seen =
                Collections.newSetFromMap(
                        new IdentityHashMap<>()
                );

        for (TeachingRequirement requirement : requirements) {

            Objects.requireNonNull(requirement);

            if (!seen.add(requirement)) {
                throw new IllegalArgumentException(
                        "Repeated TeachingRequirement object: "
                                + requirement
                );
            }

            List<Lesson> lessons =
                    lessonPlanner.createLessons(
                            List.of(requirement)
                    );

            if (requirement.getStream() != null
                    && requirement.getStream()
                    .getGroups().isEmpty()) {

                throw new IllegalArgumentException(
                        "Lecture stream cannot be empty"
                );
            }

            int offset = 0;

            while (offset < lessons.size()) {

                int remaining =
                        lessons.size() - offset;

                int bestSize = 0;

                List<WeekType> bestPatterns =
                        List.of();

                for (WeekType type : WeekType.values()) {

                    int count = getWeeks(
                            type, calendar
                    ).size();

                    if (count > bestSize
                            && count <= remaining) {

                        bestSize = count;
                        bestPatterns = List.of(type);

                    } else if (count == bestSize
                            && count > 0
                            && count <= remaining) {

                        List<WeekType> updated =
                                new ArrayList<>(bestPatterns);

                        updated.add(type);

                        bestPatterns =
                                List.copyOf(updated);
                    }
                }

                if (bestSize <= 0) {

                    result.add(new Series(
                            List.of(lessons.get(offset)),
                            List.of()
                    ));

                    offset++;

                } else {

                    result.add(new Series(
                            List.copyOf(
                                    lessons.subList(
                                            offset,
                                            offset + bestSize
                                    )
                            ),
                            bestPatterns
                    ));

                    offset += bestSize;
                }
            }
        }

        return result;
    }

    private List<Integer> getWeeks(
            WeekType type,
            AcademicCalendar calendar) {

        List<Integer> weeks = new ArrayList<>();

        for (int week = 1;
             week <= calendar.getWeekCount();
             week++) {

            if (TimeSlotUtils.weeksCover(
                    type,
                    calendar.getWeekType(week))) {

                weeks.add(week);
            }
        }

        return weeks;
    }

    private boolean backtrack(
            List<Series> series,
            int index,
            List<Classroom> rooms,
            AcademicCalendar calendar,
            Schedule schedule) {

        if (index == series.size()) {
            return true;
        }

        if (++searchSteps > MAX_SEARCH_STEPS) {
            searchLimitReached = true;
            return false;
        }

        List<Choice> choices = candidateChoices(
                series.get(index),
                rooms,
                calendar,
                schedule
        );

        choices.sort(
                Comparator.comparingDouble(
                        Choice::score
                )
        );

        for (Choice choice : choices) {

            choice.entries().forEach(
                    schedule::addEntry
            );

            if (backtrack(
                    series,
                    index + 1,
                    rooms,
                    calendar,
                    schedule)) {

                return true;
            }

            choice.entries().forEach(
                    schedule::removeEntry
            );
        }

        return false;
    }

    private List<Choice> candidateChoices(
            Series series,
            List<Classroom> rooms,
            AcademicCalendar calendar,
            Schedule schedule) {

        List<Choice> choices = new ArrayList<>();

        Lesson first = series.lessons().get(0);

        List<List<Integer>> weekVariants =
                new ArrayList<>();

        if (series.patterns().isEmpty()) {

            for (int week = 1;
                 week <= calendar.getWeekCount();
                 week++) {

                weekVariants.add(List.of(week));
            }

        } else {

            for (WeekType type : series.patterns()) {

                weekVariants.add(
                        getWeeks(type, calendar)
                );
            }
        }

        for (List<Integer> weeks : weekVariants) {

            if (weeks.size()
                    != series.lessons().size()) {
                continue;
            }

            for (WeekDay day : WeekDay.values()) {

                for (int pair = 1;
                     pair <= 9 - first.getDuration();
                     pair++) {

                    for (Classroom room : rooms) {

                        if (room.getType()
                                != first.getRequiredClassroomType()) {
                            continue;
                        }

                        List<ScheduleEntry> entries =
                                new ArrayList<>();

                        boolean feasible = true;
                        double score = 0;

                        for (int i = 0;
                             i < weeks.size();
                             i++) {

                            Lesson lesson =
                                    series.lessons().get(i);

                            TimeSlot slot =
                                    BellSchedule.createSlot(
                                            weeks.get(i),
                                            day,
                                            pair
                                    );

                            ScheduleEntry entry =
                                    new ScheduleEntry(
                                            lesson,
                                            slot,
                                            room
                                    );

                            if (!validator.canAdd(
                                    entry,
                                    schedule)) {

                                feasible = false;
                                break;
                            }

                            entries.add(entry);

                            score += scorePlacement(
                                    entry,
                                    schedule
                            );
                        }

                        if (feasible) {

                            if (series.patterns().isEmpty()) {
                                score += 12;
                            }

                            choices.add(new Choice(
                                    List.copyOf(entries),
                                    score
                            ));
                        }
                    }
                }
            }
        }

        return choices;
    }

    private double scorePlacement(
            ScheduleEntry entry,
            Schedule schedule) {

        Lesson lesson = entry.getLesson();
        TimeSlot slot = entry.getTimeSlot();

        int existingGroupPairs = 0;
        int sameSubjectThisWeek = 0;
        int existingDayPairs = 0;
        int adjacentPairs = 0;

        for (ScheduleEntry other : schedule.getEntries()) {

            TimeSlot otherSlot = other.getTimeSlot();

            if (otherSlot.getWeekNumber()
                    != slot.getWeekNumber()) {
                continue;
            }

            if (other.getLesson().getRequirement()
                    == lesson.getRequirement()) {

                sameSubjectThisWeek +=
                        other.getLesson().getDuration();
            }

            if (!sharesGroup(
                    lesson, other.getLesson())) {
                continue;
            }

            existingGroupPairs +=
                    other.getLesson().getDuration();

            if (otherSlot.getDay() == slot.getDay()) {

                existingDayPairs +=
                        other.getLesson().getDuration();

                int end =
                        slot.getLessonNumber()
                                + lesson.getDuration() - 1;

                int otherEnd =
                        otherSlot.getLessonNumber()
                                + other.getLesson()
                                .getDuration() - 1;

                if (otherEnd + 1
                        == slot.getLessonNumber()
                        || end + 1
                        == otherSlot.getLessonNumber()) {

                    adjacentPairs++;
                }
            }
        }

        int newDayPairs =
                existingDayPairs + lesson.getDuration();

        double score =
                existingGroupPairs * 1.5
                        + sameSubjectThisWeek * 5.0;

        score += switch (newDayPairs) {
            case 5 -> 3;
            case 6 -> 10;
            case 7 -> 25;
            case 8 -> 50;
            default -> 0;
        };

        if (slot.getDay() == WeekDay.SATURDAY) {
            score += 3;
        }

        if (slot.getLessonNumber() >= 7) {
            score += 5;
        }

        score -= Math.min(adjacentPairs, 2) * 2;

        score += slot.getLessonNumber() * 0.05;

        return score;
    }

    private void improveSchedule(
            List<Series> series,
            List<Classroom> rooms,
            AcademicCalendar calendar,
            Schedule schedule,
            ScheduleQualityEvaluator evaluator) {

        double currentScore =
                evaluator.evaluate(schedule);

        for (int pass = 0;
             pass < MAX_OPTIMIZATION_PASSES;
             pass++) {

            if (optimizationQualityChecks
                    >= MAX_QUALITY_CHECKS) {
                break;
            }

            optimizationPasses++;
            boolean improved = false;

            for (Series currentSeries : series) {

                if (optimizationQualityChecks
                        >= MAX_QUALITY_CHECKS) {
                    return;
                }

                List<ScheduleEntry> originalEntries =
                        findSeriesEntries(
                                currentSeries,
                                schedule
                        );

                for (ScheduleEntry entry : originalEntries) {
                    schedule.removeEntry(entry);
                }

                List<Choice> choices = candidateChoices(
                        currentSeries,
                        rooms,
                        calendar,
                        schedule
                );

                List<ScheduleEntry> bestEntries =
                        originalEntries;

                double bestScore = currentScore;

                for (Choice choice : choices) {

                    if (optimizationQualityChecks
                            >= MAX_QUALITY_CHECKS) {
                        break;
                    }

                    for (ScheduleEntry entry
                            : choice.entries()) {
                        schedule.addEntry(entry);
                    }

                    double candidateScore =
                            evaluator.evaluate(schedule);

                    optimizationQualityChecks++;

                    for (ScheduleEntry entry
                            : choice.entries()) {
                        schedule.removeEntry(entry);
                    }

                    if (candidateScore
                            < bestScore - 0.000001) {

                        bestScore = candidateScore;
                        bestEntries = choice.entries();
                    }
                }

                for (ScheduleEntry entry : bestEntries) {
                    schedule.addEntry(entry);
                }

                if (bestScore < currentScore - 0.000001) {
                    currentScore = bestScore;
                    acceptedImprovements++;
                    improved = true;
                }
            }

            if (!improved) {
                break;
            }
        }
    }

    private List<ScheduleEntry> findSeriesEntries(
            Series series,
            Schedule schedule) {

        Set<Lesson> lessons =
                Collections.newSetFromMap(
                        new IdentityHashMap<>()
                );

        lessons.addAll(series.lessons());

        List<ScheduleEntry> result =
                new ArrayList<>();

        for (ScheduleEntry entry : schedule.getEntries()) {

            if (lessons.contains(entry.getLesson())) {
                result.add(entry);
            }
        }

        if (result.size()
                != series.lessons().size()) {

            throw new IllegalStateException(
                    "Cannot find all lessons of the series"
            );
        }

        return result;
    }

    private boolean sharesGroup(
            Lesson first,
            Lesson second) {

        Set<StudentGroup> groups = new HashSet<>(
                first.getStream() != null
                        ? first.getStream().getGroups()
                        : List.of(first.getGroup())
        );

        List<StudentGroup> otherGroups =
                second.getStream() != null
                        ? second.getStream().getGroups()
                        : List.of(second.getGroup());

        for (StudentGroup group : otherGroups) {

            if (groups.contains(group)) {
                return true;
            }
        }

        return false;
    }
}
