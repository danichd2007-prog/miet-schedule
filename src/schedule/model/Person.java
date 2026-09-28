package schedule.model;

public abstract class Person {
    private String firstName;
    private String lastName;
    private String middleName;

    public Person(String firstName, String lastName, String middleName) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.middleName = middleName;
    }

    public String getFullName() {
        return lastName + " " + firstName + " " + middleName;
    }

    public String getShortName() {
        return lastName + " " + firstName.charAt(0) + "." + middleName.charAt(0) + ".";
    }

    // Абстрактный метод — для демонстрации полиморфизма
    public abstract String getRole();

    // Геттеры
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getMiddleName() { return middleName; }

    // Сеттеры
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public void setMiddleName(String middleName) { this.middleName = middleName; }

    @Override
    public String toString() {
        return getRole() + ": " + getFullName();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Person person = (Person) o;
        return firstName.equals(person.firstName) &&
                lastName.equals(person.lastName) &&
                middleName.equals(person.middleName);
    }

    @Override
    public int hashCode() {
        return firstName.hashCode() * 31 * 31 + lastName.hashCode() * 31 + middleName.hashCode();
    }
}