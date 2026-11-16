package ru.miet.schedule.db;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Параметры подключения к PostgreSQL.
 *
 * Берутся из файла database.properties (ключи db.url, db.user, db.password).
 * Системные свойства -Ddb.url=... -Ddb.user=... -Ddb.password=... перекрывают файл,
 * это удобно для тестов и для запуска на другой базе без правки файла.
 */
public record DbConfig(String url, String user, String password) {

    public static final String RESOURCE = "/database.properties";

    public static DbConfig fromClasspath() {
        Properties properties = new Properties();

        try (InputStream in = DbConfig.class.getResourceAsStream(RESOURCE)) {
            if (in != null) {
                try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    properties.load(reader);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось прочитать " + RESOURCE, e);
        }

        return of(properties);
    }

    public static DbConfig of(Properties properties) {
        String url = System.getProperty("db.url", properties.getProperty("db.url"));
        String user = System.getProperty("db.user", properties.getProperty("db.user"));
        String password = System.getProperty("db.password", properties.getProperty("db.password", ""));

        if (url == null || url.isBlank()) {
            throw new IllegalStateException(
                    "Не задан db.url: проверьте src/main/resources/database.properties");
        }

        return new DbConfig(url.trim(), user == null ? null : user.trim(), password);
    }

    /** Новое соединение. Автофиксация выключена: транзакцией управляет вызывающий код. */
    public Connection connect() throws SQLException {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException(
                    "Не удалось загрузить JDBC-драйвер PostgreSQL", e
            );
        }

        Connection connection =
                DriverManager.getConnection(url, user, password);

        connection.setAutoCommit(false);
        return connection;
    }

    /**
     * Создаёт базу данных из db.url, если её ещё нет. Для этого подключается к служебной
     * базе postgres на том же сервере. Возвращает true, если база была создана сейчас.
     */
    public boolean createDatabaseIfMissing() throws SQLException {
        String prefix = "jdbc:postgresql://";

        if (!url.startsWith(prefix)) {
            throw new IllegalStateException("Ожидался адрес вида jdbc:postgresql://host:port/имя");
        }

        String rest = url.substring(prefix.length());
        int slash = rest.indexOf('/');

        if (slash < 0) {
            throw new IllegalStateException("В db.url не указано имя базы");
        }

        String hostPort = rest.substring(0, slash);
        String name = rest.substring(slash + 1);
        int query = name.indexOf('?');

        if (query >= 0) {
            name = name.substring(0, query);
        }

        if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalStateException(
                    "Имя базы «" + name + "» нельзя создать автоматически: используйте латиницу, цифры и _");
        }

        try (Connection admin = DriverManager.getConnection(
                prefix + hostPort + "/postgres", user, password)) {
            admin.setAutoCommit(true);

            try (var check = admin.prepareStatement("SELECT 1 FROM pg_database WHERE datname = ?")) {
                check.setString(1, name);

                try (var rs = check.executeQuery()) {
                    if (rs.next()) {
                        return false;
                    }
                }
            }

            try (var create = admin.createStatement()) {
                create.executeUpdate("CREATE DATABASE " + name);
                return true;
            }
        }
    }
}
