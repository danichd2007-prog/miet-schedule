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
}
