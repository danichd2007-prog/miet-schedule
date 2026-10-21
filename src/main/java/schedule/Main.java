package schedule;

import schedule.calendar.AcademicCalendar;
import schedule.constraint.ConstraintValidator;
import schedule.generator.ScheduleGenerator;
import schedule.generator.ScheduleQualityEvaluator;
import schedule.model.*;

import java.time.LocalDate;
import java.util.*;

public class Main {

    public static void main(String[] args) {

        AcademicCalendar calendar = new AcademicCalendar(
                LocalDate.of(2026, 9, 7),
                16
        );

        Direction direction = new Direction(
                "Программная инженерия", "09.03.04"
        );

        StudentGroup group = new StudentGroup(
                "ПИН-ТЕСТ", 2, direction
        );

        Subject math = new Subject("Математический анализ");
        Subject english = new Subject("Английский язык");
        Subject java = new Subject("Основы Java");
        Subject algorithms = new Subject("Алгоритмы и структуры данных");

        Teacher mathTeacher = new Teacher(
                "Иван", "Иванов", "Иванович", "Математика"
        );

        Teacher englishTeacher = new Teacher(
                "Анна", "Петрова", "Сергеевна", "Иностранные языки"
        );

        Teacher javaTeacher = new Teacher(
                "Павел", "Сидоров", "Алексеевич", "Информатика"
        );

        Teacher algorithmsTeacher = new Teacher(
                "Ольга", "Смирнова", "Викторовна", "Информатика"
        );

        List<Teacher> teachers = List.of(
                mathTeacher,
                englishTeacher,
                javaTeacher,
                algorithmsTeacher
        );

        List<TeachingRequirement> requirements = List.of(
                TeachingRequirement.forGroup(
                        new TeacherSubject(
                                mathTeacher, math,
                                Set.of(LessonType.SEMINAR)
                        ),
                        new SubjectToGroup(math, group),
                        LessonType.SEMINAR,
                        16,
                        ClassroomType.REGULAR,
                        false
                ),
                TeachingRequirement.forGroup(
                        new TeacherSubject(
                                englishTeacher, english,
                                Set.of(LessonType.PRACTICE)
                        ),
                        new SubjectToGroup(english, group),
                        LessonType.PRACTICE,
                        24,
                        ClassroomType.REGULAR,
                        false
                ),
                TeachingRequirement.forGroup(
                        new TeacherSubject(
                                javaTeacher, java,
                                Set.of(LessonType.LABORATORY)
                        ),
                        new SubjectToGroup(java, group),
                        LessonType.LABORATORY,
                        16,
                        ClassroomType.COMPUTER,
                        true
                ),
                TeachingRequirement.forGroup(
                        new TeacherSubject(
                                algorithmsTeacher, algorithms,
                                Set.of(LessonType.LABORATORY)
                        ),
                        new SubjectToGroup(algorithms, group),
                        LessonType.LABORATORY,
                        8,
                        ClassroomType.COMPUTER,
                        true
                )
        );

        List<Classroom> classrooms = List.of(
                new Classroom(
                        "101", "1",
                        ClassroomType.REGULAR, 30, true
                ),
                new Classroom(
                        "102", "1",
                        ClassroomType.REGULAR, 30, true
                ),
                new Classroom(
                        "201", "1",
                        ClassroomType.COMPUTER, 30, true
                ),
                new Classroom(
                        "202", "1",
                        ClassroomType.COMPUTER, 30, true
                )
        );

        List<TeacherAvailability> availabilities =
                new ArrayList<>();

        for (Teacher teacher : teachers) {
            for (WeekDay day : WeekDay.values()) {
                availabilities.add(
                        new TeacherAvailability(
                                teacher,
                                day,
                                1,
                                8,
                                WeekType.EVERY_WEEK
                        )
                );
            }
        }

        ConstraintValidator validator =
                new ConstraintValidator(availabilities, calendar);

        ScheduleGenerator generator =
                new ScheduleGenerator(validator);

        long started = System.nanoTime();

        Optional<Schedule> result = generator.generate(
                requirements,
                classrooms,
                calendar
        );

        double seconds =
                (System.nanoTime() - started) / 1_000_000_000.0;

        if (result.isEmpty()) {
            System.out.println("Расписание не найдено.");

            if (generator.wasSearchLimitReached()) {
                System.out.println(
                        "Причина: достигнут лимит поиска."
                );
            }

            return;
        }

        Schedule schedule = result.get();


        System.out.println();
        System.out.println("=== РЕЗУЛЬТАТ ОПТИМИЗАЦИИ ===");

        System.out.printf(
                "Штраф до оптимизации:    %.1f%n",
                generator.getQualityBeforeOptimization()
        );

        System.out.printf(
                "Штраф после оптимизации: %.1f%n",
                generator.getQualityAfterOptimization()
        );

        System.out.printf(
                "Улучшение:                %.1f%n",
                generator.getQualityBeforeOptimization()
                        - generator.getQualityAfterOptimization()
        );


        System.out.printf(
                "Расписание построено за %.3f сек.%n", seconds
        );

        int requiredPairs = requirements.stream()
                .mapToInt(TeachingRequirement::getTotalPairs)
                .sum();

        int actualPairs = schedule.getEntries().stream()
                .mapToInt(e -> e.getLesson().getDuration())
                .sum();

        System.out.println();
        System.out.println("=== ПРОВЕРКА НАГРУЗКИ ===");
        System.out.println("Требовалось пар: " + requiredPairs);
        System.out.println("Размещено пар: " + actualPairs);
        System.out.println(
                "Количество занятий: " + schedule.getEntries().size()
        );

        System.out.println();
        System.out.println("=== НАГРУЗКА ПО НЕДЕЛЯМ ===");

        for (int week = 1; week <= calendar.getWeekCount(); week++) {
            final int currentWeek = week;

            int pairs = schedule.getEntries().stream()
                    .filter(e -> e.getTimeSlot().getWeekNumber()
                            == currentWeek)
                    .mapToInt(e -> e.getLesson().getDuration())
                    .sum();

            System.out.printf(
                    "Неделя %2d | %-15s | %d пар%n",
                    week,
                    calendar.getWeekType(week),
                    pairs
            );
        }

        System.out.println();
        System.out.println("=== ПОВТОРЯЮЩИЕСЯ СЛОТЫ ===");

        Map<String, List<Integer>> recurring = new TreeMap<>();

        for (ScheduleEntry entry : schedule.getEntries()) {
            Lesson lesson = entry.getLesson();
            TimeSlot slot = entry.getTimeSlot();

            String key = lesson.getSubject().getName()
                    + " | " + lesson.getLessonType()
                    + " | " + slot.getDay()
                    + " | пара " + slot.getLessonNumber()
                    + (lesson.isDoubleBlock()
                    ? "-" + (slot.getLessonNumber() + 1)
                    : "")
                    + " | ауд. " + entry.getClassroom().getNumber();

            recurring.computeIfAbsent(
                    key, k -> new ArrayList<>()
            ).add(slot.getWeekNumber());
        }

        recurring.forEach((key, weeks) -> {
            Collections.sort(weeks);
            System.out.println(key);
            System.out.println("    Недели: " + weeks);
        });

        System.out.println();
        System.out.println("=== ПРОВЕРКА КОНФЛИКТОВ ===");

        int conflicts = 0;
        Schedule checked = new Schedule(
                "Проверка", "Проверка готового расписания"
        );

        for (ScheduleEntry entry : schedule.getEntries()) {
            if (!validator.canAdd(entry, checked)) {
                conflicts++;
            }
            checked.addEntry(entry);
        }

        System.out.println("Нарушений ограничений: " + conflicts);

        System.out.println();

        ScheduleQualityEvaluator evaluator =
                new ScheduleQualityEvaluator(calendar.getWeekCount());

        ScheduleQualityEvaluator.QualityReport report =
                evaluator.evaluateDetailed(schedule);

        System.out.println();
        report.print();


        if (requiredPairs == actualPairs && conflicts == 0) {
            System.out.println(
                    "ПРОВЕРКА ПРОЙДЕНА: нагрузка размещена без конфликтов."
            );
        } else {
            System.out.println(
                    "ПРОВЕРКА НЕ ПРОЙДЕНА."
            );
        }
    }
}
