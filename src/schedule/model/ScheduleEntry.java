package schedule.model;

public class ScheduleEntry {
    private Lesson lesson;
    private TimeSlot timeSlot;
    private Classroom classroom;

    public ScheduleEntry(Lesson lesson, TimeSlot timeSlot, Classroom classroom) {
        this.lesson = lesson;
        this.timeSlot = timeSlot;
        this.classroom = classroom;
    }

    public Lesson getLesson() { return lesson; }
    public TimeSlot getTimeSlot() { return timeSlot; }
    public Classroom getClassroom() { return classroom; }

    public void setLesson(Lesson lesson) {
        this.lesson = lesson;
    }

    public void setTimeSlot(TimeSlot timeSlot) {
        this.timeSlot = timeSlot;
    }

    public void setClassroom(Classroom classroom) {
        this.classroom = classroom;
    }

    @Override
    public String toString() {
        return lesson.getSubject().getName() + " | " + lesson.getTeacher().getShortName() +
                " | " + lesson.getGroup().getName() + " | " + timeSlot + " | " + classroom.getNumber();
    }
}