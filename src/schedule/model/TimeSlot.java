package schedule.model;

public class TimeSlot {
    private WeekDay day;
    private int lessonNumber;
    private WeekType weekType;

    public TimeSlot(WeekDay day, int lessonNumber, WeekType weekType) {
        this.day = day;
        this.lessonNumber = lessonNumber;
        this.weekType = weekType;
    }

    public WeekDay getDay(){
        return day;
    }

    public int getLessonNumber() {
        return lessonNumber;
    }

    public WeekType getWeekType() {
        return weekType;
    }

    public void setDay(WeekDay day) {
        this.day = day;
    }

    public void setLessonNumber(int lessonNumber) {
        this.lessonNumber = lessonNumber;
    }

    public void setWeekType(WeekType weekType) {
        this.weekType = weekType;
    }

    @Override
    public  String toString(){
        return day + "," + lessonNumber + "," + weekType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        TimeSlot timeSlot = (TimeSlot) o;

        return lessonNumber == timeSlot.lessonNumber
                && day == timeSlot.day
                && weekType == timeSlot.weekType;
    }

    @Override
    public int hashCode() {
        int result = day.hashCode();
        result = 31 * result + lessonNumber;
        result = 31 * result + weekType.hashCode();
        return result;
    }
}