package ru.miet.schedule.domain;

import java.util.Objects;

/**
 * Преподаватель. Порядок аргументов конструктора сохранён из прежнего кода:
 * (firstName, lastName, middleName, department).
 */
public final class Teacher extends Entity {

    private final String firstName;
    private final String lastName;
    private final String middleName;
    private final String department;
    private final TeacherCategory category;

    public Teacher(
            String firstName,
            String lastName,
            String middleName,
            String department) {

        this(firstName, lastName, middleName, department,
                TeacherCategory.STAFF);
    }

    public Teacher(
            String firstName,
            String lastName,
            String middleName,
            String department,
            TeacherCategory category) {

        this.firstName = requireText(firstName, "First name");
        this.lastName = requireText(lastName, "Last name");
        this.middleName = middleName == null ? "" : middleName.trim();
        this.department = requireText(department, "Department");
        this.category = Objects.requireNonNull(
                category, "Category cannot be null");
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getMiddleName() {
        return middleName;
    }

    public String getDepartment() {
        return department;
    }

    public TeacherCategory getCategory() {
        return category;
    }

    public String getFullName() {
        return lastName + " " + firstName
                + (middleName.isEmpty() ? "" : " " + middleName);
    }

    public String getShortName() {
        String result = lastName + " " + firstName.charAt(0) + ".";

        if (!middleName.isEmpty()) {
            result += middleName.charAt(0) + ".";
        }

        return result;
    }

    @Override
    public String toString() {
        return "Преподаватель: " + getFullName();
    }
}
