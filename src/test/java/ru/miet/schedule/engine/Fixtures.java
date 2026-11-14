package ru.miet.schedule.engine;

import ru.miet.schedule.domain.AcademicCalendar;
import ru.miet.schedule.domain.Classroom;
import ru.miet.schedule.domain.ClassroomType;
import ru.miet.schedule.domain.Direction;
import ru.miet.schedule.domain.LectureStream;
import ru.miet.schedule.domain.LessonType;
import ru.miet.schedule.domain.StudentGroup;
import ru.miet.schedule.domain.Subject;
import ru.miet.schedule.domain.SubjectToGroup;
import ru.miet.schedule.domain.Teacher;
import ru.miet.schedule.domain.TeacherCategory;
import ru.miet.schedule.domain.TeacherSubject;
import ru.miet.schedule.domain.TeachingRequirement;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicLong;

/** Мелкие заготовки для тестов движка. Все сущности получают id, как после загрузки из БД. */
public final class Fixtures {

    public static final AcademicCalendar CALENDAR =
            new AcademicCalendar(LocalDate.of(2026, 8, 31), 16);

    private static final AtomicLong IDS = new AtomicLong(1);
    private static final Direction DIRECTION = withId(new Direction("Тест", "ТСТ"));

    private Fixtures() {
    }

    private static <T extends ru.miet.schedule.domain.Entity> T withId(T entity) {
        entity.setId(IDS.getAndIncrement());
        return entity;
    }

    public static StudentGroup group(String name, int students) {
        return withId(new StudentGroup(name, 2, DIRECTION, students));
    }

    public static Teacher teacher(String lastName) {
        return teacher(lastName, TeacherCategory.STAFF);
    }

    public static Teacher teacher(String lastName, TeacherCategory category) {
        return withId(new Teacher("И.", lastName, "О.", "Кафедра", category));
    }

    public static Subject subject(String name) {
        return withId(new Subject(name));
    }

    public static TeacherSubject qualification(Teacher teacher, Subject subject) {
        return withId(new TeacherSubject(
                teacher, subject,
                EnumSet.of(LessonType.LECTURE, LessonType.SEMINAR, LessonType.LABORATORY)));
    }

    public static Classroom room(String number, ClassroomType type, int seats) {
        return withId(new Classroom(number, "Корпус 1", type, seats));
    }

    public static TeachingRequirement seminar(
            TeacherSubject qualification, StudentGroup group, int pairs) {

        return withId(TeachingRequirement.forGroup(
                qualification,
                withId(new SubjectToGroup(qualification.getSubject(), group)),
                LessonType.SEMINAR, pairs, ClassroomType.REGULAR, false));
    }

    public static TeachingRequirement lab(
            TeacherSubject qualification, StudentGroup group, int pairs, ClassroomType room) {

        return withId(TeachingRequirement.forGroup(
                qualification,
                withId(new SubjectToGroup(qualification.getSubject(), group)),
                LessonType.LABORATORY, pairs, room, true));
    }

    public static TeachingRequirement lecture(
            TeacherSubject qualification, int pairs, StudentGroup... groups) {

        LectureStream stream = withId(new LectureStream(
                "Поток " + qualification.getSubject().getName(), qualification.getSubject()));

        for (StudentGroup group : groups) {
            stream.addGroup(group);
        }

        return withId(TeachingRequirement.forLecture(
                qualification, stream, pairs, ClassroomType.LECTURE, false));
    }
}
