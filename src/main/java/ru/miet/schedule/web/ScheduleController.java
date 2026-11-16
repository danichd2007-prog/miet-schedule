package ru.miet.schedule.web;

import ru.miet.schedule.db.ScheduleData;
import ru.miet.schedule.domain.Classroom;
import ru.miet.schedule.domain.StudentGroup;
import ru.miet.schedule.domain.WeekDay;
import ru.miet.schedule.engine.edit.LessonMover;
import ru.miet.schedule.domain.Teacher;
import ru.miet.schedule.engine.model.ScheduledLesson;
import ru.miet.schedule.web.HtmlViews.Kind;
import ru.miet.schedule.web.ScheduleService.GenerateResult;
import ru.miet.schedule.web.ScheduleService.RunView;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Контроллер («C» в схеме MVC): по адресу решает, что показать.
 * Не зависит от HTTP-сервера, поэтому проверяется обычными вызовами в тестах.
 *
 * Адреса:
 *   GET  /                                  главная: исходные данные и список расписаний
 *   POST /generate                          составить новое расписание и перейти к нему
 *   GET  /run/{id}                          расписание целиком: списки групп, преподавателей, аудиторий
 *   GET  /run/{id}/group|teacher|room/{id}?week=N   сетка недели
 *   GET  /run/{id}/dashboard                дашборд: ключевые показатели и сравнение версий
 *   POST /run/{id}/copy                     копия расписания для правок
 *   GET|POST /run/{id}/edit/{нагрузка}/{неделя}/{день}/{пара}   перенос занятия
 */
public final class ScheduleController {

    /** Ответ контроллера. Заголовок Location задаётся только у перенаправлений. */
    public record Response(int status, String body, String location) {

        static Response html(int status, String body) {
            return new Response(status, body, null);
        }

        static Response redirect(String location) {
            return new Response(303, "", location);
        }
    }

    private static final class NotFound extends RuntimeException {
        NotFound(String message) {
            super(message);
        }
    }

    private final ScheduleService service;

    public ScheduleController(ScheduleService service) {
        this.service = service;
    }

    public Response handle(String method, String path, String rawQuery, String body) {
        try {
            return route(method, path, parseQuery(rawQuery), parseQuery(body));
        } catch (NotFound e) {
            return Response.html(404, HtmlViews.error(404, e.getMessage()));
        } catch (IllegalArgumentException e) {
            return Response.html(404, HtmlViews.error(404, e.getMessage()));
        } catch (IllegalStateException e) {
            return Response.html(409, HtmlViews.error(409, e.getMessage()));
        } catch (SQLException e) {
            return Response.html(503, HtmlViews.error(503,
                    "Нет связи с базой данных. Проверьте, что PostgreSQL запущен, "
                            + "а database.properties указывает на нужную базу. "
                            + "Если база новая, один раз запустите DbDemo с аргументом init."));
        }
    }

    private Response route(String method, String path, Map<String, String> query, Map<String, String> form)
            throws SQLException {
        String[] parts = Arrays.stream(path.split("/")).filter(s -> !s.isEmpty()).toArray(String[]::new);

        if (parts.length == 0) {
            return onlyGet(method, () -> {
                ScheduleService.Home home = service.home();
                return Response.html(200, HtmlViews.home(home.data(), home.runs(), null, false));
            });
        }

        if (parts.length == 1 && parts[0].equals("generate")) {
            if (!method.equals("POST")) {
                return Response.html(405, HtmlViews.error(405, "Составить расписание можно только кнопкой на главной странице."));
            }

            GenerateResult result = service.generate();
            return Response.redirect("/run/" + result.runId());
        }

        if (parts.length == 3 && parts[0].equals("run") && parts[2].equals("copy")) {
            if (!method.equals("POST")) {
                return Response.html(405, HtmlViews.error(405, "Копию можно создать только кнопкой на странице расписания."));
            }

            long copyId = service.copyRun(id(parts[1], "Расписание"));
            return Response.redirect("/run/" + copyId + "?notice=copied");
        }

        if (parts.length == 3 && parts[0].equals("run") && parts[2].equals("dashboard")) {
            return onlyGet(method, () -> {
                long runId = id(parts[1], "Расписание");
                RunView view = service.run(runId)
                        .orElseThrow(() -> new NotFound("Расписание № " + runId + " не найдено"));

                return Response.html(200, HtmlViews.dashboard(
                        view, ru.miet.schedule.engine.verify.DashboardStats.of(
                                view.lessons(), view.data().rooms()),
                        service.compare(8)));
            });
        }

        if (parts.length == 7 && parts[0].equals("run") && parts[2].equals("edit")) {
            return edit(method, parts, method.equals("POST") ? form : query);
        }

        if (parts[0].equals("run") && (parts.length == 2 || parts.length == 4)) {
            long runId = id(parts[1], "Расписание");
            RunView view = service.run(runId)
                    .orElseThrow(() -> new NotFound("Расписание № " + runId + " не найдено"));

            if (!method.equals("GET")) {
                return Response.html(405, HtmlViews.error(405, "Страница только для просмотра."));
            }

            if (parts.length == 2) {
                return Response.html(200, HtmlViews.run(view, notice(query)));
            }

            return timetable(view, parts[2], id(parts[3], "Объект"),
                    "summary".equals(query.get("view")) ? 0 : week(query, view), notice(query));
        }

        throw new NotFound("Такой страницы нет");
    }

    private Response timetable(RunView view, String kindName, long entityId, int week, String notice) {
        ScheduleData data = view.data();
        List<ScheduledLesson> lessons = view.lessons();
        Kind kind;
        String title;
        List<ScheduledLesson> mine;

        switch (kindName) {
            case "group" -> {
                StudentGroup group = data.groups().stream()
                        .filter(g -> g.getId() == entityId).findFirst()
                        .orElseThrow(() -> new NotFound("Группа не найдена"));
                kind = Kind.GROUP;
                title = "Группа " + group.getName();
                mine = lessons.stream().filter(l -> l.requirement().groups().contains(group)).toList();
            }
            case "teacher" -> {
                Optional<Teacher> found = lessons.stream().map(l -> l.requirement().teacher())
                        .filter(t -> t.getId() == entityId).findFirst();
                Teacher teacher = found.orElseThrow(() -> new NotFound("Преподаватель не найден в этом расписании"));
                kind = Kind.TEACHER;
                title = teacher.getFullName();
                mine = lessons.stream().filter(l -> l.requirement().teacher().equals(teacher)).toList();
            }
            case "room" -> {
                Classroom room = data.rooms().stream()
                        .filter(r -> r.getId() == entityId).findFirst()
                        .orElseThrow(() -> new NotFound("Аудитория не найдена"));
                kind = Kind.ROOM;
                title = "Аудитория " + room.getNumber() + ", " + room.getBuilding();
                mine = lessons.stream().filter(l -> l.room().equals(room)).toList();
            }
            default -> throw new NotFound("Такой страницы нет");
        }

        if (week == 0) {
            return Response.html(200, HtmlViews.summaryTimetable(
                    view.info().id(), kind, entityId, title, data.calendar(), mine));
        }
        return Response.html(200, HtmlViews.timetable(
                view.info().id(), kind, entityId, title, week, data.calendar(), mine, notice));
    }

    /** Тексты уведомлений фиксированы: из адреса берётся только короткий код, а не текст. */
    private static String notice(Map<String, String> query) {
        return switch (query.getOrDefault("notice", "")) {
            case "moved" -> "Занятие перенесено.";
            case "copied" -> "Создана копия расписания. Правки вносятся в неё, исходное остаётся без изменений.";
            default -> null;
        };
    }

    private Response edit(String method, String[] parts, Map<String, String> params) throws SQLException {
        long runId = id(parts[1], "Расписание");
        long requirementId = id(parts[3], "Нагрузка");
        int week = (int) id(parts[4], "Неделя");
        int dayIndex = (int) number(parts[5], -1);
        int pair = (int) id(parts[6], "Пара");

        if (dayIndex < 0 || dayIndex >= WeekDay.values().length) {
            throw new NotFound("Занятие не найдено");
        }

        RunView view = service.run(runId)
                .orElseThrow(() -> new NotFound("Расписание № " + runId + " не найдено"));

        LessonMover.Key key = new LessonMover.Key(requirementId, week, WeekDay.values()[dayIndex], pair);
        ScheduledLesson lesson = view.lessons().stream().filter(key::matches).findFirst()
                .orElseThrow(() -> new NotFound(
                        "Занятие не найдено: возможно, расписание уже изменили. Вернитесь к расписанию и откройте занятие заново."));

        LessonMover mover = service.mover(view);

        LessonMover.Scope scope = "series".equals(params.get("scope"))
                ? LessonMover.Scope.FROM_THIS_WEEK
                : LessonMover.Scope.SINGLE_WEEK;

        int dayChoice = (int) number(params.get("day2"), -1);
        int pairChoice = (int) number(params.get("pair2"), -1);
        long roomChoice = number(params.get("room"), -1);

        boolean chosen = dayChoice >= 0 && dayChoice < WeekDay.values().length
                && pairChoice >= 1 && pairChoice <= 8 && roomChoice > 0;

        WeekDay day = chosen ? WeekDay.values()[dayChoice] : lesson.day();
        int newPair = chosen ? pairChoice : lesson.pair();
        long roomId = chosen ? roomChoice : lesson.room().getId();

        LessonMover.Plan plan = null;

        if (chosen) {
            boolean apply = method.equals("POST") && "apply".equals(params.get("action"));
            ScheduleService.EditResult result =
                    service.editLesson(runId, key, scope, day, newPair, roomId, apply);

            if (result.applied()) {
                return Response.redirect("/run/" + runId + "/group/"
                        + lesson.requirement().groups().get(0).getId()
                        + "?week=" + lesson.week() + "&notice=moved");
            }

            plan = result.plan();
        }

        var requirement = lesson.requirement();
        List<Classroom> rooms = view.data().rooms().stream()
                .filter(c -> c.equals(lesson.room())
                        || (c.getType() == requirement.getRequiredClassroomType()
                        && c.getSeats() >= requirement.studentCount()))
                .sorted(java.util.Comparator.comparing(Classroom::getBuilding).thenComparing(Classroom::getNumber))
                .toList();

        return Response.html(200, HtmlViews.edit(
                view, lesson, new HtmlViews.EditForm(scope, day, newPair, roomId), plan,
                mover.suggest(view.lessons(), key, scope, view.data().rooms(), 12), rooms));
    }

    private static long number(String text, long fallback) {
        try {
            return text == null ? fallback : Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private interface Action {
        Response run() throws SQLException;
    }

    private static Response onlyGet(String method, Action action) throws SQLException {
        if (!method.equals("GET")) {
            return Response.html(405, HtmlViews.error(405, "Метод не поддерживается"));
        }

        return action.run();
    }

    private static long id(String text, String what) {
        try {
            long value = Long.parseLong(text);

            if (value > 0) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // падаем в «не найдено» ниже
        }

        throw new NotFound(what + " не найдено");
    }

    /** Номер недели из ?week=; неверное значение заменяется ближайшим допустимым. */
    private static int week(Map<String, String> query, RunView view) {
        int max = view.data().calendar().getWeekCount();

        try {
            return Math.max(1, Math.min(max, Integer.parseInt(query.getOrDefault("week", "1"))));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> result = new HashMap<>();

        if (rawQuery == null || rawQuery.isEmpty()) {
            return result;
        }

        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : pair.substring(eq + 1);

            result.put(URLDecoder.decode(key, StandardCharsets.UTF_8),
                    URLDecoder.decode(value, StandardCharsets.UTF_8));
        }

        return result;
    }
}
