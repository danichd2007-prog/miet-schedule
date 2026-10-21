
package schedule.generator;

import schedule.model.*;

import java.util.*;

public class ScheduleQualityEvaluator {
    //fd
    private final int weekCount;

    public ScheduleQualityEvaluator(int weekCount) {
        if (weekCount <= 0) {
            throw new IllegalArgumentException(
                    "Week count must be positive");
        }

        this.weekCount = weekCount;
    }

    public record QualityReport(
            double dailyLoad,
            double studentWindows,
            double subjectRepetitions,
            double weekendsAndLate,
            double teacherInconvenience,
            double weeklyBalance,
            double stability) {

        public double total() {
            return dailyLoad
                    + studentWindows
                    + subjectRepetitions
                    + weekendsAndLate
                    + teacherInconvenience
                    + weeklyBalance
                    + stability;
        }

        public void print() {
            System.out.println("=== КАЧЕСТВО РАСПИСАНИЯ ===");

            System.out.printf(
                    "Нагрузка студентов:        %.1f%n", dailyLoad);
            System.out.printf(
                    "Окна студентов:            %.1f%n", studentWindows);
            System.out.printf(
                    "Повторения дисциплин:      %.1f%n", subjectRepetitions);
            System.out.printf(
                    "Субботы и поздние пары:    %.1f%n", weekendsAndLate);
            System.out.printf(
                    "Неудобства преподавателей: %.1f%n", teacherInconvenience);
            System.out.printf(
                    "Неравномерность недель:    %.1f%n", weeklyBalance);
            System.out.printf(
                    "Нестабильность слотов:     %.1f%n", stability);

            System.out.printf(
                    "%nИТОГОВЫЙ ШТРАФ: %.1f%n", total());
        }
    }

    private record DayKey(int weekNumber, WeekDay day) {
    }

    private record SlotKey(
            WeekDay day,
            int pair,
            Classroom classroom) {
    }

    public double evaluate(Schedule schedule) {
        return evaluateDetailed(schedule).total();
    }

    public QualityReport evaluateDetailed(Schedule schedule) {

        Objects.requireNonNull(schedule);

        Map<StudentGroup, List<ScheduleEntry>> groups =
                new HashMap<>();

        Map<Teacher, List<ScheduleEntry>> teachers =
                new HashMap<>();

        for (ScheduleEntry entry : schedule.getEntries()) {

            for (StudentGroup group : getGroups(entry.getLesson())) {
                groups.computeIfAbsent(
                        group, k -> new ArrayList<>()
                ).add(entry);
            }

            teachers.computeIfAbsent(
                    entry.getLesson().getTeacher(),
                    k -> new ArrayList<>()
            ).add(entry);
        }

        double dailyLoad = 0;
        double studentWindows = 0;
        double repetitions = 0;
        double weekendsAndLate = 0;
        double teacherInconvenience = 0;
        double weeklyBalance = 0;

        for (List<ScheduleEntry> entries : groups.values()) {

            for (List<ScheduleEntry> day :
                    groupByDay(entries).values()) {

                Lesson[] occupied = buildDay(day);

                int pairs = countOccupied(occupied);
                int windows = countWindows(occupied);

                dailyLoad += dailyLoadPenalty(pairs);
                studentWindows += windowPenalty(windows);

                repetitions += repetitionPenalty(occupied);

                if (day.get(0).getTimeSlot().getDay()
                        == WeekDay.SATURDAY) {
                    weekendsAndLate += 3;
                }

                if (occupied[7] != null) {
                    weekendsAndLate += 5;
                }

                if (occupied[8] != null) {
                    weekendsAndLate += 12;
                }
            }

            weeklyBalance += weeklyBalancePenalty(entries);
        }

        for (List<ScheduleEntry> entries : teachers.values()) {

            for (List<ScheduleEntry> day :
                    groupByDay(entries).values()) {

                Lesson[] occupied = buildDay(day);

                int pairs = countOccupied(occupied);
                int windows = countWindows(occupied);

                teacherInconvenience += windows * 4;

                if (pairs == 1) {
                    teacherInconvenience += 8;
                }

                if (pairs >= 7) {
                    teacherInconvenience += 10;
                }
            }
        }

        double stability = stabilityPenalty(schedule);

        return new QualityReport(
                dailyLoad,
                studentWindows,
                repetitions,
                weekendsAndLate,
                teacherInconvenience,
                weeklyBalance,
                stability
        );
    }

    private double dailyLoadPenalty(int pairs) {
        return switch (pairs) {
            case 0, 2, 3, 4 -> 0;
            case 1 -> 25;
            case 5 -> 3;
            case 6 -> 10;
            case 7 -> 25;
            case 8 -> 50;
            default -> throw new IllegalArgumentException(
                    "Invalid daily load");
        };
    }

    private double windowPenalty(int windows) {
        return switch (windows) {
            case 0 -> 0;
            case 1 -> 8;
            case 2 -> 25;
            case 3 -> 50;
            default -> 80 + (windows - 4) * 20;
        };
    }

    private double repetitionPenalty(Lesson[] occupied) {

        double penalty = 0;
        int pair = 1;

        while (pair <= 8) {

            Lesson first = occupied[pair];

            if (first == null) {
                pair++;
                continue;
            }

            int end = pair;

            while (end < 8
                    && occupied[end + 1] != null
                    && first.getSubject().equals(
                    occupied[end + 1].getSubject())
                    && first.getLessonType()
                    == occupied[end + 1].getLessonType()) {
                end++;
            }

            int length = end - pair + 1;

            if (length >= 3) {
                penalty += 30 + (length - 3) * 15;
            } else if (length == 2
                    && !first.isDoubleBlock()) {
                penalty += 5;
            }

            pair = end + 1;
        }

        return penalty;
    }

    private double weeklyBalancePenalty(
            List<ScheduleEntry> entries) {

        int[] weeklyPairs = new int[weekCount];

        for (ScheduleEntry entry : entries) {

            int week = entry.getTimeSlot().getWeekNumber();

            if (week < 1 || week > weekCount) {
                throw new IllegalArgumentException(
                        "Lesson week is outside the semester");
            }

            weeklyPairs[week - 1] +=
                    entry.getLesson().getDuration();
        }

        double average = Arrays.stream(weeklyPairs)
                .average()
                .orElse(0);

        double penalty = 0;

        for (int pairs : weeklyPairs) {
            penalty += Math.abs(pairs - average);
        }

        return penalty;
    }

    private double stabilityPenalty(Schedule schedule) {

        Map<TeachingRequirement, List<ScheduleEntry>> byRequirement =
                new IdentityHashMap<>();

        for (ScheduleEntry entry : schedule.getEntries()) {
            byRequirement.computeIfAbsent(
                    entry.getLesson().getRequirement(),
                    k -> new ArrayList<>()
            ).add(entry);
        }

        double penalty = 0;

        for (List<ScheduleEntry> entries :
                byRequirement.values()) {

            Map<SlotKey, Set<Integer>> slotWeeks =
                    new HashMap<>();

            for (ScheduleEntry entry : entries) {

                TimeSlot slot = entry.getTimeSlot();

                SlotKey key = new SlotKey(
                        slot.getDay(),
                        slot.getLessonNumber(),
                        entry.getClassroom()
                );

                slotWeeks.computeIfAbsent(
                        key, k -> new HashSet<>()
                ).add(slot.getWeekNumber());
            }

            for (Set<Integer> weeks : slotWeeks.values()) {
                penalty += patternDeviation(weeks) * 12.0;
            }
        }

        return penalty;
    }

    private int patternDeviation(Set<Integer> actualWeeks) {

        int minimum = Integer.MAX_VALUE;

        for (WeekType type : WeekType.values()) {

            int differences = 0;

            for (int week = 1; week <= weekCount; week++) {

                boolean expected = covers(type, week);
                boolean actual = actualWeeks.contains(week);

                if (expected != actual) {
                    differences++;
                }
            }

            minimum = Math.min(minimum, differences);
        }

        return minimum;
    }

    private boolean covers(WeekType type, int week) {

        int phase = (week - 1) % 4;

        return switch (type) {
            case EVERY_WEEK -> true;
            case NUMERATOR -> phase == 0 || phase == 2;
            case DENOMINATOR -> phase == 1 || phase == 3;
            case F_NUMERATOR -> phase == 0;
            case F_DENOMINATOR -> phase == 1;
            case S_NUMERATOR -> phase == 2;
            case S_DENOMINATOR -> phase == 3;
        };
    }

    private Map<DayKey, List<ScheduleEntry>> groupByDay(
            List<ScheduleEntry> entries) {

        Map<DayKey, List<ScheduleEntry>> result =
                new HashMap<>();

        for (ScheduleEntry entry : entries) {

            TimeSlot slot = entry.getTimeSlot();

            DayKey key = new DayKey(
                    slot.getWeekNumber(),
                    slot.getDay()
            );

            result.computeIfAbsent(
                    key, k -> new ArrayList<>()
            ).add(entry);
        }

        return result;
    }

    private Lesson[] buildDay(List<ScheduleEntry> entries) {

        Lesson[] occupied = new Lesson[9];

        for (ScheduleEntry entry : entries) {

            int start = entry.getTimeSlot().getLessonNumber();
            int end = start + entry.getLesson().getDuration() - 1;

            if (start < 1 || end > 8) {
                throw new IllegalArgumentException(
                        "Lesson is outside allowed pairs");
            }

            for (int pair = start; pair <= end; pair++) {

                if (occupied[pair] != null) {
                    throw new IllegalArgumentException(
                            "Overlapping lessons");
                }

                occupied[pair] = entry.getLesson();
            }
        }

        return occupied;
    }

    private int countOccupied(Lesson[] occupied) {

        int count = 0;

        for (int pair = 1; pair <= 8; pair++) {
            if (occupied[pair] != null) {
                count++;
            }
        }

        return count;
    }

    private int countWindows(Lesson[] occupied) {

        int first = -1;
        int last = -1;

        for (int pair = 1; pair <= 8; pair++) {
            if (occupied[pair] != null) {
                if (first == -1) {
                    first = pair;
                }
                last = pair;
            }
        }

        if (first == -1) {
            return 0;
        }

        int windows = 0;

        for (int pair = first; pair <= last; pair++) {
            if (occupied[pair] == null) {
                windows++;
            }
        }

        return windows;
    }

    private List<StudentGroup> getGroups(Lesson lesson) {

        if (lesson.getStream() != null) {
            return lesson.getStream().getGroups();
        }

        return List.of(lesson.getGroup());
    }
}
