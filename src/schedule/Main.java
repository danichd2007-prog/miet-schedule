package schedule;

import schedule.constraint.ClassroomConflictConstraint;
import schedule.constraint.ClassroomTypeConstraint;
import schedule.constraint.ScheduleConstraint;
import schedule.constraint.StudentGroupConflictConstraint;
import schedule.constraint.TeacherConflictConstraint;
import schedule.controller.ScheduleController;
import schedule.model.Classroom;
import schedule.model.ClassroomType;
import schedule.model.Lesson;
import schedule.model.Schedule;
import schedule.model.ScheduleEntry;
import schedule.model.StudentGroup;
import schedule.model.Subject;
import schedule.model.Teacher;
import schedule.model.TimeSlot;
import schedule.model.WeekDay;
import schedule.model.WeekType;
import schedule.view.ConsoleView;

public class Main {

    public static void main(String[] args) {

        // Преподаватели

        Teacher ivanov = new Teacher(
                "Иван",
                "Иванов",
                "Иванович",
                "Кафедра СПИН-ТЕХ"
        );

        Teacher petrov = new Teacher(
                "Пётр",
                "Петров",
                "Петрович",
                "Кафедра ВМ1"
        );


        // Группы

        StudentGroup group1 =
                new StudentGroup("ПИН-21", 2);

        StudentGroup group2 =
                new StudentGroup("ПИН-22", 2);


        // Предметы

        Subject java =
                new Subject("Java");

        Subject math =
                new Subject("Матанализ");

        Subject algorithms =
                new Subject("Алгоритмы");


        // Аудитории

        Classroom computerRoom =
                new Classroom(
                        "3205",
                        ClassroomType.COMPUTER
                );

        Classroom lectureRoom =
                new Classroom(
                        "3101",
                        ClassroomType.LECTURE
                );

        Classroom secondComputerRoom =
                new Classroom(
                        "3206",
                        ClassroomType.COMPUTER
                );


        // Время

        TimeSlot mondaySecond =
                new TimeSlot(
                        WeekDay.MONDAY,
                        2,
                        WeekType.F_NUMERATOR
                );

        TimeSlot mondayThird =
                new TimeSlot(
                        WeekDay.MONDAY,
                        3,
                        WeekType.F_NUMERATOR
                );


        // Занятия

        Lesson javaLesson = new Lesson(
                java,
                ivanov,
                group1,
                ClassroomType.COMPUTER
        );

        Lesson mathLesson = new Lesson(
                math,
                petrov,
                group2,
                ClassroomType.LECTURE
        );

        Lesson algorithmsLesson = new Lesson(
                algorithms,
                ivanov,
                group2,
                ClassroomType.COMPUTER
        );


        // Расписание

        Schedule schedule = new Schedule();


        // Ограничения

        ScheduleConstraint[] constraints = {
                new ClassroomTypeConstraint(),
                new ClassroomConflictConstraint(),
                new TeacherConflictConstraint(),
                new StudentGroupConflictConstraint()
        };


        // MVC

        ScheduleController controller =
                new ScheduleController(schedule, constraints);

        ConsoleView view = new ConsoleView();


        // =========================================
        // ТЕСТ 1
        // Корректная запись
        // =========================================

        ScheduleEntry entry1 = new ScheduleEntry(
                javaLesson,
                mondaySecond,
                computerRoom
        );

        boolean result1 = controller.addEntry(entry1);

        view.showAddResult(
                "Тест 1: корректная запись",
                result1
        );


        // =========================================
        // ТЕСТ 2
        // Неправильный тип аудитории
        // Java требует COMPUTER,
        // а пытаемся поставить в LECTURE
        // =========================================

        ScheduleEntry entry2 = new ScheduleEntry(
                javaLesson,
                mondayThird,
                lectureRoom
        );

        boolean result2 = controller.addEntry(entry2);

        view.showAddResult(
                "Тест 2: неправильный тип аудитории",
                result2
        );


        // =========================================
        // ТЕСТ 3
        // Аудитория уже занята
        //
        // 3205 уже используется entry1
        // в понедельник на 2 паре
        // =========================================

        ScheduleEntry entry3 = new ScheduleEntry(
                algorithmsLesson,
                mondaySecond,
                computerRoom
        );

        boolean result3 = controller.addEntry(entry3);

        view.showAddResult(
                "Тест 3: аудитория занята",
                result3
        );


        // =========================================
        // ТЕСТ 4
        // Преподаватель уже занят
        //
        // Иванов ведёт Java на 2 паре.
        // Попытаемся поставить ему Algorithms
        // в другую аудиторию в то же время.
        // =========================================

        ScheduleEntry entry4 = new ScheduleEntry(
                algorithmsLesson,
                mondaySecond,
                secondComputerRoom
        );

        boolean result4 = controller.addEntry(entry4);

        view.showAddResult(
                "Тест 4: преподаватель занят",
                result4
        );


        // =========================================
        // ТЕСТ 5
        // Корректная вторая запись
        // =========================================

        ScheduleEntry entry5 = new ScheduleEntry(
                mathLesson,
                mondayThird,
                lectureRoom
        );

        boolean result5 = controller.addEntry(entry5);

        view.showAddResult(
                "Тест 5: корректная вторая запись",
                result5
        );


        // =========================================
        // ТЕСТ 6
        // Группа уже занята
        //
        // group2 уже находится на математике
        // на 3 паре.
        // =========================================

        Lesson groupConflictLesson = new Lesson(
                algorithms,
                ivanov,
                group2,
                ClassroomType.COMPUTER
        );

        ScheduleEntry entry6 = new ScheduleEntry(
                groupConflictLesson,
                mondayThird,
                secondComputerRoom
        );

        boolean result6 = controller.addEntry(entry6);

        view.showAddResult(
                "Тест 6: группа занята",
                result6
        );


        // Показываем результат

        view.showSchedule(controller.getSchedule());
    }
}