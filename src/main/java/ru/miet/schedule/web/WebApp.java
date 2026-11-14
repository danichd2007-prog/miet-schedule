package ru.miet.schedule.web;

import ru.miet.schedule.db.DbConfig;

import java.io.IOException;

/**
 * Запуск сайта: Run 'WebApp.main()', затем открыть http://localhost:8080 в браузере.
 *
 * Аргументы (необязательны): port=8080, semester=1.
 * База берётся из database.properties; её нужно один раз подготовить запуском DbDemo init.
 */
public final class WebApp {

    private WebApp() {
    }

    public static void main(String[] args) throws IOException {
        ru.miet.schedule.config.AppConfig app = ru.miet.schedule.config.AppConfig.defaultsFromFile();
        int port = app.webPort();
        long semester = app.semesterId();

        for (String arg : args) {
            if (arg.startsWith("port=")) {
                port = Integer.parseInt(arg.substring("port=".length()));
            } else if (arg.startsWith("semester=")) {
                semester = Long.parseLong(arg.substring("semester=".length()));
            } else {
                System.out.println("Неизвестный аргумент: " + arg);
            }
        }

        DbConfig config = DbConfig.fromClasspath();
        ScheduleController controller = new ScheduleController(new ScheduleService(config, semester, app.solver()));

        // 8080 часто занят другими программами: пробуем следующие порты
        com.sun.net.httpserver.HttpServer server = null;

        for (int candidate = port; server == null && candidate < port + 10; candidate++) {
            try {
                server = WebServer.start(candidate, controller);
            } catch (java.net.BindException e) {
                System.out.println("=== Порт " + candidate + " занят, пробую следующий");
            }
        }

        if (server == null) {
            System.out.println("=== Не нашёл свободный порт в диапазоне " + port + "-" + (port + 9)
                    + ". Укажите другой аргументом port=...");
            return;
        }

        System.out.println("=== База: " + config.url());
        System.out.println("=== Сайт запущен: http://localhost:" + server.getAddress().getPort());
        System.out.println("=== Остановить: красная кнопка Stop в окне Run");
    }
}
