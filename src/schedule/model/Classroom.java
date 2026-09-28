package schedule.model;

public class Classroom {
    private String number;
    private ClassroomType type;

    public Classroom(String number, ClassroomType type){
        this.number = number;
        this.type = type;
    }

    public String getNumber(){
        return number;
    }

    public ClassroomType getType(){
        return type;
    }

    public void setNumber(String number){
        this.number=number;
    }

    public void setType(ClassroomType type){
        this.type = type;
    }

    @Override
    public String toString() {
        return "Аудитория " + number + " (" + type + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Classroom classroom = (Classroom) o;

        return number.equals(classroom.number);
    }

    @Override
    public int hashCode() {
        return number.hashCode();
    }
}
