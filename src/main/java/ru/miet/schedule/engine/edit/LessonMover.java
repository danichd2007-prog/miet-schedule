package ru.miet.schedule.engine.edit;

import ru.miet.schedule.domain.AcademicCalendar;
import ru.miet.schedule.domain.Classroom;
import ru.miet.schedule.domain.StudentGroup;
import ru.miet.schedule.domain.Teacher;
import ru.miet.schedule.domain.TeacherAvailability;
import ru.miet.schedule.domain.TeachingRequirement;
import ru.miet.schedule.domain.WeekDay;
import ru.miet.schedule.engine.model.ScheduledLesson;
import ru.miet.schedule.engine.model.TimeGrid;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Ручной перенос занятий. Чистая логика без базы: получает расписание, возвращает план
 * переноса с перечнем нарушений и предупреждений. Сам расписание не меняет.
 *
 * Нарушения (errors) блокируют перенос: это те же жёсткие правила, что соблюдает алгоритм
 * и проверяет {@code ScheduleVerifier}. Предупреждения (warnings) не блокируют: это ухудшение
 * удобства (новое окно, суббота, поздняя пара), решение остаётся за человеком.
 */
public final class LessonMover {

    /** Что переносить: одно занятие или это и все следующие такие же. */
    public enum Scope {
        /** Только занятие выбранной недели. */
        SINGLE_WEEK,
        /** Выбранное занятие и все следующие недели того же предмета в том же дне, паре и аудитории. */
        FROM_THIS_WEEK
    }

    /** Адрес занятия внутри расписания: нагрузка, неделя, день, пара начала. */
    public record Key(long requirementId, int week, WeekDay day, int pair) {

        public boolean matches(ScheduledLesson lesson) {
            Long id = lesson.requirement().getId();
            return id != null && id == requirementId && lesson.week() == week
                    && lesson.day() == day && lesson.pair() == pair;
        }
    }

    public record Move(ScheduledLesson from, ScheduledLesson to) {
    }

    public record Plan(List<Move> moves, List<String> errors, List<String> warnings) {

        public boolean valid() {
            return errors.isEmpty() && !moves.isEmpty();
        }
    }

    /** Куда можно перенести без нарушений; warnings — сколько предупреждений это даст. */
    public record Candidate(WeekDay day, int pair, Classroom room, int warnings) {
    }

    public record Context(
            AcademicCalendar calendar,
            Collection<TeacherAvailability> availability,
            int maxPairsPerDay) {
    }

    private final Context context;
    private final Map<Teacher, List<TeacherAvailability>> rules = new HashMap<>();

    public LessonMover(Context context) {
        this.context = context;

        for (TeacherAvailability a : context.availability()) {
            rules.computeIfAbsent(a.getTeacher(), k -> new ArrayList<>()).add(a);
        }
    }

    /** Расписание после переноса (исходный список не меняется). */
    public static List<ScheduledLesson> applied(List<ScheduledLesson> lessons, Plan plan) {
        Set<ScheduledLesson> removed = new HashSet<>();

        for (Move move : plan.moves()) {
            removed.add(move.from());
        }

        List<ScheduledLesson> result = new ArrayList<>(lessons.size());

        for (ScheduledLesson lesson : lessons) {
            if (!removed.contains(lesson)) {
                result.add(lesson);
            }
        }

        for (Move move : plan.moves()) {
            result.add(move.to());
        }

        return result;
    }

    public Plan plan(
            List<ScheduledLesson> all, Key key, Scope scope, WeekDay day, int pair, Classroom room) {

        ScheduledLesson base = all.stream().filter(key::matches).findFirst().orElse(null);

        if (base == null) {
            return failure("Занятие не найдено: возможно, расписание уже изменили");
        }

        List<ScheduledLesson> source = scope == Scope.SINGLE_WEEK
                ? List.of(base)
                : all.stream()
                .filter(l -> l.requirement().equals(base.requirement())
                        && l.day() == base.day() && l.pair() == base.pair()
                        && l.room().equals(base.room()) && l.week() >= base.week())
                .sorted(Comparator.comparingInt(ScheduledLesson::week))
                .toList();

        if (day == base.day() && pair == base.pair() && room.equals(base.room())) {
            return failure("Занятие уже стоит в этом месте");
        }

        List<Move> moves = new ArrayList<>();

        for (ScheduledLesson from : source) {
            moves.add(new Move(from, new ScheduledLesson(
                    from.requirement(), from.week(), day, pair, from.duration(), room)));
        }

        Set<ScheduledLesson> movedSet = new HashSet<>(source);
        Map<Integer, List<ScheduledLesson>> remaining = new HashMap<>();

        for (ScheduledLesson lesson : all) {
            if (!movedSet.contains(lesson)) {
                remaining.computeIfAbsent(lesson.week(), k -> new ArrayList<>()).add(lesson);
            }
        }

        Map<String, TreeSet<Integer>> errors = new LinkedHashMap<>();

        for (Move move : moves) {
            check(move.to(), remaining.getOrDefault(move.to().week(), List.of()), errors);
        }

        List<String> errorTexts = format(errors);

        if (!errorTexts.isEmpty()) {
            return new Plan(List.copyOf(moves), errorTexts, List.of());
        }

        return new Plan(List.copyOf(moves), List.of(), warnings(all, remaining, moves, base));
    }

    /**
     * Все места, куда выбранное можно перенести без нарушений. Для каждой пары «день, пара»
     * берётся первая подходящая аудитория (текущая — в приоритете). Лучшие — с меньшим числом предупреждений.
     */
    public List<Candidate> suggest(
            List<ScheduledLesson> all, Key key, Scope scope, List<Classroom> rooms, int limit) {

        ScheduledLesson base = all.stream().filter(key::matches).findFirst().orElse(null);

        if (base == null) {
            return List.of();
        }

        TeachingRequirement r = base.requirement();

        List<Classroom> suitable = rooms.stream()
                .filter(c -> c.getType() == r.getRequiredClassroomType() && c.getSeats() >= r.studentCount())
                .sorted(Comparator
                        .comparing((Classroom c) -> !c.equals(base.room()))
                        .thenComparingInt(Classroom::getSeats))
                .toList();

        List<Candidate> result = new ArrayList<>();

        for (WeekDay day : WeekDay.values()) {
            for (int pair = 1; pair + base.duration() - 1 <= TimeGrid.PAIRS; pair++) {
                for (Classroom room : suitable) {
                    if (day == base.day() && pair == base.pair() && room.equals(base.room())) {
                        continue;
                    }

                    Plan plan = plan(all, key, scope, day, pair, room);

                    if (plan.valid()) {
                        result.add(new Candidate(day, pair, room, plan.warnings().size()));
                        break;
                    }
                }
            }
        }

        result.sort(Comparator
                .comparingInt(Candidate::warnings)
                .thenComparing(c -> c.day().ordinal())
                .thenComparingInt(Candidate::pair));

        return result.size() > limit ? List.copyOf(result.subList(0, limit)) : List.copyOf(result);
    }

    // ---------- проверка жёстких правил ----------

    private void check(ScheduledLesson to, List<ScheduledLesson> sameWeek, Map<String, TreeSet<Integer>> errors) {
        TeachingRequirement r = to.requirement();
        Teacher teacher = r.teacher();
        Classroom room = to.room();
        int week = to.week();

        if (to.pair() < 1 || to.lastPair() > TimeGrid.PAIRS) {
            add(errors, "Занятие длительностью " + to.duration() + " пар не помещается в день, если начать с "
                    + to.pair() + " пары", week);
            return;
        }

        if (room.getType() != r.getRequiredClassroomType()) {
            add(errors, "Аудитория " + roomName(room) + " не подходит по типу: нужна «"
                    + r.getRequiredClassroomType().getRussianName().toLowerCase() + "»", week);
        }

        if (room.getSeats() < r.studentCount()) {
            add(errors, "В аудитории " + roomName(room) + " " + room.getSeats()
                    + " мест, а студентов " + r.studentCount(), week);
        }

        List<TeacherAvailability> teacherRules = rules.get(teacher);

        if (teacherRules != null) {
            for (int p = to.pair(); p <= to.lastPair(); p++) {
                int pair = p;
                boolean allowed = teacherRules.stream().anyMatch(
                        a -> a.allows(to.day(), pair, context.calendar().getWeekType(week)));

                if (!allowed) {
                    add(errors, "Преподаватель " + teacher.getShortName()
                            + " не работает в это время (" + to.day().getRussianName().toLowerCase()
                            + ", " + p + " пара)", week);
                }
            }
        }

        Map<StudentGroup, Integer> load = new HashMap<>();

        for (StudentGroup group : r.groups()) {
            load.put(group, to.duration());
        }

        for (ScheduledLesson other : sameWeek) {
            if (other.day() != to.day()) {
                continue;
            }

            for (StudentGroup group : r.groups()) {
                if (other.requirement().groups().contains(group)) {
                    load.merge(group, other.duration(), Integer::sum);
                }
            }

            boolean overlap = other.pair() <= to.lastPair() && to.pair() <= other.lastPair();

            if (!overlap) {
                continue;
            }

            String what = "«" + other.requirement().subject().getName() + "»";

            if (other.requirement().teacher().equals(teacher)) {
                add(errors, "Преподаватель " + teacher.getShortName() + " в это время ведёт " + what, week);
            }

            if (other.room().equals(room)) {
                add(errors, "Аудитория " + roomName(room) + " занята: " + what, week);
            }

            for (StudentGroup group : r.groups()) {
                if (other.requirement().groups().contains(group)) {
                    add(errors, "У группы " + group.getName() + " в это время " + what, week);
                }
            }
        }

        for (Map.Entry<StudentGroup, Integer> e : load.entrySet()) {
            if (e.getValue() > context.maxPairsPerDay()) {
                add(errors, "У группы " + e.getKey().getName() + " будет " + e.getValue()
                        + " пар в день, допустимо " + context.maxPairsPerDay(), week);
            }
        }
    }

    // ---------- предупреждения об ухудшении удобства ----------

    private List<String> warnings(
            List<ScheduledLesson> all,
            Map<Integer, List<ScheduledLesson>> remaining,
            List<Move> moves,
            ScheduledLesson base) {

        Map<Integer, List<ScheduledLesson>> before = new HashMap<>();

        for (ScheduledLesson lesson : all) {
            before.computeIfAbsent(lesson.week(), k -> new ArrayList<>()).add(lesson);
        }

        Map<Integer, List<ScheduledLesson>> after = new HashMap<>();
        remaining.forEach((w, list) -> after.put(w, new ArrayList<>(list)));

        for (Move move : moves) {
            after.computeIfAbsent(move.to().week(), k -> new ArrayList<>()).add(move.to());
        }

        Map<String, TreeSet<Integer>> found = new LinkedHashMap<>();

        for (Move move : moves) {
            int week = move.to().week();
            TeachingRequirement r = move.to().requirement();
            Set<WeekDay> days = new java.util.LinkedHashSet<>(List.of(move.from().day(), move.to().day()));

            for (WeekDay day : days) {
                int teacherBefore = windows(before.get(week), day, l -> l.requirement().teacher().equals(r.teacher()));
                int teacherAfter = windows(after.get(week), day, l -> l.requirement().teacher().equals(r.teacher()));

                if (teacherAfter > teacherBefore) {
                    add(found, "У преподавателя " + r.teacher().getShortName() + " станет больше окон ("
                            + day.getRussianName().toLowerCase() + ")", week);
                }

                for (StudentGroup group : r.groups()) {
                    int groupBefore = windows(before.get(week), day, l -> l.requirement().groups().contains(group));
                    int groupAfter = windows(after.get(week), day, l -> l.requirement().groups().contains(group));

                    if (groupAfter > groupBefore) {
                        add(found, "У группы " + group.getName() + " станет больше окон ("
                                + day.getRussianName().toLowerCase() + ")", week);
                    }
                }
            }
        }

        List<String> result = format(found);
        Move first = moves.get(0);

        if (first.to().day() == WeekDay.SATURDAY && base.day() != WeekDay.SATURDAY) {
            result.add("Занятие переносится на субботу");
        }

        if (first.to().lastPair() > 4 && base.lastPair() <= 4) {
            result.add("Занятие окажется позже 4 пары");
        }

        return result;
    }

    private interface Owner {
        boolean test(ScheduledLesson lesson);
    }

    private static int windows(List<ScheduledLesson> weekLessons, WeekDay day, Owner owner) {
        if (weekLessons == null) {
            return 0;
        }

        int mask = 0;

        for (ScheduledLesson lesson : weekLessons) {
            if (lesson.day() == day && owner.test(lesson)) {
                mask |= ((1 << lesson.duration()) - 1) << (lesson.pair() - 1);
            }
        }

        if (mask == 0) {
            return 0;
        }

        int first = Integer.numberOfTrailingZeros(mask);
        int last = 31 - Integer.numberOfLeadingZeros(mask);

        return (last - first + 1) - Integer.bitCount(mask);
    }

    // ---------- вспомогательное ----------

    private static Plan failure(String message) {
        return new Plan(List.of(), List.of(message), List.of());
    }

    private static void add(Map<String, TreeSet<Integer>> map, String message, int week) {
        map.computeIfAbsent(message, k -> new TreeSet<>()).add(week);
    }

    private static List<String> format(Map<String, TreeSet<Integer>> map) {
        List<String> result = new ArrayList<>();

        for (Map.Entry<String, TreeSet<Integer>> e : map.entrySet()) {
            result.add(e.getKey() + weeksText(e.getValue()));
        }

        return result;
    }

    private static String weeksText(TreeSet<Integer> weeks) {
        if (weeks.size() == 1) {
            return " (неделя " + weeks.first() + ")";
        }

        if (weeks.size() <= 6) {
            return " (недели " + String.join(", ", weeks.stream().map(String::valueOf).toList()) + ")";
        }

        return " (" + weeks.size() + " недель, с " + weeks.first() + " по " + weeks.last() + ")";
    }

    private static String roomName(Classroom room) {
        return room.getNumber() + ", " + room.getBuilding();
    }
}
