package ru.miet.schedule.console;

import ru.miet.schedule.config.AppConfig;
import ru.miet.schedule.db.DbConfig;
import ru.miet.schedule.web.ScheduleService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Запуск консольного меню: Run 'ConsoleApp.main()'.
 * Аргумент config=путь к файлу настроек (необязателен) перекрывает app.properties из проекта.
 * База берётся из database.properties.
 */
public final class ConsoleApp {

    private ConsoleApp() {
    }

    public static void main(String[] args) throws IOException {
        Path external = null;

        for (String arg : args) {
            if (arg.startsWith("config=")) {
                external = Path.of(arg.substring("config=".length()));
            } else {
                System.out.println("Неизвестный аргумент: " + arg);
            }
        }

        AppConfig config;

        try {
            config = AppConfig.load(external);
        } catch (IllegalArgumentException e) {
            System.out.println("Ошибка настроек: " + e.getMessage());
            return;
        }

        ScheduleService service = new ScheduleService(
                DbConfig.fromClasspath(), config.semesterId(), config.solver());

        // Консоль IDEA передаёт ввод в UTF-8, а свойство stdin.encoding на Windows всегда равно cp1251,
        // из-за чего русские буквы ломаются. Поэтому читаем UTF-8; другая кодировка задаётся
        // свойством -Dmiet.input.encoding=Cp866 (например, для запуска из cmd.exe).
        Charset charset = StandardCharsets.UTF_8;
        String name = System.getProperty("miet.input.encoding");

        if (name != null && Charset.isSupported(name)) {
            charset = Charset.forName(name);
        }

        new ConsoleMenu(service, config,
                new BufferedReader(new InputStreamReader(System.in, charset)), System.out).run();
    }
}
