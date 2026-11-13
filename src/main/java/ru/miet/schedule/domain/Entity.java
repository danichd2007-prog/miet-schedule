package ru.miet.schedule.domain;

/**
 * Базовый класс сущностей с идентификатором из БД.
 *
 * Равенство: две сущности равны, если один класс и одинаковый непустой id.
 * Сущности без id равны только сами себе. Объекты, загруженные из БД,
 * получают id сразу после создания (до попадания в хеш-коллекции),
 * поэтому hashCode остаётся стабильным.
 */
public abstract class Entity {

    private Long id;

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

    protected static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be empty");
        }

        return value.trim();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Entity that = (Entity) o;

        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : System.identityHashCode(this);
    }
}
