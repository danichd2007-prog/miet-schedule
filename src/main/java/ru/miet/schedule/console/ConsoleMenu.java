package ru.miet.schedule.console;

import ru.miet.schedule.config.AppConfig;
import ru.miet.schedule.db.ScheduleWriter.RunInfo;
import ru.miet.schedule.domain.Classroom;
import ru.miet.schedule.domain.StudentGroup;
import ru.miet.schedule.domain.Teacher;
import ru.miet.schedule.domain.WeekDay;
import ru.miet.schedule.engine.edit.LessonMover;
import ru.miet.schedule.engine.model.ScheduledLesson;
import ru.miet.schedule.engine.verify.ScheduleMetrics;
import ru.miet.schedule.web.ScheduleService;
import ru.miet.schedule.web.ScheduleService.RunView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Консольное меню (ввод и вывод передаются снаружи, поэтому меню можно проверять тестами).
 * Все действия идут через {@link ScheduleService}, как и в веб-интерфейсе.
 */
public class ConsoleMenu {

    private final ScheduleService service;
    private final AppConfig config;
    private final BufferedReader in;
    private final PrintStream out;

    public ConsoleMenu(ScheduleService service, AppConfig config, BufferedReader in, PrintStream out) {
        this.service = service;
        this.config = config;
        this.in = in;
        this.out = out;
    }

    /** Главный цикл. Заканчивается выбором 0 или концом ввода. */
    public void run() throws IOException {
        out.println("=== Расписание МИЭТ: консольное меню (семестр " + config.semesterId() + ")");

        while (true) {
            out.println();
            out.println("1. Составить расписание");
            out.println("2. Сохранённые версии");
            out.println("3. Расписание группы");
            out.println("4. Расписание преподавателя");
            out.println("5. Расписание аудитории");
            out.println("6. Перенести занятие вручную");
            out.println("7. Выгрузить версию в CSV");
            out.println("8. Показатели качества версии");
            out.println("9. Текущие настройки");
            out.println("0. Выход");

            String choice = ask("Пункт: ");

            if (choice == null || choice.equals("0")) {
                out.println("До свидания.");
                return;
            }

            try {
                switch (choice) {
                    case "1" -> generate();
                    case "2" -> versions();
                    case "3" -> showTimetable("группы", v -> v.data().groups(),
                            StudentGroup::getName, g -> l -> l.requirement().groups().contains(g), false);
                    case "4" -> showTimetable("преподавателя", v -> teachers(v),
                            Teacher::getFullName, t -> l -> l.requirement().teacher().equals(t), true);
                    case "5" -> showTimetable("аудитории", v -> v.data().rooms(),
                            Classroom::getNumber, r -> l -> l.room().equals(r), true);
                    case "6" -> moveLesson();
                    case "7" -> exportCsv();
                    case "8" -> metrics();
                    case "9" -> settings();
                    default -> out.println("Нет такого пункта: «" + choice + "»");
                }
            } catch (SQLException e) {
                out.println("Ошибка базы данных: " + e.getMessage());
            } catch (RuntimeException e) {
                out.println("Ошибка: " + e.getMessage());
            }
        }
    }

    // ---------------------------------------------------------------- пункты меню

    private void generate() throws SQLException {
        out.println("Составляю расписание, подождите...");
        ScheduleService.GenerateResult r = service.generate();

        out.println("Готово: версия № " + r.runId() + ", поставлено занятий " + r.placed()
                + ", не поставлено серий " + r.unplaced() + ", нарушений " + r.violations()
                + ", время " + r.elapsedMs() + " мс");
    }

    private void versions() throws SQLException {
        List<RunInfo> runs = service.runs();

        if (runs.isEmpty()) {
            out.println("Сохранённых версий нет. Выберите пункт 1.");
            return;
        }

        out.println("№    создано              занятий  не поставлено  примечание");

        for (RunInfo run : runs) {
            out.printf("%-4d %-20s %-8d %-14d %s%n", run.id(),
                    run.createdAt().toLocalDateTime().withNano(0).toString().replace('T', ' '),
                    run.placed(), run.unplaced(), run.note() == null ? "" : run.note());
        }
    }

    private <T> void showTimetable(
            String what,
            Function<RunView, List<T>> items,
            Function<T, String> name,
            Function<T, Predicate<ScheduledLesson>> filter,
            boolean withGroups) throws SQLException, IOException {

        RunView view = pickRun();

        if (view == null) {
            return;
        }

        T item = pickByName(what, items.apply(view), name);

        if (item == null) {
            return;
        }

        int week = askWeek(view);

        if (week < 0) {
            return;
        }

        out.println(Timetables.weekGrid(
                "Версия № " + view.info().id() + ", " + what + " " + name.apply(item)
                        + ", неделя " + week + " (" + view.data().calendar().getDate(week, WeekDay.MONDAY) + ")",
                view.lessons(), week, filter.apply(item)));

        if (withGroups) {
            out.println(Timetables.lessonList(view.lessons(), week, filter.apply(item)));
        }
    }

    private void moveLesson() throws SQLException, IOException {
        RunView view = pickRun();

        if (view == null) {
            return;
        }

        StudentGroup group = pickByName("группы", view.data().groups(), StudentGroup::getName);

        if (group == null) {
            return;
        }

        int week = askWeek(view);

        if (week < 0) {
            return;
        }

        List<ScheduledLesson> mine = view.lessons().stream()
                .filter(l -> l.week() == week && l.requirement().groups().contains(group))
                .sorted(Comparator.comparingInt((ScheduledLesson l) -> l.day().ordinal())
                        .thenComparingInt(ScheduledLesson::pair))
                .toList();

        if (mine.isEmpty()) {
            out.println("У группы на этой неделе занятий нет.");
            return;
        }

        for (int i = 0; i < mine.size(); i++) {
            ScheduledLesson l = mine.get(i);
            out.printf("%2d. %s, пара %d: %s (%s), %s, ауд. %s%n", i + 1, l.day().getRussianName(), l.pair(),
                    l.requirement().subject().getName(), Timetables.kind(l),
                    l.requirement().teacher().getShortName(), l.room().getNumber());
        }

        Integer number = askNumber("Номер занятия (0 - отмена): ", mine.size());

        if (number == null || number == 0) {
            return;
        }

        ScheduledLesson lesson = mine.get(number - 1);
        out.println("1. Только эту неделю");
        out.println("2. Эту и все следующие недели");
        Integer scopeChoice = askNumber("Что переносить (0 - отмена): ", 2);

        if (scopeChoice == null || scopeChoice == 0) {
            return;
        }

        LessonMover.Scope scope = scopeChoice == 1
                ? LessonMover.Scope.SINGLE_WEEK : LessonMover.Scope.FROM_THIS_WEEK;
        LessonMover.Key key = new LessonMover.Key(
                lesson.requirement().getId(), lesson.week(), lesson.day(), lesson.pair());

        List<LessonMover.Candidate> options = service.mover(view)
                .suggest(view.lessons(), key, scope, view.data().rooms(), 10);

        if (options.isEmpty()) {
            out.println("Подходящих мест без нарушений не нашлось.");
            return;
        }

        out.println("Куда можно перенести:");

        for (int i = 0; i < options.size(); i++) {
            LessonMover.Candidate c = options.get(i);
            out.printf("%2d. %s, пара %d, ауд. %s%s%n", i + 1, c.day().getRussianName(), c.pair(),
                    c.room().getNumber(),
                    c.warnings() == 0 ? "" : " (предупреждений: " + c.warnings() + ")");
        }

        Integer target = askNumber("Номер места (0 - отмена): ", options.size());

        if (target == null || target == 0) {
            return;
        }

        LessonMover.Candidate c = options.get(target - 1);
        ScheduleService.EditResult result = service.editLesson(
                view.info().id(), key, scope, c.day(), c.pair(), c.room().getId(), true);

        if (result.applied()) {
            out.println("Перенесено занятий: " + result.plan().moves().size());
            result.plan().warnings().forEach(w -> out.println("Предупреждение: " + w));
        } else {
            out.println("Перенос не выполнен:");
            result.plan().errors().forEach(e -> out.println(" - " + e));
        }
    }

    private void exportCsv() throws SQLException, IOException {
        RunView view = pickRun();

        if (view == null) {
            return;
        }

        Path file = config.exportDirectory().resolve("schedule-" + view.info().id() + ".csv");
        Timetables.writeCsv(file, view.lessons(), view.data().calendar());
        out.println("Сохранено: " + file.toAbsolutePath() + " (" + view.lessons().size() + " занятий)");
    }

    private void metrics() throws SQLException, IOException {
        RunView view = pickRun();

        if (view == null) {
            return;
        }

        ScheduleMetrics m = ScheduleMetrics.of(view.lessons());
        out.println("Версия № " + view.info().id() + " (типовые недели):");
        out.println("  занятий: " + m.lessons() + ", групп: " + m.groups());
        out.println("  окон у студентов: " + m.studentWindows()
                + " (в среднем " + String.format("%.2f", m.studentWindowsPerGroupWeek()) + " на группу в неделю)");
        out.println("  окон у преподавателей: " + m.teacherWindows());
        out.println("  занятий в субботу: " + m.saturdayLessons());
        out.println("  поздних занятий (после 4 пары): " + m.lateLessons());
    }

    private void settings() {
        var s = config.solver();
        out.println("Настройки (файл app.properties):");
        out.println("  максимум пар в день: " + s.maxPairsPerDay());
        out.println("  штраф окна у студентов: " + s.studentWindow());
        out.println("  штраф окна у преподавателя: " + s.teacherWindow());
        out.println("  штраф поздней пары: " + s.latePair());
        out.println("  штраф субботы: " + s.saturday());
        out.println("  штраф лишней вместимости: " + s.roomSlack());
        out.println("  штраф перегруженного дня: " + s.dayLoad());
        out.println("  попыток исправления: " + s.repairRounds());
        out.println("  предел времени: " + s.timeLimitMillis() / 1000 + " с");
        out.println("  папка выгрузки: " + config.exportDirectory().toAbsolutePath());
    }

    // ---------------------------------------------------------------- ввод

    private static List<Teacher> teachers(RunView view) {
        return view.data().requirements().stream()
                .map(r -> r.teacher()).distinct()
                .sorted(Comparator.comparing(Teacher::getFullName)).toList();
    }

    /** Версия по номеру; пустой ввод означает последнюю. */
    private RunView pickRun() throws SQLException, IOException {
        List<RunInfo> runs = service.runs();

        if (runs.isEmpty()) {
            out.println("Сохранённых версий нет. Сначала составьте расписание (пункт 1).");
            return null;
        }

        long latest = runs.get(0).id();
        String text = ask("Номер версии (Enter - последняя, " + latest + "): ");

        if (text == null) {
            return null;
        }

        long id = latest;

        if (!text.isBlank()) {
            try {
                id = Long.parseLong(text.trim());
            } catch (NumberFormatException e) {
                out.println("Нужно число.");
                return null;
            }
        }

        Optional<RunView> view = service.run(id);

        if (view.isEmpty()) {
            out.println("Версии № " + id + " нет.");
            return null;
        }

        return view.get();
    }

    /**
     * Ищет по части названия без учёта регистра. При пустом результате или нескольких совпадениях
     * показывает варианты и спрашивает снова; пустая строка отменяет выбор.
     */
    private <T> T pickByName(String what, List<T> items, Function<T, String> name) throws IOException {
        while (true) {
            String text = ask(namePrompt(what) + " (Enter - отмена): ");

            if (text == null || text.isBlank()) {
                return null;
            }

            String needle = text.trim().toLowerCase();
            List<T> exact = items.stream().filter(i -> name.apply(i).equalsIgnoreCase(text.trim())).toList();
            List<T> found = !exact.isEmpty() ? exact
                    : items.stream().filter(i -> name.apply(i).toLowerCase().contains(needle)).toList();

            if (found.size() == 1) {
                if (exact.isEmpty()) {
                    String answer = ask("Под запрос «" + text.trim() + "» подходит только: "
                            + name.apply(found.get(0)) + ". Показать? (Enter или д - да, н - ввести заново): ");

                    if (answer == null) {
                        return null;
                    }

                    String a = answer.trim().toLowerCase();

                    if (!a.isEmpty() && !a.equals("д") && !a.equals("да") && !a.equals("y")) {
                        continue;
                    }
                }

                return found.get(0);
            }

            List<T> shown = found.isEmpty() ? items : found;
            out.println(found.isEmpty()
                    ? "По запросу «" + text.trim() + "» ничего не найдено. Есть:"
                    : "Подходит несколько, уточните:");
            shown.stream().limit(10).forEach(i -> out.println("  " + name.apply(i)));

            if (shown.size() > 10) {
                out.println("  ... и ещё " + (shown.size() - 10));
            }
        }
    }

    private static String namePrompt(String what) {
        return switch (what) {
            case "преподавателя" -> "Фамилия преподавателя (можно часть ФИО)";
            case "аудитории" -> "Номер аудитории (можно начало номера)";
            default -> "Название " + what + " (можно начало)";
        };
    }

    /** Неделя семестра; пустой ввод означает первую. Возвращает -1 при ошибке. */
    private int askWeek(RunView view) throws IOException {
        int max = view.data().calendar().getWeekCount();
        String text = ask("Неделя 1-" + max + " (Enter - первая): ");

        if (text == null) {
            return -1;
        }

        if (text.isBlank()) {
            return 1;
        }

        try {
            int week = Integer.parseInt(text.trim());

            if (week >= 1 && week <= max) {
                return week;
            }
        } catch (NumberFormatException ignored) {
            // сообщение ниже
        }

        out.println("Неделя должна быть числом от 1 до " + max);
        return -1;
    }

    /** Число от 0 до max; null при неверном вводе. */
    private Integer askNumber(String prompt, int max) throws IOException {
        String text = ask(prompt);

        if (text == null) {
            return null;
        }

        try {
            int n = Integer.parseInt(text.trim());

            if (n >= 0 && n <= max) {
                return n;
            }
        } catch (NumberFormatException ignored) {
            // сообщение ниже
        }

        out.println("Нужно число от 0 до " + max);
        return null;
    }

    private String ask(String prompt) throws IOException {
        out.print(prompt);
        out.flush();
        return in.readLine();
    }
}
