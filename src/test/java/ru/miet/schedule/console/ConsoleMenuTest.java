package ru.miet.schedule.console;

import org.junit.jupiter.api.Test;
import ru.miet.schedule.config.AppConfig;
import ru.miet.schedule.db.DbConfig;
import ru.miet.schedule.db.ScheduleWriter;
import ru.miet.schedule.db.SchemaInstaller;
import ru.miet.schedule.web.ScheduleService;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Меню с заранее заданным вводом и настоящей базой. Без базы тест пропускается. */
class ConsoleMenuTest {

    private static String run(ScheduleService service, AppConfig config, String input) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        new ConsoleMenu(service, config, new BufferedReader(new StringReader(input)), out).run();
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    void exitAndUnknownItemNeedNoDatabase() throws Exception {
        String text = run(null, AppConfig.defaults(), "42\n0\n");
        assertTrue(text.contains("Нет такого пункта"));
        assertTrue(text.contains("До свидания"));

        // конец ввода тоже завершает работу
        assertTrue(run(null, AppConfig.defaults(), "").contains("До свидания"));
        assertTrue(run(null, AppConfig.defaults(), "9\n0\n").contains("максимум пар в день: 6"));
    }

    @Test
    void scriptedSessionWorksEndToEnd() throws Exception {
        DbConfig db = DbConfig.fromClasspath();

        try (Connection c = db.connect()) {
            SchemaInstaller.install(c);
            c.commit();
        } catch (SQLException e) {
            assumeTrue(false, "PostgreSQL недоступен: " + e.getMessage());
        }

        Path dir = Files.createTempDirectory("export");
        Properties p = new Properties();
        p.setProperty("export.directory", dir.toString());
        AppConfig config = AppConfig.of(p);
        ScheduleService service = new ScheduleService(db, config.semesterId(), config.solver());

        long before = service.runs().stream().mapToLong(r -> r.id()).max().orElse(0);

        try {
            String text = run(service, config, String.join("\n",
                    "1",                  // составить
                    "2",                  // версии
                    "3", "", "ПИН-2", "н", "ПИН-27", "1", // группа, неполное название с отказом, затем полное, последняя версия, неделя 1
                    "4", "", "Гужов", "1",
                    "5", "", "zzzzzz", "",   // аудитории нет, потом отмена
                    "6", "", "ПИН-27", "1", "1", "1", "1", // перенос: занятие 1, одна неделя, место 1
                    "7", "",
                    "8", "",
                    "3", "abc",            // версия не число
                    "0", ""));

            assertTrue(text.contains("Готово: версия №"), text);
            assertTrue(text.contains("ПИН-27"), text);
            assertTrue(text.contains("ничего не найдено"), text);
            assertTrue(text.contains("Перенесено занятий: 1"), text);
            assertTrue(text.contains("Сохранено:"), text);
            assertTrue(text.contains("подходит только: ПИН-27"), text);
            assertTrue(text.contains("окон у студентов"), text);
            assertTrue(text.contains("Нужно число"), text);

            List<Path> files;

            try (var s = Files.list(dir)) {
                files = s.toList();
            }

            assertEquals(1, files.size());
            assertTrue(Files.readString(files.get(0)).startsWith("﻿Неделя;Дата"));
        } finally {
            try (Connection c = db.connect()) {
                for (var r : service.runs()) {
                    if (r.id() > before) {
                        ScheduleWriter.deleteRun(c, r.id());
                    }
                }

                c.commit();
            }

            try (var s = Files.list(dir)) {
                for (Path f : s.toList()) {
                    Files.delete(f);
                }
            }

            Files.delete(dir);
        }
    }
}
