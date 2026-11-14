package ru.miet.schedule.web;

import ru.miet.schedule.db.ScheduleData;
import ru.miet.schedule.db.ScheduleWriter.RunInfo;
import ru.miet.schedule.domain.AcademicCalendar;
import ru.miet.schedule.domain.Classroom;
import ru.miet.schedule.domain.LessonType;
import ru.miet.schedule.domain.StudentGroup;
import ru.miet.schedule.domain.Teacher;
import ru.miet.schedule.domain.WeekDay;
import ru.miet.schedule.engine.edit.LessonMover;
import ru.miet.schedule.engine.model.ScheduledLesson;
import ru.miet.schedule.engine.verify.ScheduleMetrics;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Страницы сайта (слой «V» в схеме MVC). Это только превращение данных в HTML:
 * никаких обращений к базе. Любой текст из данных проходит через {@link #esc}.
 */
final class HtmlViews {

    /** Чьё расписание показывает сетка: от этого зависит, что писать в клетке. */
    enum Kind {
        GROUP("group"), TEACHER("teacher"), ROOM("room");

        final String path;

        Kind(String path) {
            this.path = path;
        }
    }

    private static final DateTimeFormatter CREATED = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("dd.MM");

    private static final String CSS = """
            :root{--bg:#f6f7f9;--card:#fff;--text:#1d2330;--muted:#6b7385;--line:#e1e4ea;--accent:#2f5bd8;
            --lec:#e8efff;--sem:#e8f6ec;--lab:#fff1e0;--other:#f1ecfa}
            @media (prefers-color-scheme:dark){:root{--bg:#14171d;--card:#1c2029;--text:#e6e9ef;--muted:#98a1b3;
            --line:#2c3240;--accent:#7a9bff;--lec:#223055;--sem:#1f3a2a;--lab:#4a3320;--other:#33294a}}
            *{box-sizing:border-box}
            body{margin:0;background:var(--bg);color:var(--text);font:15px/1.5 system-ui,Segoe UI,Roboto,sans-serif}
            main{max-width:1100px;margin:0 auto;padding:24px 16px 48px}
            a{color:var(--accent);text-decoration:none}a:hover{text-decoration:underline}
            h1{font-size:24px;margin:8px 0 4px}h2{font-size:17px;margin:24px 0 8px}
            .muted{color:var(--muted)}
            .card{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:16px;margin:12px 0}
            table{border-collapse:collapse;width:100%}
            th,td{border:1px solid var(--line);padding:6px 8px;text-align:left;vertical-align:top}
            th{background:var(--bg);font-weight:600}
            .grid td{height:64px;font-size:13px}.grid th.pair{width:42px;text-align:center}
            .lec{background:var(--lec)}.sem{background:var(--sem)}.lab{background:var(--lab)}.other{background:var(--other)}
            .tag{font-size:11px;border:1px solid var(--line);border-radius:4px;padding:0 4px;margin-left:4px}
            .tabs a{display:inline-block;padding:2px 9px;margin:2px;border:1px solid var(--line);border-radius:6px}
            .tabs a.on{background:var(--accent);color:#fff;border-color:var(--accent)}
            .cols{display:grid;grid-template-columns:repeat(auto-fit,minmax(240px,1fr));gap:12px}
            ul{margin:4px 0;padding-left:20px}
            button{font:inherit;background:var(--accent);color:#fff;border:0;border-radius:8px;padding:9px 16px;cursor:pointer}
            .notice{border-left:4px solid var(--accent)}.bad{border-left:4px solid #d64545}
            .scroll{overflow-x:auto}
            select,input{font:inherit;padding:4px 6px;border:1px solid var(--line);border-radius:6px;background:var(--card);color:var(--text)}
            .row{margin:8px 0}.edit{float:right;font-size:12px;margin-left:4px}
            .err li{color:#d64545}.warn li{color:#b7791f}
            .kpis{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:12px}
            .kpi{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:12px}
            .kpi b{display:block;font-size:26px;line-height:1.2}
            .bar{display:flex;align-items:center;gap:8px;margin:3px 0;font-size:13px}
            .bar span.l{width:150px;flex:none;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
            .bar i{display:block;height:12px;border-radius:3px;background:var(--accent);min-width:2px}
            .bar i.hot{background:#d64545}.bar span.v{color:var(--muted);flex:none}
            button.alt{background:transparent;color:var(--accent);border:1px solid var(--accent)}
            """;

    private HtmlViews() {
    }

    static String esc(String text) {
        if (text == null) {
            return "";
        }

        StringBuilder out = new StringBuilder(text.length() + 16);

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);

            switch (ch) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(ch);
            }
        }

        return out.toString();
    }

    static String layout(String title, String body) {
        return "<!doctype html><html lang=\"ru\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>" + esc(title) + "</title><style>" + CSS + "</style></head>"
                + "<body><main>" + body + "</main></body></html>";
    }

    static String home(ScheduleData data, List<RunInfo> runs, String notice, boolean noticeIsError) {
        StringBuilder b = new StringBuilder();

        b.append("<h1>Расписание МИЭТ</h1>");
        b.append("<div class=\"muted\">Семестр: ").append(esc(data.semesterName())).append("</div>");

        if (notice != null) {
            b.append("<div class=\"card ").append(noticeIsError ? "bad" : "notice").append("\">")
                    .append(esc(notice)).append("</div>");
        }

        b.append("<div class=\"card\"><b>Исходные данные:</b> ")
                .append(data.groups().size()).append(" групп, ")
                .append(data.rooms().size()).append(" аудиторий, ")
                .append(data.requirements().size()).append(" строк учебной нагрузки.");

        if (!data.warnings().isEmpty()) {
            b.append("<div class=\"muted\">Пропущено при загрузке:</div><ul>");

            for (String warning : data.warnings()) {
                b.append("<li>").append(esc(warning)).append("</li>");
            }

            b.append("</ul>");
        }

        b.append("<form method=\"post\" action=\"/generate\" style=\"margin-top:12px\">"
                + "<button type=\"submit\">Составить новое расписание</button></form></div>");

        b.append("<h2>Составленные расписания</h2>");

        if (runs.isEmpty()) {
            b.append("<div class=\"card muted\">Расписаний пока нет. Нажмите «Составить новое расписание».</div>");
        } else {
            b.append("<div class=\"card scroll\"><table><tr><th>№</th><th>Создано</th><th>Занятий</th>"
                    + "<th>Не размещено</th><th>Время, мс</th><th>Примечание</th></tr>");

            for (RunInfo run : runs) {
                b.append("<tr><td><a href=\"/run/").append(run.id()).append("\">№ ").append(run.id())
                        .append("</a></td><td>")
                        .append(run.createdAt() == null ? ""
                                : CREATED.format(run.createdAt().atZoneSameInstant(ZoneId.systemDefault())))
                        .append("</td><td>").append(run.placed())
                        .append("</td><td>").append(run.unplaced())
                        .append("</td><td>").append(run.elapsedMs())
                        .append("</td><td>").append(esc(run.note())).append("</td></tr>");
            }

            b.append("</table></div>");
        }

        return layout("Расписание МИЭТ", b.toString());
    }

    static String run(ScheduleService.RunView view) {
        return run(view, null);
    }

    static String run(ScheduleService.RunView view, String notice) {
        ScheduleData data = view.data();
        long runId = view.info().id();
        ScheduleMetrics metrics = ScheduleMetrics.of(view.lessons());

        StringBuilder b = new StringBuilder();

        b.append("<a href=\"/\">← Все расписания</a>");
        b.append("<h1>Расписание № ").append(runId).append("</h1>");
        b.append("<div class=\"muted\">").append(esc(data.semesterName())).append(", занятий: ")
                .append(view.info().placed());

        if (view.info().unplaced() > 0) {
            b.append(", <b>не размещено серий: ").append(view.info().unplaced()).append("</b>");
        }

        b.append("</div>");

        if (notice != null) {
            b.append("<div class=\"card notice\">").append(esc(notice)).append("</div>");
        }

        b.append("<form method=\"post\" action=\"/run/").append(runId).append("/copy\" style=\"margin:8px 0\">"
                + "<button class=\"alt\" type=\"submit\">Создать копию для правок</button></form>");

        b.append("<div class=\"row\"><a href=\"/run/").append(runId)
                .append("/dashboard\">Дашборд: ключевые показатели и сравнение версий →</a></div>");

        b.append("<div class=\"card\"><b>Удобство</b> (по первым четырём неделям): окон у студентов — ")
                .append(metrics.studentWindows()).append(", у преподавателей — ")
                .append(metrics.teacherWindows()).append(", занятий в субботу — ")
                .append(metrics.saturdayLessons()).append(", после 4 пары — ")
                .append(metrics.lateLessons()).append(" из ").append(metrics.lessons()).append("</div>");

        List<StudentGroup> groups = data.groups().stream()
                .filter(g -> view.lessons().stream().anyMatch(l -> l.requirement().groups().contains(g)))
                .sorted(Comparator.comparing(StudentGroup::getName))
                .toList();

        List<Teacher> teachers = view.lessons().stream()
                .map(l -> l.requirement().teacher()).distinct()
                .sorted(Comparator.comparing(Teacher::getFullName))
                .toList();

        List<Classroom> rooms = data.rooms().stream()
                .sorted(Comparator.comparing(Classroom::getBuilding).thenComparing(Classroom::getNumber))
                .toList();

        b.append("<div class=\"cols\">");

        b.append("<div class=\"card\"><b>Группы</b><ul>");
        for (StudentGroup g : groups) {
            b.append(link(runId, Kind.GROUP, g.getId(), g.getName()));
        }
        b.append("</ul></div>");

        b.append("<div class=\"card\"><b>Преподаватели</b><ul>");
        for (Teacher t : teachers) {
            b.append(link(runId, Kind.TEACHER, t.getId(), t.getShortName()
                    + " (" + t.getCategory().getRussianName().toLowerCase() + ")"));
        }
        b.append("</ul></div>");

        b.append("<div class=\"card\"><b>Аудитории</b><ul>");
        for (Classroom r : rooms) {
            b.append(link(runId, Kind.ROOM, r.getId(), r.getNumber() + ", " + r.getBuilding()));
        }
        b.append("</ul></div>");

        b.append("</div>");

        return layout("Расписание № " + runId, b.toString());
    }

    /** Дашборд: показатели версии, загрузка по дням, парам, аудиториям, преподавателям, группам. */
    static String dashboard(
            ScheduleService.RunView view,
            ru.miet.schedule.engine.verify.DashboardStats stats,
            List<ScheduleService.RunMetrics> compare) {

        ScheduleData data = view.data();
        long runId = view.info().id();
        ScheduleMetrics m = ScheduleMetrics.of(view.lessons());
        StringBuilder b = new StringBuilder();

        b.append("<a href=\"/run/").append(runId).append("\">← Расписание № ").append(runId).append("</a>");
        b.append("<h1>Дашборд расписания № ").append(runId).append("</h1>");
        b.append("<div class=\"muted\">").append(esc(data.semesterName()))
                .append(". Значения «в неделю» — среднее за типовой цикл из четырёх недель.</div>");

        b.append("<div class=\"kpis\" style=\"margin-top:12px\">");
        kpi(b, "Занятий размещено", String.valueOf(view.info().placed()), false);
        kpi(b, "Не размещено серий", String.valueOf(view.info().unplaced()), view.info().unplaced() > 0);
        kpi(b, "Окон у студентов", String.valueOf(m.studentWindows()), false);
        kpi(b, "Окон у преподавателей", String.valueOf(m.teacherWindows()), false);
        kpi(b, "Занятий в субботу", String.valueOf(m.saturdayLessons()), false);
        kpi(b, "Занятий после 4 пары", String.valueOf(m.lateLessons()), false);
        b.append("</div>");

        b.append("<div class=\"cols\">");

        b.append("<div class=\"card\"><b>Пар по дням недели</b>");
        double maxDay = max(stats.perDay());
        WeekDay[] days = WeekDay.values();

        for (int i = 0; i < days.length; i++) {
            bar(b, days[i].getRussianName(), stats.perDay()[i], maxDay, false, number(stats.perDay()[i]));
        }

        b.append("</div><div class=\"card\"><b>Пар по номерам пары</b>");
        double maxPair = max(stats.perPair());

        for (int i = 0; i < stats.perPair().length; i++) {
            bar(b, (i + 1) + " пара", stats.perPair()[i], maxPair, false, number(stats.perPair()[i]));
        }

        b.append("</div></div>");

        b.append("<div class=\"cols\">");
        loadCard(b, "Аудитории: занятость (доля от 48 пар в неделю)", stats.rooms(), 10, true);
        loadCard(b, "Преподаватели: пар в неделю", stats.teachers(), 10, false);
        loadCard(b, "Группы: пар в неделю", stats.groups(), 10, false);
        b.append("</div>");

        b.append("<h2>Сравнение версий</h2><div class=\"card scroll\"><table><tr><th>Версия</th><th>Занятий</th>"
                + "<th>Не размещено</th><th>Окон студ.</th><th>Окон преп.</th><th>Суббота</th>"
                + "<th>После 4 пары</th><th>Примечание</th></tr>");

        for (ScheduleService.RunMetrics r : compare) {
            boolean current = r.info().id() == runId;

            b.append("<tr").append(current ? " class=\"lec\"" : "").append("><td><a href=\"/run/")
                    .append(r.info().id()).append("/dashboard\">№ ").append(r.info().id()).append("</a>")
                    .append(current ? " (эта)" : "").append("</td><td>").append(r.info().placed())
                    .append("</td><td>").append(r.info().unplaced())
                    .append("</td><td>").append(r.metrics().studentWindows())
                    .append("</td><td>").append(r.metrics().teacherWindows())
                    .append("</td><td>").append(r.metrics().saturdayLessons())
                    .append("</td><td>").append(r.metrics().lateLessons())
                    .append("</td><td>").append(esc(r.info().note())).append("</td></tr>");
        }

        b.append("</table><div class=\"muted\">Показаны последние версии. Меньше окон, субботы и поздних пар — удобнее.</div></div>");

        return layout("Дашборд расписания № " + runId, b.toString());
    }

    private static void kpi(StringBuilder b, String label, String value, boolean bad) {
        b.append("<div class=\"kpi\"><b").append(bad ? " style=\"color:#d64545\"" : "").append(">")
                .append(esc(value)).append("</b><span class=\"muted\">").append(esc(label)).append("</span></div>");
    }

    private static void loadCard(
            StringBuilder b, String title, List<ru.miet.schedule.engine.verify.DashboardStats.Load> loads,
            int limit, boolean percent) {

        b.append("<div class=\"card\"><b>").append(esc(title)).append("</b>");

        if (loads.isEmpty()) {
            b.append("<div class=\"muted\">Нет данных</div>");
        }

        double max = loads.isEmpty() ? 1 : Math.max(loads.get(0).perWeek(), 1);

        for (int i = 0; i < Math.min(limit, loads.size()); i++) {
            var l = loads.get(i);
            bar(b, l.name(), l.perWeek(), max, percent && l.share() > 0.8,
                    percent ? Math.round(l.share() * 100) + "%" : number(l.perWeek()));
        }

        if (loads.size() > limit) {
            b.append("<div class=\"muted\">Показаны ").append(limit).append(" самых загруженных из ")
                    .append(loads.size()).append("</div>");
        }

        b.append("</div>");
    }

    private static void bar(StringBuilder b, String label, double value, double max, boolean hot, String text) {
        long width = max <= 0 ? 0 : Math.round(value / max * 220);

        b.append("<div class=\"bar\"><span class=\"l\" title=\"").append(esc(label)).append("\">")
                .append(esc(label)).append("</span><i").append(hot ? " class=\"hot\"" : "")
                .append(" style=\"width:").append(width).append("px\"></i><span class=\"v\">")
                .append(esc(text)).append("</span></div>");
    }

    private static double max(double[] values) {
        double max = 0;

        for (double v : values) {
            max = Math.max(max, v);
        }

        return max;
    }

    private static String number(double value) {
        return String.format(java.util.Locale.forLanguageTag("ru"), "%.1f", value);
    }

    private static String link(long runId, Kind kind, Long id, String text) {
        return "<li><a href=\"/run/" + runId + "/" + kind.path + "/" + id + "?week=1\">"
                + esc(text) + "</a></li>";
    }

    /**
     * Сетка одной недели: шесть дней, восемь пар. Двухпарный блок занимает две строки.
     *
     * @param lessons занятия этого объекта за все недели семестра; нужная неделя отбирается здесь
     */
    static String timetable(
            long runId,
            Kind kind,
            long entityId,
            String title,
            int week,
            AcademicCalendar calendar,
            List<ScheduledLesson> lessons) {

        return timetable(runId, kind, entityId, title, week, calendar, lessons, null);
    }

    static String timetable(
            long runId,
            Kind kind,
            long entityId,
            String title,
            int week,
            AcademicCalendar calendar,
            List<ScheduledLesson> lessons,
            String notice) {

        Map<Integer, List<ScheduledLesson>> starts = new HashMap<>();
        Set<Integer> covered = new HashSet<>();

        for (ScheduledLesson lesson : lessons) {
            if (lesson.week() != week) {
                continue;
            }

            int day = lesson.day().ordinal();
            starts.computeIfAbsent(day * 10 + lesson.pair(), k -> new ArrayList<>()).add(lesson);

            for (int k = 1; k < lesson.duration(); k++) {
                covered.add(day * 10 + lesson.pair() + k);
            }
        }

        StringBuilder b = new StringBuilder();

        b.append("<a href=\"/run/").append(runId).append("\">← Расписание № ").append(runId).append("</a>");
        b.append("<h1>").append(esc(title)).append("</h1>");

        if (notice != null) {
            b.append("<div class=\"card notice\">").append(esc(notice)).append("</div>");
        }

        b.append("<div class=\"muted\">Неделя ").append(week).append(": ")
                .append(SHORT_DATE.format(calendar.getDate(week, WeekDay.MONDAY))).append(" – ")
                .append(SHORT_DATE.format(calendar.getDate(week, WeekDay.SATURDAY)))
                .append(", ").append(esc(calendar.getWeekType(week).getRussianName().toLowerCase()))
                .append("</div>");

        b.append("<div class=\"tabs\" style=\"margin:8px 0\">");
        for (int w = 1; w <= calendar.getWeekCount(); w++) {
            b.append("<a href=\"/run/").append(runId).append('/').append(kind.path).append('/')
                    .append(entityId).append("?week=").append(w).append('"')
                    .append(w == week ? " class=\"on\"" : "").append('>').append(w).append("</a>");
        }
        b.append("</div>");

        b.append("<div class=\"card scroll\"><table class=\"grid\"><tr><th class=\"pair\">Пара</th>");
        for (WeekDay day : WeekDay.values()) {
            b.append("<th>").append(esc(day.getRussianName())).append("</th>");
        }
        b.append("</tr>");

        for (int pair = 1; pair <= 8; pair++) {
            b.append("<tr><th class=\"pair\">").append(pair).append("</th>");

            for (WeekDay day : WeekDay.values()) {
                int key = day.ordinal() * 10 + pair;

                if (covered.contains(key)) {
                    continue;
                }

                List<ScheduledLesson> here = starts.get(key);

                if (here == null) {
                    b.append("<td></td>");
                    continue;
                }

                int span = here.stream().mapToInt(ScheduledLesson::duration).max().orElse(1);

                b.append("<td class=\"").append(css(here.get(0).requirement().getLessonType())).append('"');

                if (span > 1) {
                    b.append(" rowspan=\"").append(span).append('"');
                }

                b.append('>');

                for (int i = 0; i < here.size(); i++) {
                    if (i > 0) {
                        b.append("<hr>");
                    }

                    b.append(cell(kind, runId, here.get(i)));
                }

                b.append("</td>");
            }

            b.append("</tr>");
        }

        b.append("</table></div>");

        return layout(title, b.toString());
    }

    private static String cell(Kind kind, long runId, ScheduledLesson lesson) {
        String groups = lesson.requirement().groups().stream()
                .map(StudentGroup::getName).collect(Collectors.joining(", "));
        String room = lesson.room().getNumber() + ", " + lesson.room().getBuilding();
        String teacher = lesson.requirement().teacher().getShortName();

        String details = switch (kind) {
            case GROUP -> teacher + " · " + room;
            case TEACHER -> groups + " · " + room;
            case ROOM -> groups + " · " + teacher;
        };

        return editLink(runId, lesson)
                + "<b>" + esc(lesson.requirement().subject().getName()) + "</b>"
                + "<span class=\"tag\">" + shortType(lesson.requirement().getLessonType()) + "</span><br>"
                + "<span class=\"muted\">" + esc(details) + "</span>";
    }

    static String editUrl(long runId, ScheduledLesson lesson) {
        return "/run/" + runId + "/edit/" + lesson.requirement().getId() + "/" + lesson.week()
                + "/" + lesson.day().ordinal() + "/" + lesson.pair();
    }

    private static String editLink(long runId, ScheduledLesson lesson) {
        return "<a class=\"edit\" title=\"Перенести занятие\" href=\"" + editUrl(runId, lesson) + "\">✎</a>";
    }

    static String shortType(LessonType type) {
        return switch (type) {
            case LECTURE -> "лек";
            case SEMINAR -> "сем";
            case LABORATORY -> "лаб";
            case PRACTICE -> "прак";
            case CONSULTATION -> "конс";
            case EXAM -> "экз";
        };
    }

    private static String css(LessonType type) {
        return switch (type) {
            case LECTURE -> "lec";
            case SEMINAR -> "sem";
            case LABORATORY -> "lab";
            default -> "other";
        };
    }

    /** Параметры формы переноса: что выбрано сейчас. */
    record EditForm(LessonMover.Scope scope, WeekDay day, int pair, long roomId) {
    }

    /**
     * Страница переноса одного занятия.
     *
     * @param plan        результат проверки выбранного места; null, если проверки ещё не было
     * @param suggestions места, куда можно перенести без нарушений
     * @param rooms       аудитории, подходящие занятию по типу и вместимости
     */
    static String edit(
            ScheduleService.RunView view,
            ScheduledLesson lesson,
            EditForm form,
            LessonMover.Plan plan,
            List<LessonMover.Candidate> suggestions,
            List<Classroom> rooms) {

        long runId = view.info().id();
        var r = lesson.requirement();
        String action = editUrl(runId, lesson);
        long firstGroup = r.groups().get(0).getId();
        boolean series = form.scope() == LessonMover.Scope.FROM_THIS_WEEK;

        StringBuilder b = new StringBuilder();

        b.append("<a href=\"/run/").append(runId).append("/group/").append(firstGroup).append("?week=")
                .append(lesson.week()).append("\">← К расписанию группы</a>");
        b.append("<h1>Перенос занятия</h1>");

        String groups = r.groups().stream().map(StudentGroup::getName).collect(Collectors.joining(", "));

        b.append("<div class=\"card\"><b>").append(esc(r.subject().getName())).append("</b> ")
                .append("<span class=\"tag\">").append(shortType(r.getLessonType())).append("</span><br>")
                .append("Группы: ").append(esc(groups)).append("<br>")
                .append("Преподаватель: ").append(esc(r.teacher().getFullName())).append("<br>")
                .append("Сейчас: неделя ").append(lesson.week()).append(", ")
                .append(esc(lesson.day().getRussianName().toLowerCase())).append(", ")
                .append(pairsText(lesson)).append(", аудитория ").append(esc(roomName(lesson.room())))
                .append("</div>");

        b.append("<form method=\"post\" action=\"").append(action).append("\" class=\"card\">");

        b.append("<div class=\"row\"><label><input type=\"radio\" name=\"scope\" value=\"single\"")
                .append(series ? "" : " checked").append("> Только эту неделю (неделя ")
                .append(lesson.week()).append(")</label></div>");
        b.append("<div class=\"row\"><label><input type=\"radio\" name=\"scope\" value=\"series\"")
                .append(series ? " checked" : "")
                .append("> Эту и все следующие недели, где занятие стоит так же</label></div>");

        b.append("<div class=\"row\">День: <select name=\"day2\">");
        for (WeekDay day : WeekDay.values()) {
            b.append("<option value=\"").append(day.ordinal()).append('"')
                    .append(day == form.day() ? " selected" : "").append('>')
                    .append(esc(day.getRussianName())).append("</option>");
        }
        b.append("</select> &nbsp; Пара: <select name=\"pair2\">");
        for (int pair = 1; pair <= 8; pair++) {
            b.append("<option value=\"").append(pair).append('"')
                    .append(pair == form.pair() ? " selected" : "").append('>').append(pair).append("</option>");
        }
        b.append("</select> &nbsp; Аудитория: <select name=\"room\">");
        for (Classroom room : rooms) {
            b.append("<option value=\"").append(room.getId()).append('"')
                    .append(room.getId() == form.roomId() ? " selected" : "").append('>')
                    .append(esc(roomName(room))).append(" (").append(room.getSeats()).append(" мест)</option>");
        }
        b.append("</select></div>");

        b.append("<div class=\"row\"><button class=\"alt\" type=\"submit\" name=\"action\" value=\"check\">Проверить</button> "
                + "<button type=\"submit\" name=\"action\" value=\"apply\">Перенести</button></div></form>");

        if (plan != null) {
            b.append(planCard(plan));
        }

        b.append("<h2>Куда можно перенести без нарушений</h2>");

        if (suggestions.isEmpty()) {
            b.append("<div class=\"card muted\">Свободных подходящих мест нет.</div>");
        } else {
            b.append("<div class=\"card\"><ul>");

            for (LessonMover.Candidate c : suggestions) {
                b.append("<li><a href=\"").append(action).append("?scope=").append(series ? "series" : "single")
                        .append("&amp;day2=").append(c.day().ordinal()).append("&amp;pair2=").append(c.pair())
                        .append("&amp;room=").append(c.room().getId()).append("\">")
                        .append(esc(c.day().getRussianName())).append(", ").append(c.pair()).append(" пара, ауд. ")
                        .append(esc(roomName(c.room()))).append("</a> ")
                        .append("<span class=\"muted\">")
                        .append(c.warnings() == 0 ? "без замечаний" : "замечаний: " + c.warnings())
                        .append("</span></li>");
            }

            b.append("</ul></div>");
        }

        return layout("Перенос занятия", b.toString());
    }

    private static String planCard(LessonMover.Plan plan) {
        StringBuilder b = new StringBuilder();

        if (!plan.errors().isEmpty()) {
            b.append("<div class=\"card bad\"><b>Перенести нельзя:</b><ul class=\"err\">");

            for (String error : plan.errors()) {
                b.append("<li>").append(esc(error)).append("</li>");
            }

            b.append("</ul></div>");
            return b.toString();
        }

        b.append("<div class=\"card notice\"><b>Нарушений нет.</b> Будет перенесено занятий: ")
                .append(plan.moves().size()).append(".");

        if (plan.moves().size() > 1) {
            b.append(" Недели ").append(plan.moves().get(0).to().week()).append("–")
                    .append(plan.moves().get(plan.moves().size() - 1).to().week()).append(".");
        }

        if (!plan.warnings().isEmpty()) {
            b.append("<div>Замечания (перенос возможен):</div><ul class=\"warn\">");

            for (String warning : plan.warnings()) {
                b.append("<li>").append(esc(warning)).append("</li>");
            }

            b.append("</ul>");
        }

        b.append("</div>");
        return b.toString();
    }

    private static String roomName(Classroom room) {
        return room.getNumber() + ", " + room.getBuilding();
    }

    private static String pairsText(ScheduledLesson lesson) {
        return lesson.duration() == 1
                ? lesson.pair() + " пара"
                : lesson.pair() + "–" + lesson.lastPair() + " пары";
    }

    static String error(int status, String message) {
        return layout("Ошибка " + status,
                "<a href=\"/\">← На главную</a><h1>Ошибка " + status + "</h1>"
                        + "<div class=\"card bad\">" + esc(message) + "</div>");
    }
}
