package schedule.repository;
import java.util.List;
public interface CrudRepository<T, ID> {
    void save(T entity);
    List<T> findAll();
}
