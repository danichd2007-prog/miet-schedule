package schedule.constraint;

import schedule.model.Classroom;
import schedule.model.ClassroomType;
import schedule.model.Lesson;
import schedule.model.Schedule;
import schedule.model.ScheduleEntry;

public class ClassroomTypeConstraint implements ScheduleConstraint {

    @Override
    public boolean isSatisfied(Schedule schedule, ScheduleEntry entry) {
        Lesson lesson = entry.getLesson();
        Classroom classroom = entry.getClassroom();

        ClassroomType requiredType = lesson.getRequiredRoomType();
        ClassroomType actualType = classroom.getType();

        return requiredType == actualType;
    }

    @Override
    public String getErrorMessage() {
        return "Тип аудитории не соответствует типу занятия";
    }
}