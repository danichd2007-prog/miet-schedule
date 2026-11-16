package ru.miet.schedule.web;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import ru.miet.schedule.db.DbConfig;
import ru.miet.schedule.db.ScheduleWriter;
import ru.miet.schedule.db.SchemaInstaller;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Проверка сайта целиком: настоящий HTTP-сервер и настоящий PostgreSQL.
 * Без базы тест пропускается. Составленное в тесте расписание в конце удаляется.
 */
class WebIntegrationTest {

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER).build();

    private HttpResponse<String> send(HttpServer server, String method, String path, String... headers)
            throws Exception {

        HttpRequest.Builder request = HttpRequest.newBuilder(
                URI.create("http://localhost:" + server.getAddress().getPort() + path));

        if (method.equals("POST")) {
            request.POST(HttpRequest.BodyPublishers.noBody());
        } else {
            request.GET();
        }

        for (int i = 0; i + 1 < headers.length; i += 2) {
            request.header(headers[i], headers[i + 1]);
        }

        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> form(HttpServer server, String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + server.getAddress().getPort() + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();

        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void siteWorksEndToEnd() throws Exception {
        DbConfig config = DbConfig.fromClasspath();
        List<Long> createdRuns = new ArrayList<>();

        try (Connection c = config.connect()) {
            SchemaInstaller.install(c);
            c.commit();
        } catch (SQLException e) {
            assumeTrue(false, "PostgreSQL недоступен: " + e.getMessage());
        }

        ScheduleService service = new ScheduleService(config, 1);
        HttpServer server = WebServer.start(0, new ScheduleController(service));

        try {
            HttpResponse<String> home = send(server, "GET", "/");
            assertEquals(200, home.statusCode());
            assertTrue(home.body().contains("Расписание МИЭТ"));

            assertEquals(405, send(server, "GET", "/generate").statusCode());
            assertEquals(403, send(server, "POST", "/generate", "Origin", "http://evil.example").statusCode());
            assertEquals(404, send(server, "GET", "/nope").statusCode());
            assertEquals(404, send(server, "GET", "/run/abc").statusCode());
            assertEquals(404, send(server, "GET", "/run/999999999").statusCode());

            HttpResponse<String> generated = send(server, "POST", "/generate");
            assertEquals(303, generated.statusCode());

            String location = generated.headers().firstValue("Location").orElseThrow();
            assertTrue(location.matches("/run/\\d+"), location);
            createdRuns.add(Long.parseLong(location.substring("/run/".length())));

            HttpResponse<String> run = send(server, "GET", location);
            assertEquals(200, run.statusCode());
            assertTrue(run.body().contains("Удобство"));
            assertTrue(run.body().contains("ПИН-27"));

            String groupLink = firstLink(run.body(), location + "/group/");
            HttpResponse<String> group = send(server, "GET", groupLink);
            assertEquals(200, group.statusCode());
            assertTrue(group.body().contains("Понедельник"));

            assertEquals(200, send(server, "GET", firstLink(run.body(), location + "/teacher/")).statusCode());
            assertEquals(200, send(server, "GET", firstLink(run.body(), location + "/room/")).statusCode());
            assertEquals(404, send(server, "GET", location + "/group/999999999?week=1").statusCode());

            // неверная неделя не ломает страницу
            assertEquals(200, send(server, "GET", groupLink.replace("week=1", "week=999")).statusCode());
            assertEquals(200, send(server, "GET", groupLink.replace("week=1", "week=abc")).statusCode());

            HttpResponse<String> dashboard = send(server, "GET", location + "/dashboard");
            assertEquals(200, dashboard.statusCode());
            assertTrue(dashboard.body().contains("Дашборд расписания"));
            assertTrue(dashboard.body().contains("Сравнение версий"));
            assertEquals(404, send(server, "GET", "/run/999999999/dashboard").statusCode());
            assertEquals(405, send(server, "POST", location + "/dashboard").statusCode());

            // ---- ручной перенос ----
            String editLink = firstLink(group.body(), location + "/edit/");
            HttpResponse<String> editPage = send(server, "GET", editLink);
            assertEquals(200, editPage.statusCode());
            assertTrue(editPage.body().contains("Куда можно перенести"));

            String suggestion = unescape(firstLink(editPage.body(), editLink + "?scope=single"));
            HttpResponse<String> checked = send(server, "GET", suggestion);
            assertEquals(200, checked.statusCode());
            assertTrue(checked.body().contains("Нарушений нет"), "предложенное место должно проходить проверку");

            String query = suggestion.substring(suggestion.indexOf('?') + 1);
            HttpResponse<String> applied = form(server, editLink, query + "&action=apply");
            assertEquals(303, applied.statusCode());
            assertTrue(applied.headers().firstValue("Location").orElseThrow().contains("notice=moved"));

            // старая ссылка больше не указывает на занятие, новая место нашла
            assertEquals(404, send(server, "GET", editLink).statusCode());

            String[] parts = editLink.split("/");
            String movedEdit = String.join("/", java.util.Arrays.copyOf(parts, 5)) + "/"
                    + parts[5] + "/" + queryValue(query, "day2") + "/" + queryValue(query, "pair2");
            assertEquals(200, send(server, "GET", movedEdit).statusCode());

            // повторный перенос по устаревшей ссылке не портит данные
            assertEquals(404, form(server, editLink, query + "&action=apply").statusCode());

            // после переноса расписание из базы по-прежнему проходит независимую проверку
            var saved = service.run(createdRuns.get(0)).orElseThrow();
            var violations = ru.miet.schedule.engine.verify.ScheduleVerifier.verify(
                    saved.data().requirements(), saved.data().availability(), saved.data().calendar(),
                    saved.lessons(), ru.miet.schedule.engine.solver.SolverConfig.defaults().maxPairsPerDay(),
                    java.util.Set.of());
            assertTrue(violations.isEmpty(), () -> "После переноса: " + violations);

            // перенос с чужого сайта отклоняется
            HttpRequest evil = HttpRequest.newBuilder(URI.create(
                            "http://localhost:" + server.getAddress().getPort() + movedEdit))
                    .header("Origin", "http://evil.example")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(query + "&action=apply")).build();
            assertEquals(403, client.send(evil, HttpResponse.BodyHandlers.ofString()).statusCode());

            // копия расписания
            HttpResponse<String> copy = send(server, "POST", location + "/copy");
            assertEquals(303, copy.statusCode());
            String copyLocation = copy.headers().firstValue("Location").orElseThrow();
            createdRuns.add(Long.parseLong(copyLocation.substring("/run/".length(), copyLocation.indexOf('?'))));
            assertEquals(200, send(server, "GET", copyLocation).statusCode());
        } finally {
            server.stop(0);

            try (Connection c = config.connect()) {
                for (Long id : createdRuns) {
                    ScheduleWriter.deleteRun(c, id);
                }

                c.commit();
            }
        }
    }

    private static String unescape(String html) {
        return html.replace("&amp;", "&");
    }

    private static String queryValue(String query, String name) {
        for (String pair : query.split("&")) {
            if (pair.startsWith(name + "=")) {
                return pair.substring(name.length() + 1);
            }
        }

        throw new AssertionError("нет параметра " + name);
    }

    private static String firstLink(String html, String prefix) {
        int start = html.indexOf("href=\"" + prefix);
        assertTrue(start >= 0, "нет ссылки " + prefix);
        int from = start + "href=\"".length();
        return html.substring(from, html.indexOf('"', from));
    }
}
