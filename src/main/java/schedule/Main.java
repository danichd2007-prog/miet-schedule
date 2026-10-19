
package schedule;

import schedule.constraint.ConstraintValidator;
import schedule.generator.ScheduleGenerator;
import schedule.generator.TimeSlotGenerator;
import schedule.model.*;

import java.util.*;

public class Main {

    private record Row(
            String subject,
            String teacher,
            LessonType type,
            WeekType week,
            ClassroomType classroomType
    ) {}

    // Данные группы ПИН-27, извлечённые из Excel
    private static final String[] RAW = {
            "Основы Java^Воронова Н.М.^LABORATORY^S_NUMERATOR^LABORATORY",
            "Основы Java^Воронова Н.М.^LABORATORY^S_NUMERATOR^LABORATORY",

            "Электротехника^Камолов М.М.^LABORATORY^S_NUMERATOR^LABORATORY",
            "Электротехника^Камолов М.М.^LABORATORY^S_NUMERATOR^LABORATORY",

            "Теория вероятностей и математическая статистика^Жаркова Н.В.^PRACTICE^EVERY_WEEK^REGULAR",
            "Теория вероятностей и математическая статистика^Жаркова Н.В.^PRACTICE^F_DENOMINATOR^REGULAR",
            "Теория вероятностей и математическая статистика^Жаркова Н.В.^LABORATORY^S_DENOMINATOR^LABORATORY",

            "Индивидуальные виды спорта / Командные виды спорта^Преподаватель ФВ^PRACTICE^EVERY_WEEK^REGULAR",
            "Индивидуальные виды спорта / Командные виды спорта^Преподаватель ФВ^PRACTICE^EVERY_WEEK^REGULAR",

            "Алгоритмы и структуры данных^Лупин С.С.^LECTURE^EVERY_WEEK^LECTURE",
            "Основы Java^Андрианов А.М.^LECTURE^EVERY_WEEK^LECTURE",

            "Иностранный язык^Горбачева Е.А.^PRACTICE^EVERY_WEEK^REGULAR",
            "Иностранный язык^Горбачева Е.А.^PRACTICE^EVERY_WEEK^REGULAR",

            "Алгоритмы и структуры данных^Волков А.С.^LABORATORY^F_NUMERATOR^LABORATORY",
            "Алгоритмы и структуры данных^Волков А.С.^LABORATORY^F_NUMERATOR^LABORATORY",

            "Основы Java^Воронова Н.М.^LABORATORY^F_NUMERATOR^LABORATORY",
            "Основы Java^Воронова Н.М.^LABORATORY^F_NUMERATOR^LABORATORY",

            "Индивидуальные виды спорта / Командные виды спорта^Преподаватель ФВ^PRACTICE^EVERY_WEEK^REGULAR",

            "Электротехника^Камолов М.М.^PRACTICE^EVERY_WEEK^REGULAR",
            "Электротехника^Хисамов В.Т.^LECTURE^EVERY_WEEK^LECTURE",

            "Сети ЭВМ и телекоммуникации/Сети и телекоммуникации^Слюсарь В.В.^LECTURE^EVERY_WEEK^LECTURE",
            "Теория вероятностей и математическая статистика^Олейник Т.А.^LECTURE^EVERY_WEEK^LECTURE",

            "Сети ЭВМ и телекоммуникации^Слюсарь М.В.^LABORATORY^EVERY_WEEK^LABORATORY",
            "Сети ЭВМ и телекоммуникации^Слюсарь М.В.^LABORATORY^EVERY_WEEK^LABORATORY",

            "Теория вероятностей и математическая статистика^Олейник Т.А.^LECTURE^EVERY_WEEK^LECTURE",

            "Математический анализ^Мигунова Е.С.^PRACTICE^EVERY_WEEK^REGULAR",
            "Математический анализ^Безменов И.В.^LECTURE^EVERY_WEEK^LECTURE",
            "Математический анализ^Мигунова Е.С.^PRACTICE^EVERY_WEEK^REGULAR",

            "Иностранный язык^Горбачева Е.А.^PRACTICE^EVERY_WEEK^REGULAR",
            "Алгоритмы и структуры данных^Волков А.С.^PRACTICE^EVERY_WEEK^REGULAR"
    };

    public static void main(String[] args) {

        // 1. Направление и группа
        Direction direction = new Direction(
                "Программная инженерия",
                "ПИН"
        );

        StudentGroup group = new StudentGroup(
                "ПИН-27",
                2,
                direction
        );

        // 2. Коллекции объектов
        Map<String, Subject> subjects = new LinkedHashMap<>();
        Map<String, Teacher> teachers = new LinkedHashMap<>();

        Map<String, TeacherSubject> qualifications = new HashMap<>();
        Map<String, SubjectToGroup> groupSubjects = new HashMap<>();
        Map<String, Stream> lectureStreams = new HashMap<>();

        List<Lesson> lessons = new ArrayList<>();

        // 3. Создаём дисциплины, преподавателей и занятия
        for (String line : RAW) {

            String[] fields = line.split("\\^", -1);

            Row row = new Row(
                    fields[0],
                    fields[1],
                    LessonType.valueOf(fields[2]),
                    WeekType.valueOf(fields[3]),
                    ClassroomType.valueOf(fields[4])
            );

            Subject subject = subjects.computeIfAbsent(
                    row.subject(),
                    Subject::new
            );

            Teacher teacher = teachers.computeIfAbsent(
                    row.teacher(),
                    Main::makeTeacher
            );

            // Связь преподавателя с дисциплиной
            String qualificationKey =
                    row.teacher() + "|" + row.subject();

            TeacherSubject qualification =
                    qualifications.get(qualificationKey);

            if (qualification == null) {

                EnumSet<LessonType> types =
                        EnumSet.noneOf(LessonType.class);

                for (String candidate : RAW) {
                    String[] candidateFields =
                            candidate.split("\\^", -1);

                    if (candidateFields[0].equals(row.subject())
                            && candidateFields[1].equals(row.teacher())) {

                        types.add(
                                LessonType.valueOf(candidateFields[2])
                        );
                    }
                }

                qualification = new TeacherSubject(
                        teacher,
                        subject,
                        types
                );

                qualifications.put(
                        qualificationKey,
                        qualification
                );
            }

            TeachingRequirement requirement;

            // 4. Лекции назначаются потокам
            if (row.type() == LessonType.LECTURE) {

                Stream stream = lectureStreams.computeIfAbsent(
                        row.subject(),
                        name -> {
                            Stream result = new Stream(
                                    "Поток ПИН-27: " + name,
                                    subject
                            );

                            result.addGroup(group);
                            return result;
                        }
                );

                requirement = TeachingRequirement.forLecture(
                        qualification,
                        stream,
                        1,
                        row.week(),
                        row.classroomType()
                );

            } else {

                // 5. Остальные занятия назначаются группе
                SubjectToGroup subjectToGroup =
                        groupSubjects.computeIfAbsent(
                                row.subject(),
                                name -> new SubjectToGroup(
                                        subject,
                                        group
                                )
                        );

                requirement = TeachingRequirement.forGroup(
                        qualification,
                        subjectToGroup,
                        row.type(),
                        1,
                        row.week(),
                        row.classroomType()
                );
            }

            lessons.add(new Lesson(requirement, 2));
        }

        // 6. Тестовые аудитории
        List<Classroom> classrooms = List.of(

                new Classroom(
                        "1201", "МИЭТ",
                        ClassroomType.LECTURE,
                        120, true
                ),

                new Classroom(
                        "1205", "МИЭТ",
                        ClassroomType.LECTURE,
                        120, true
                ),

                new Classroom(
                        "3118", "МИЭТ",
                        ClassroomType.LABORATORY,
                        30, true
                ),

                new Classroom(
                        "4340", "МИЭТ",
                        ClassroomType.LABORATORY,
                        30, true
                ),

                new Classroom(
                        "3202", "МИЭТ",
                        ClassroomType.LABORATORY,
                        30, true
                ),

                new Classroom(
                        "3229", "МИЭТ",
                        ClassroomType.REGULAR,
                        40, true
                ),

                new Classroom(
                        "4303", "МИЭТ",
                        ClassroomType.REGULAR,
                        40, true
                ),

                new Classroom(
                        "5101", "МИЭТ",
                        ClassroomType.REGULAR,
                        40, true
                )
        );

        // 7. Рабочие дни
        List<WeekDay> days = List.of(
                WeekDay.MONDAY,
                WeekDay.TUESDAY,
                WeekDay.WEDNESDAY,
                WeekDay.THURSDAY,
                WeekDay.FRIDAY,
                WeekDay.SATURDAY
        );

        // 8. Временная тестовая доступность преподавателей
        List<TeacherAvailability> availabilities =
                new ArrayList<>();

        for (Teacher teacher : teachers.values()) {

            for (WeekDay day : days) {

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

        // 9. Генерируем временные слоты через BellSchedule
        TimeSlotGenerator slotGenerator =
                new TimeSlotGenerator();

        List<TimeSlot> slots = slotGenerator.generateSlots(
                days,
                Arrays.asList(WeekType.values()),
                1,
                8
        );

        // 10. Создаём валидатор ограничений
        ConstraintValidator validator =
                new ConstraintValidator(availabilities);

        // 11. Создаём генератор расписания
        ScheduleGenerator generator =
                new ScheduleGenerator(validator);

        System.out.println("Группа: " + group.getName());
        System.out.println("Дисциплин: " + subjects.size());
        System.out.println("Преподавателей: " + teachers.size());
        System.out.println("Занятий: " + lessons.size());
        System.out.println("Возможных слотов: " + slots.size());
        System.out.println();

        // 12. Запускаем алгоритм
        long start = System.nanoTime();

        Optional<Schedule> result = generator.generate(
                lessons,
                slots,
                classrooms
        );

        long elapsed = System.nanoTime() - start;

        // 13. Проверяем результат
        if (result.isEmpty()) {
            System.out.println(
                    "Не удалось составить расписание."
            );
            return;
        }

        // 14. Сортируем занятия по дням и парам
        List<ScheduleEntry> entries =
                new ArrayList<>(result.get().getEntries());

        entries.sort(
                Comparator
                        .comparing((ScheduleEntry entry) ->
                                entry.getTimeSlot().getDay())
                        .thenComparing(entry ->
                                entry.getTimeSlot().getLessonNumber())
                        .thenComparing(entry ->
                                entry.getTimeSlot().getWeekType().name())
        );

        // 15. Вывод готового расписания
        System.out.println("РАСПИСАНИЕ ПИН-27");
        System.out.println("================================");

        WeekDay currentDay = null;

        for (ScheduleEntry entry : entries) {

            WeekDay day = entry.getTimeSlot().getDay();

            if (day != currentDay) {
                currentDay = day;

                System.out.println();
                System.out.println("=== " + day + " ===");
            }

            System.out.println(entry);
        }

        System.out.println();
        System.out.println("================================");

        System.out.println(
                "Размещено занятий: " + entries.size()
        );

        System.out.printf(
                "Время генерации: %.3f мс%n",
                elapsed / 1_000_000.0
        );
    }

    // Создание преподавателя по фамилии и инициалам
    private static Teacher makeTeacher(String displayName) {

        String[] words = displayName.trim().split("\\s+", 2);

        if (words.length == 2
                && words[1].matches("[А-ЯЁ]\\.[А-ЯЁ]\\.?")) {

            String initials = words[1];

            return new Teacher(
                    initials.substring(0, 1),
                    words[0],
                    initials.substring(2, 3),
                    "Не указана"
            );
        }

        return new Teacher(
                "Неизвестно",
                displayName,
                "",
                "Не указана"
        );
    }
}
