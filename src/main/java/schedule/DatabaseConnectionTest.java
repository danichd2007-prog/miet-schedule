package schedule;

import schedule.util.DatabaseConfig;

import java.sql.Connection;

public class DatabaseConnectionTest {

    public static void main(String[] args) {
        try {
            Connection connection =
                    DatabaseConfig.getConnection();

            System.out.println("Подключение к PostgreSQL успешно!");
            System.out.println("База данных: " + connection.getCatalog());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}