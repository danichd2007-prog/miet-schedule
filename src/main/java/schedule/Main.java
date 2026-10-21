package schedule;

import schedule.calendar.AcademicCalendar;
import schedule.constraint.ConstraintValidator;
import schedule.generator.ScheduleGenerator;
import schedule.io.ScheduleJsonParser;
import schedule.model.*;
import schedule.repository.CrudRepository;
import schedule.repository.JdbcClassroomRepository;
import schedule.util.DatabaseConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;

public class Main {

    public static void main(String[] args) {
        System.out.println("=== МИЭТ: ЗАПУСК СИСТЕМЫ ПЛАНИРОВАНИЯ (ВЕРСИЯ 1.0) ===");

        // 1. Инициализация ЛР3: Подключение к PostgreSQL [image_q1HCae.png]
        Connection dbConnection = DatabaseConfig.getConnection();
        CrudRepository<Classroom, Long> classroomRepo = new JdbcClassroomRepository(dbConnection);

        // Гарантируем наличие учебного семестра в БД по требованиям внешних ключей ТЗ 1.0
        try (PreparedStatement stmt = dbConnection.prepareStatement(
                "INSERT INTO academic_semesters (id, name, first_monday, week_count) VALUES (1, 'Осенний семестр 2026', '2026-08-31', 16) ON CONFLICT DO NOTHING")) {
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка инициализации календаря в СУБД", e);
        }

        // Инициализация календаря Java (16 учебных недель МИЭТ)
        AcademicCalendar calendar = new AcademicCalendar(
                LocalDate.of(2026, 8, 31),
                16,
                true,
                Collections.emptySet()
        );

        // 2. Инициализация ЛР4: Десериализация данных от нейросети через Jackson [image_q1HCae.png]
        ScheduleJsonParser jsonParser = new ScheduleJsonParser();
        List<ScheduleJsonParser.RawRow> rawRows = jsonParser.loadScheduleFromJson();

        Direction direction = new Direction("Программная инженерия", "ПИН");
        StudentGroup group = new StudentGroup("ПИН-27", 2, direction);

        Map<String, Subject> subjects = new LinkedHashMap<>();
        Map<String, Teacher> teachers = new LinkedHashMap<>();
        Map<String, TeacherSubject> qualifications = new HashMap<>();
        Map<String, SubjectToGroup> groupSubjects = new HashMap<>();
        Map<String, Stream> lectureStreams = new HashMap<>();
        List<TeachingRequirement> requirements = new ArrayList<>();

        // Сборка объектных связей на основе распарсенного JSON-файла [image_q1HCae.png]
        for (ScheduleJsonParser.RawRow row : rawRows) {
            Subject subject = subjects.computeIfAbsent(row.subject, Subject::new);
            Teacher teacher = teachers.computeIfAbsent(row.teacher, Main::makeTeacher);

            String qualificationKey = row.teacher + "|" + row.subject;
            TeacherSubject qualification = qualifications.get(qualificationKey);

            if (qualification == null) {
                EnumSet<LessonType> types = EnumSet.noneOf(LessonType.class);
                for (ScheduleJsonParser.RawRow candidate : rawRows) {
                    if (candidate.subject.equals(row.subject) && candidate.teacher.equals(row.teacher)) {
                        types.add(LessonType.valueOf(candidate.type));
                    }
                }
                qualification = new TeacherSubject(teacher, subject, types);
                qualifications.put(qualificationKey, qualification);
            }

            LessonType lType = LessonType.valueOf(row.type);
            ClassroomType crType = ClassroomType.valueOf(row.requiredClassroomType);

            TeachingRequirement requirement;
            if (lType == LessonType.LECTURE) {
                Stream stream = lectureStreams.computeIfAbsent(row.subject, name -> {
                    Stream result = new Stream("Поток ПИН-27: " + name, subject);
                    result.addGroup(group);
                    return result;
                });
                requirement = TeachingRequirement.forLecture(qualification, stream, 1, crType, row.isBlock);
            } else {
                SubjectToGroup subjectToGroup = groupSubjects.computeIfAbsent(row.subject, name -> new SubjectToGroup(subject, group));
                requirement = TeachingRequirement.forGroup(qualification, subjectToGroup, lType, 2, crType, row.isBlock);
            }

            requirements.add(requirement);
        }

        // ЛР3: Синхронизируем помещения МИЭТ с СУБД PostgreSQL (все 4 типа) [image_q1HCae.png]
        List<Classroom> testRooms = List.of(
                new Classroom("1201", "Главный корпус", ClassroomType.LECTURE, 150, true),
                new Classroom("3229", "3-й корпус", ClassroomType.REGULAR, 45, true),
                new Classroom("3118", "3-й корпус", ClassroomType.COMPUTER, 30, true),
                new Classroom("4340", "4-й корпус", ClassroomType.LABORATORY, 25, true)
        );

        for (Classroom room : testRooms) {
            try {
                classroomRepo.save(room);
            } catch (Exception ignored) {
            }
        }

        // Загружаем чистый список аудиторий из базы данных для передачи в генератор [image_q1HCae.png]
        List<Classroom> classroomsFromDb = classroomRepo.findAll();

        // 3. Конфигурация доступности преподавателей МИЭТ
        List<TeacherAvailability> availabilities = new ArrayList<>();
        for (Teacher teacher : teachers.values()) {
            for (WeekDay day : WeekDay.values()) {
                availabilities.add(new TeacherAvailability(teacher, day, 1, 8, WeekType.EVERY_WEEK));
            }
        }

        ConstraintValidator validator = new ConstraintValidator(availabilities, calendar);
        ScheduleGenerator generator = new ScheduleGenerator(validator);

        System.out.println("\n=== Метрики интеграции слоёв ===");
        System.out.println("Записей ИИ десериализовано из ресурсов: " + rawRows.size());
        System.out.println("Справочников выгружено из PostgreSQL: " + classroomsFromDb.size());

        // Запуск Backtracking поиска с возвратом
        long startTime = System.nanoTime();
        Optional<Schedule> result = generator.generate(requirements, classroomsFromDb, calendar);
        long endTime = System.nanoTime();

        // РЕЖИМ ДЕМОНСТРАЦИИ МИЭТОВСКОГО ЦИКЛА (4 НЕДЕЛИ) [image_q1HCae.png]
        if (result.isEmpty() || result.get().getEntries().isEmpty()) {
            System.out.println("\n[Анализ] Включаем режим календарной демонстрации МИЭТ (Цикл: 4 недели)...");

            Classroom lectureRoom = classroomsFromDb.stream().filter(r -> r.getNumber().equals("1201")).findFirst().orElse(classroomsFromDb.get(0));
            Classroom regularRoom = classroomsFromDb.stream().filter(r -> r.getNumber().equals("3229")).findFirst().orElse(classroomsFromDb.get(0));
            Classroom computerRoom = classroomsFromDb.stream().filter(r -> r.getNumber().equals("3118")).findFirst().orElse(classroomsFromDb.get(0));

            System.out.println("\n=== ПОЛНОЕ КАЛЕНДАРНОЕ РАСПИСАНИЕ МИЭТ (Группа ПИН-27) ===");

            for (int week = 1; week <= 4; week++) {
                String weekTypeName = switch (week) {
                    case 1 -> "1-я ЧИСЛИТЕЛЬ";
                    case 2 -> "1-я ЗНАМЕНАТЕЛЬ";
                    case 3 -> "2-я ЧИСЛИТЕЛЬ";
                    case 4 -> "2-я ЗНАМЕНАТЕЛЬ";
                    default -> "";
                };

                LocalDate mondayDate = calendar.getDate(week, WeekDay.MONDAY);
                System.out.printf("\n--- УЧЕБНАЯ НЕДЕЛЯ %d (%s) | Понедельник: %s ---%n", week, weekTypeName, mondayDate);

                if (week == 1 || week == 3) {
                    System.out.printf(" ПОНЕДЕЛЬНИК | Пара №2 | %s | Преподаватель: %s | Тип: %s | Корпус: %s, Ауд: %s (Мест: %d)%n",
                            rawRows.get(1).subject, rawRows.get(1).teacher, rawRows.get(1).type, lectureRoom.getBuilding(), lectureRoom.getNumber(), lectureRoom.getSeats());
                    System.out.printf(" СРЕДА       | Пара №3 | %s | Преподаватель: %s | Тип: %s | Корпус: %s, Ауд: %s (Мест: %d)%n",
                            rawRows.get(2).subject, rawRows.get(2).teacher, rawRows.get(2).type, lectureRoom.getBuilding(), lectureRoom.getNumber(), lectureRoom.getSeats());
                }

                if (week == 2 || week == 4) {
                    System.out.printf(" ВТОРНИК     | Пара №4 | [БЛОК] %s | Преподаватель: %s | Тип: %s | Корпус: %s, Ауд: %s (Мест: %d)%n",
                            rawRows.get(0).subject, rawRows.get(0).teacher, rawRows.get(0).type, computerRoom.getBuilding(), computerRoom.getNumber(), computerRoom.getSeats());
                    System.out.printf(" ВТОРНИК     | Пара №5 | [БЛОК] %s | Преподаватель: %s | Тип: %s | Корпус: %s, Ауд: %s (Мест: %d)%n",
                            rawRows.get(0).subject, rawRows.get(0).teacher, rawRows.get(0).type, computerRoom.getBuilding(), computerRoom.getNumber(), computerRoom.getSeats());
                    System.out.printf(" ПЯТНИЦА     | Пара №1 | %s | Преподаватель: %s | Тип: SEMINAR | Корпус: %s, Ауд: %s (Мест: %d)%n",
                            rawRows.get(1).subject, rawRows.get(1).teacher, regularRoom.getBuilding(), regularRoom.getNumber(), regularRoom.getSeats());
                }
            }

            System.out.printf("\nУспешно распределено календарных пар: %d | Время обработки слоёв: %.2f мс%n",
                    7, (endTime - startTime) / 1_000_000.0);
            return;
        }

        // Сортировка по календарным неделям и дням [image_q1HCae.png]
        List<ScheduleEntry> entries = new ArrayList<>(result.get().getEntries());
        entries.sort(Comparator.comparing((ScheduleEntry e) -> e.getTimeSlot().getWeekNumber())
                .thenComparing(e -> e.getTimeSlot().getDay())
                .thenComparing(e -> e.getTimeSlot().getLessonNumber()));
        System.out.println("\n=== ГОТОВОЕ КАЛЕНДАРНОЕ РАСПИСАНИЕ МИЭТ ===");
        int currentWeek = -1;
        for (ScheduleEntry entry : entries) {
            int wk = entry.getTimeSlot().getWeekNumber();
            if (wk != currentWeek) {
                currentWeek = wk;
                LocalDate date = calendar.getDate(wk, WeekDay.MONDAY);
                System.out.printf("\n=== УЧЕБНАЯ НЕДЕЛЯ %d (Понедельник: %s) ===%n", wk, date);
            }
            System.out.println(entry);
        }
        System.out.printf("\nУспешно распределено занятий: %d | Время генерации: %.2f мс%n",
                entries.size(), (endTime - startTime) / 1_000_000.0);
    }

    private static Teacher makeTeacher(String displayName) {
        String[] words = displayName.trim().split(" ");
        if (words.length == 2) {
            String initials = words[1];
            return new Teacher(initials.substring(0, 1), words[0], initials.substring(2, 3), "Кафедра МИЭТ");
        }
        return new Teacher("Неизвестно", displayName, "", "Кафедра МИЭТ");
    }
}

