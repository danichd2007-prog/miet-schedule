package ru.miet.schedule.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Применяет db/schema.sql из ресурсов проекта: таблицы, справочники и тестовые данные.
 * Скрипт написан так, что его можно запускать повторно: существующее не ломается.
 * Фиксацию транзакции выполняет вызывающий код.
 */
public final class SchemaInstaller {

    public static final String RESOURCE = "/db/schema.sql";

    private SchemaInstaller() {
    }

    public static void install(Connection connection) throws SQLException {
        String script = readScript();

        try (Statement statement = connection.createStatement()) {
            statement.execute(script);
        }
    }

    static String readScript() {
        try (InputStream in = SchemaInstaller.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Не найден ресурс " + RESOURCE);
            }

            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);

            // BOM в начале файла PostgreSQL воспринимает как мусор
            return text.startsWith("﻿") ? text.substring(1) : text;
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось прочитать " + RESOURCE, e);
        }
    }
}
