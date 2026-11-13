package ru.miet.schedule.domain;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeachingRequirementTest {

    private final Direction direction = new Direction("Программная инженерия", "ПИН");
    private final StudentGroup group = new StudentGroup("ПИН-27", 2, direction, 25);
    private final Subject subject = new Subject("Алгоритмы и структуры данных");
    private final Teacher teacher = new Teacher("С.", "Лупин", "С.", "Кафедра МИЭТ");

    private TeacherSubject qualification(LessonType... types) {
        return new TeacherSubject(teacher, subject, EnumSet.copyOf(java.util.List.of(types)));
    }

    private LectureStream stream() {
        LectureStream stream = new LectureStream("Поток ПИН-27", subject);
        stream.addGroup(group);
        return stream;
    }

    @Test
    void lectureForStreamIsValid() {
        TeachingRequirement requirement = TeachingRequirement.forLecture(
                qualification(LessonType.LECTURE), stream(), 16,
                ClassroomType.LECTURE, false);

        assertEquals(16, requirement.lessonCount());
        assertEquals(1, requirement.lessonDuration());
        assertEquals(25, requirement.studentCount());
        assertTrue(requirement.isStreamLecture());
    }

    @Test
    void blockHasDurationTwoAndHalfAsManyLessons() {
        TeachingRequirement requirement = TeachingRequirement.forGroup(
                qualification(LessonType.LABORATORY),
                new SubjectToGroup(subject, group),
                LessonType.LABORATORY, 8, ClassroomType.COMPUTER, true);

        assertEquals(2, requirement.lessonDuration());
        assertEquals(4, requirement.lessonCount());
        assertFalse(requirement.isStreamLecture());
    }

    @Test
    void oddBlockIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> TeachingRequirement.forGroup(
                        qualification(LessonType.LABORATORY),
                        new SubjectToGroup(subject, group),
                        LessonType.LABORATORY, 7, ClassroomType.COMPUTER, true));
    }

    @Test
    void lectureWithoutStreamRulesAreEnforced() {
        // лекция обязана идти через forLecture, а не forGroup
        assertThrows(IllegalArgumentException.class,
                () -> TeachingRequirement.forGroup(
                        qualification(LessonType.LECTURE),
                        new SubjectToGroup(subject, group),
                        LessonType.LECTURE, 16, ClassroomType.LECTURE, false));
    }

    @Test
    void teacherMustBeAllowedToTeachThisType() {
        assertThrows(IllegalArgumentException.class,
                () -> TeachingRequirement.forLecture(
                        qualification(LessonType.SEMINAR), stream(), 16,
                        ClassroomType.LECTURE, false));
    }

    @Test
    void subjectOfStreamMustMatchQualification() {
        Subject other = new Subject("Физика");
        LectureStream wrongStream = new LectureStream("Поток", other);
        wrongStream.addGroup(group);

        assertThrows(IllegalArgumentException.class,
                () -> TeachingRequirement.forLecture(
                        qualification(LessonType.LECTURE), wrongStream, 16,
                        ClassroomType.LECTURE, false));
    }

    @Test
    void entitiesAreEqualByIdOnly() {
        Teacher first = new Teacher("А.", "Иванов", "А.", "Кафедра");
        Teacher second = new Teacher("А.", "Иванов", "А.", "Кафедра");

        assertNotEquals(first, second); // без id равны только сами себе

        first.setId(7L);
        second.setId(7L);
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());

        Teacher third = new Teacher("А.", "Иванов", "А.", "Кафедра");
        third.setId(8L);
        assertNotEquals(first, third);
    }

    @Test
    void idCannotBeChangedOrBeInvalid() {
        Teacher t = new Teacher("А.", "Иванов", "А.", "Кафедра");
        t.setId(1L);

        assertThrows(IllegalStateException.class, () -> t.setId(2L));
        assertThrows(IllegalArgumentException.class,
                () -> new Teacher("Б.", "Петров", "Б.", "Кафедра").setId(0L));
    }

    @Test
    void shortNameAndCategory() {
        assertEquals("Лупин С.С.", teacher.getShortName());
        assertEquals(TeacherCategory.STAFF, teacher.getCategory());

        Teacher partTime = new Teacher("М.", "Камолов", "М.", "Кафедра",
                TeacherCategory.PART_TIME);

        assertTrue(partTime.getCategory().priority() < teacher.getCategory().priority());
        assertTrue(partTime.getCategory().availabilityIsHard());
    }
}
