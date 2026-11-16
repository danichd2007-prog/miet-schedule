package ru.miet.schedule.config;

import ru.miet.schedule.engine.solver.SolverConfig;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Настройки приложения из файла app.properties: параметры алгоритма, номер семестра,
 * порт сайта, папка для экспорта. Каждый параметр необязателен, пропущенный берёт значение
 * по умолчанию. Подключение к базе настраивается отдельно, в database.properties.
 *
 * Порядок чтения: сначала app.properties из ресурсов проекта, затем (если указан) внешний файл,
 * значения которого перекрывают встроенные. Так можно менять настройки, не пересобирая проект.
 */
public record AppConfig(SolverConfig solver, long semesterId, int webPort, Path exportDirectory) {

    public static final String RESOURCE = "/app.properties";

    public static AppConfig defaults() {
        return of(new Properties());
    }

    /** Настройки из app.properties проекта; при ошибке чтения берутся значения по умолчанию. */
    public static AppConfig defaultsFromFile() {
        try {
            return load(null);
        } catch (IOException e) {
            return defaults();
        }
    }

    /** @param externalFile внешний файл настроек или null */
    public static AppConfig load(Path externalFile) throws IOException {
        Properties properties = new Properties();

        try (InputStream in = AppConfig.class.getResourceAsStream(RESOURCE)) {
            if (in != null) {
                try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    properties.load(reader);
                }
            }
        }

        if (externalFile != null) {
            if (!Files.isRegularFile(externalFile)) {
                throw new IllegalArgumentException("Файл настроек не найден: " + externalFile);
            }

            try (Reader reader = Files.newBufferedReader(externalFile, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        }

        return of(properties);
    }

    public static AppConfig of(Properties p) {
        SolverConfig d = SolverConfig.defaults();

        try {
            SolverConfig solver = new SolverConfig(
                    integer(p, "solver.maxPairsPerDay", d.maxPairsPerDay()),
                    real(p, "solver.weight.studentWindow", d.studentWindow()),
                    real(p, "solver.weight.teacherWindow", d.teacherWindow()),
                    real(p, "solver.weight.latePair", d.latePair()),
                    real(p, "solver.weight.saturday", d.saturday()),
                    real(p, "solver.weight.roomSlack", d.roomSlack()),
                    real(p, "solver.weight.dayLoad", d.dayLoad()),
                    integer(p, "solver.repairRounds", d.repairRounds()),
                    integer(p, "solver.maxEjectionsPerSeries", d.maxEjectionsPerSeries()),
                    integer(p, "solver.timeLimitSeconds", (int) (d.timeLimitMillis() / 1000)) * 1000L);

            int port = integer(p, "web.port", 8080);

            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Параметр web.port: порт должен быть от 1 до 65535");
            }

            long semester = integer(p, "semester.id", 1);

            if (semester < 1) {
                throw new IllegalArgumentException("Параметр semester.id: номер семестра должен быть положительным");
            }

            return new AppConfig(solver, semester, port,
                    Path.of(p.getProperty("export.directory", ".").trim()));
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("Параметр")) {
                throw e;
            }

            throw new IllegalArgumentException("Неверные параметры алгоритма в app.properties: " + e.getMessage());
        }
    }

    private static int integer(Properties p, String key, int fallback) {
        String text = p.getProperty(key);

        if (text == null || text.isBlank()) {
            return fallback;
        }

        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Параметр " + key + ": «" + text.trim() + "» не целое число");
        }
    }

    private static double real(Properties p, String key, double fallback) {
        String text = p.getProperty(key);

        if (text == null || text.isBlank()) {
            return fallback;
        }

        try {
            return Double.parseDouble(text.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Параметр " + key + ": «" + text.trim() + "» не число");
        }
    }
}
