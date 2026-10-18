package schedule.model;

import java.util.Objects;

public abstract class Person {
    private Long id;
    private String firstName;
    private String lastName;
    private String middleName;

    public Person(String firstName, String lastName, String middleName) {
        setFirstName(firstName);
        setLastName(lastName);
        setMiddleName(middleName);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("ID must be positive");
        }

        if (this.id != null && !this.id.equals(id)) {
            throw new IllegalStateException("ID cannot be changed");
        }

        this.id = id;
    }

    public String getFullName() {
        return lastName + " " + firstName +
                (middleName.isEmpty() ? "" : " " + middleName);
    }

    public String getShortName() {
        String result = lastName + " " + firstName.charAt(0) + ".";

        if (!middleName.isEmpty()) {
            result += middleName.charAt(0) + ".";
        }

        return result;
    }

    public abstract String getRole();

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getMiddleName() {
        return middleName;
    }

    public void setFirstName(String firstName) {
        this.firstName = requireText(firstName, "First name");
    }

    public void setLastName(String lastName) {
        this.lastName = requireText(lastName, "Last name");
    }

    public void setMiddleName(String middleName) {
        this.middleName = middleName == null ? "" : middleName.trim();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be empty");
        }

        return value.trim();
    }

    @Override
    public String toString() {
        return getRole() + ": " + getFullName();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Person person = (Person) o;

        return id != null && id.equals(person.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
