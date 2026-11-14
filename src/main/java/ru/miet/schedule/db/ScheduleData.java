package ru.miet.schedule.db;

import ru.miet.schedule.domain.AcademicCalendar;
import ru.miet.schedule.domain.Classroom;
import ru.miet.schedule.domain.StudentGroup;
import ru.miet.schedule.domain.TeacherAvailability;
import ru.miet.schedule.domain.TeachingRequirement;

import java.util.List;

/**
 * Всё, что нужно алгоритму для одного семестра, прочитанное из базы.
 *
 * @param warnings записи, которые пришлось пропустить, с объяснением причины
 *                 (например, преподаватель не может вести такой тип занятий)
 */
public record ScheduleData(
        long semesterId,
        String semesterName,
        AcademicCalendar calendar,
        List<StudentGroup> groups,
        List<Classroom> rooms,
        List<TeachingRequirement> requirements,
        List<TeacherAvailability> availability,
        List<String> warnings) {
}
