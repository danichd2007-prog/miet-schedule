package ru.miet.schedule.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * Обёртка над встроенным в JDK HTTP-сервером: переводит HTTP в вызовы контроллера.
 * Слушает только локальный адрес (127.0.0.1): пока нет входа по паролю, наружу сайт не открываем.
 */
public final class WebServer {

    private WebServer() {
    }

    /** Запускает сервер. Порт 0 означает «любой свободный» (так удобно в тестах). */
    public static HttpServer start(int port, ScheduleController controller) throws IOException {
        HttpServer server = HttpServer.create(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);

        server.createContext("/", exchange -> serve(controller, exchange));
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();

        return server;
    }

    private static void serve(ScheduleController controller, HttpExchange exchange) throws IOException {
        try {
            String body = new String(exchange.getRequestBody().readNBytes(65536), StandardCharsets.UTF_8);

            String method = exchange.getRequestMethod();
            ScheduleController.Response response;

            if (method.equals("POST") && !sameOrigin(exchange)) {
                response = new ScheduleController.Response(
                        403, HtmlViews.error(403, "Запрос с чужого сайта отклонён"), null);
            } else {
                URI uri = exchange.getRequestURI();
                response = controller.handle(method, uri.getPath(), uri.getRawQuery(), body);
            }

            send(exchange, response);
        } catch (RuntimeException e) {
            System.err.println("Ошибка обработки " + exchange.getRequestURI() + ": " + e);
            send(exchange, new ScheduleController.Response(
                    500, HtmlViews.error(500, "Внутренняя ошибка. Подробности в консоли сервера."), null));
        } finally {
            exchange.close();
        }
    }

    /** Защита от отправки формы с постороннего сайта: Origin, если он есть, должен совпадать с Host. */
    private static boolean sameOrigin(HttpExchange exchange) {
        String origin = exchange.getRequestHeaders().getFirst("Origin");

        if (origin == null) {
            return true;
        }

        String host = exchange.getRequestHeaders().getFirst("Host");
        return host != null && origin.equals("http://" + host);
    }

    private static void send(HttpExchange exchange, ScheduleController.Response response) throws IOException {
        byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("Content-Security-Policy",
                "default-src 'none'; style-src 'unsafe-inline'; form-action 'self'");

        if (response.location() != null) {
            exchange.getResponseHeaders().set("Location", response.location());
        }

        exchange.sendResponseHeaders(response.status(), bytes.length == 0 ? -1 : bytes.length);

        if (bytes.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
    }
}
