package schedule.util;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;

public final class DatabaseConfig {
    private static Connection connection = null;

    private DatabaseConfig() {}

    public static Connection getConnection() {
        if (connection == null) {
            try (InputStream input = DatabaseConfig.class.getClassLoader().getResourceAsStream("database.properties")) {
                Properties prop = new Properties();
                if (input == null) {
                    throw new RuntimeException("Не найден файл database.properties");
                }
                prop.load(input);
                connection = DriverManager.getConnection(
                        prop.getProperty("db.url"),
                        prop.getProperty("db.user"),
                        prop.getProperty("db.password")
                );
            } catch (Exception e) {
                throw new RuntimeException("Ошибка подключения к СУБД", e);
            }
        }
        return connection;
    }
}
