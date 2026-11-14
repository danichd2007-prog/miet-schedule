package ru.miet.schedule.engine.solver;

import ru.miet.schedule.engine.model.Occupancy;
import ru.miet.schedule.engine.model.Problem;
import ru.miet.schedule.engine.model.Series;
import ru.miet.schedule.engine.model.TimeGrid;

import java.util.ArrayList;
import java.util.List;

/**
 * Текущее состояние решения: кто и когда занят, и какая серия занимает каждый слот.
 * Все проверки на «свободно» выполняются через битовые индексы за O(1).
 */
final class Board {

    record Candidate(Placement placement, double cost) {
    }

    private static final int T = TimeGrid.TOTAL_SLOTS;

    private final Problem problem;
    private final SolverConfig config;
    private final Occupancy teachers;
    private final Occupancy groups;
    private final Occupancy rooms;
    private final int[] ownerTeacher;
    private final int[] ownerGroup;
    private final int[] ownerRoom;
    private final Placement[] placed;

    Board(Problem problem, SolverConfig config) {
        this.problem = problem;
        this.config = config;
        this.teachers = new Occupancy(problem.teacherCount());
        this.groups = new Occupancy(problem.groupCount());
        this.rooms = new Occupancy(problem.roomCount());
        this.ownerTeacher = new int[problem.teacherCount() * T];
        this.ownerGroup = new int[problem.groupCount() * T];
        this.ownerRoom = new int[problem.roomCount() * T];
        this.placed = new Placement[problem.seriesCount()];
    }

    Placement placement(int series) {
        return placed[series];
    }

    boolean isPlaced(int series) {
        return placed[series] != null;
    }

    int mask(int series, int alternative) {
        return problem.series(series).alternatives().get(alternative).phaseMask();
    }

    // ------------------------------------------------------------------
    // Проверки допустимости
    // ------------------------------------------------------------------

    /** Преподаватель доступен и в нагрузке группы остаётся место: условия, которые не лечатся вытеснением. */
    boolean allowedByRules(int s, int alt, int day, int pair) {
        int mask = mask(s, alt);
        int duration = problem.series(s).duration();

        if (!problem.isTeacherAllowed(problem.teacherOf(s), mask, day, pair, duration)) {
            return false;
        }

        for (int g : problem.groupsOf(s)) {
            if (dayLoad(g, mask, day) + duration > config.maxPairsPerDay()) {
                return false;
            }
        }

        return true;
    }

    /** Свободны ли преподаватель и все группы серии. */
    boolean peopleFree(int s, int alt, int day, int pair) {
        int mask = mask(s, alt);
        int duration = problem.series(s).duration();

        if (!teachers.isFree(problem.teacherOf(s), mask, day, pair, duration)) {
            return false;
        }

        for (int g : problem.groupsOf(s)) {
            if (!groups.isFree(g, mask, day, pair, duration)) {
                return false;
            }
        }

        return true;
    }

    /** Первая (самая подходящая по вместимости) свободная аудитория или -1. */
    int freeRoom(int s, int alt, int day, int pair) {
        int mask = mask(s, alt);
        int duration = problem.series(s).duration();

        for (int room : problem.roomsOf(s)) {
            if (rooms.isFree(room, mask, day, pair, duration)) {
                return room;
            }
        }

        return -1;
    }

    boolean isFeasible(Placement p) {
        int s = p.series();

        return allowedByRules(s, p.alternative(), p.day(), p.pair())
                && peopleFree(s, p.alternative(), p.day(), p.pair())
                && rooms.isFree(
                p.room(),
                mask(s, p.alternative()),
                p.day(),
                p.pair(),
                problem.series(s).duration());
    }

    private int dayLoad(int group, int mask, int day) {
        int load = 0;

        for (int phase = 0; phase < TimeGrid.PHASES; phase++) {
            if ((mask & (1 << phase)) != 0) {
                load = Math.max(load,
                        Integer.bitCount(groups.dayMask(group, phase, day)));
            }
        }

        return load;
    }

    // ------------------------------------------------------------------
    // Кандидаты и стоимость
    // ------------------------------------------------------------------

    /** Все допустимые размещения серии, лучшие первыми. Порядок при равной стоимости стабилен. */
    List<Candidate> candidates(int s) {
        List<Candidate> result = new ArrayList<>();
        Series series = problem.series(s);
        int duration = series.duration();

        for (int alt = 0; alt < series.alternatives().size(); alt++) {
            for (int day = 0; day < TimeGrid.DAYS; day++) {
                for (int pair = 1; pair + duration - 1 <= TimeGrid.PAIRS; pair++) {
                    if (!allowedByRules(s, alt, day, pair)
                            || !peopleFree(s, alt, day, pair)) {
                        continue;
                    }

                    int room = freeRoom(s, alt, day, pair);

                    if (room < 0) {
                        continue;
                    }

                    Placement p = new Placement(s, alt, day, pair, room);
                    result.add(new Candidate(p, cost(p)));
                }
            }
        }

        result.sort((a, b) -> Double.compare(a.cost(), b.cost()));

        return result;
    }

    /** Штраф за размещение: чем меньше, тем удобнее. Учитывает окна, поздние пары, субботу, нагрузку дня. */
    double cost(Placement p) {
        Series series = problem.series(p.series());
        int mask = mask(p.series(), p.alternative());
        int duration = series.duration();
        int lessonBits = ((1 << duration) - 1) << (p.pair() - 1);
        int teacher = problem.teacherOf(p.series());
        int[] people = problem.groupsOf(p.series());

        double windows = 0;
        double load = 0;
        int phases = 0;

        for (int phase = 0; phase < TimeGrid.PHASES; phase++) {
            if ((mask & (1 << phase)) == 0) {
                continue;
            }

            phases++;

            for (int g : people) {
                int before = groups.dayMask(g, phase, p.day());
                int after = before | lessonBits;

                windows += config.studentWindow()
                        * (gaps(after) - gaps(before));

                load += config.dayLoad()
                        * Math.max(0, Integer.bitCount(after) - 4);
            }

            int before = teachers.dayMask(teacher, phase, p.day());

            windows += config.teacherWindow()
                    * (gaps(before | lessonBits) - gaps(before));
        }

        double cost = (windows + load) / phases;

        cost += config.latePair()
                * Math.max(0, p.pair() + duration - 1 - 4);

        if (p.day() == 5) {
            cost += config.saturday();
        }

        int seats = problem.room(p.room()).getSeats();
        int need = series.requirement().studentCount();

        cost += config.roomSlack() * (seats - need) / (double) seats;

        return cost;
    }

    /** Окна: число пустых пар между первой и последней занятой. */
    static int gaps(int dayMask) {
        if (dayMask == 0) {
            return 0;
        }

        int first = Integer.numberOfTrailingZeros(dayMask);
        int last = 31 - Integer.numberOfLeadingZeros(dayMask);

        return (last - first + 1) - Integer.bitCount(dayMask);
    }

    // ------------------------------------------------------------------
    // Изменение состояния
    // ------------------------------------------------------------------

    void place(Placement p) {
        int s = p.series();

        if (placed[s] != null) {
            throw new IllegalStateException("Series " + s + " is already placed");
        }

        int mask = mask(s, p.alternative());
        int duration = problem.series(s).duration();
        int teacher = problem.teacherOf(s);

        teachers.occupy(teacher, mask, p.day(), p.pair(), duration);
        setOwners(ownerTeacher, teacher, mask, p, duration, s + 1);

        for (int g : problem.groupsOf(s)) {
            groups.occupy(g, mask, p.day(), p.pair(), duration);
            setOwners(ownerGroup, g, mask, p, duration, s + 1);
        }

        rooms.occupy(p.room(), mask, p.day(), p.pair(), duration);
        setOwners(ownerRoom, p.room(), mask, p, duration, s + 1);

        placed[s] = p;
    }

    void remove(int s) {
        Placement p = placed[s];

        if (p == null) {
            throw new IllegalStateException("Series " + s + " is not placed");
        }

        int mask = mask(s, p.alternative());
        int duration = problem.series(s).duration();
        int teacher = problem.teacherOf(s);

        teachers.release(teacher, mask, p.day(), p.pair(), duration);
        setOwners(ownerTeacher, teacher, mask, p, duration, 0);

        for (int g : problem.groupsOf(s)) {
            groups.release(g, mask, p.day(), p.pair(), duration);
            setOwners(ownerGroup, g, mask, p, duration, 0);
        }

        rooms.release(p.room(), mask, p.day(), p.pair(), duration);
        setOwners(ownerRoom, p.room(), mask, p, duration, 0);

        placed[s] = null;
    }

    private static void setOwners(
            int[] owners,
            int resource,
            int mask,
            Placement p,
            int duration,
            int value) {

        for (int phase = 0; phase < TimeGrid.PHASES; phase++) {
            if ((mask & (1 << phase)) == 0) {
                continue;
            }

            for (int k = 0; k < duration; k++) {
                owners[resource * T + TimeGrid.slot(phase, p.day(), p.pair() + k)] = value;
            }
        }
    }

    // ------------------------------------------------------------------
    // Кто мешает (для вытеснения)
    // ------------------------------------------------------------------

    /** Серии, занимающие преподавателя и группы в указанное время. */
    void peopleBlockers(int s, int alt, int day, int pair, IntSet out) {
        int mask = mask(s, alt);
        int duration = problem.series(s).duration();

        collect(ownerTeacher, problem.teacherOf(s), mask, day, pair, duration, out);

        for (int g : problem.groupsOf(s)) {
            collect(ownerGroup, g, mask, day, pair, duration, out);
        }
    }

    /** Серии, занимающие аудиторию в указанное время. */
    void roomBlockers(int s, int alt, int day, int pair, int room, IntSet out) {
        collect(ownerRoom, room, mask(s, alt), day, pair,
                problem.series(s).duration(), out);
    }

    private static void collect(
            int[] owners,
            int resource,
            int mask,
            int day,
            int pair,
            int duration,
            IntSet out) {

        for (int phase = 0; phase < TimeGrid.PHASES; phase++) {
            if ((mask & (1 << phase)) == 0) {
                continue;
            }

            for (int k = 0; k < duration; k++) {
                int owner = owners[resource * T + TimeGrid.slot(phase, day, pair + k)];

                if (owner != 0) {
                    out.add(owner - 1);
                }
            }
        }
    }

    /** Маленькое множество чисел без повторов (в нём почти всегда 0–3 элемента). */
    static final class IntSet {
        private int[] values = new int[8];
        private int size;

        void add(int value) {
            for (int i = 0; i < size; i++) {
                if (values[i] == value) {
                    return;
                }
            }

            if (size == values.length) {
                values = java.util.Arrays.copyOf(values, size * 2);
            }

            values[size++] = value;
        }

        int size() {
            return size;
        }

        int get(int index) {
            return values[index];
        }

        void clear() {
            size = 0;
        }

        boolean contains(int value) {
            for (int i = 0; i < size; i++) {
                if (values[i] == value) {
                    return true;
                }
            }

            return false;
        }
    }

    // ------------------------------------------------------------------
    // Диагностика
    // ------------------------------------------------------------------

    /** Объяснение, почему серию не удалось разместить. */
    String diagnose(int s) {
        Series series = problem.series(s);
        var requirement = series.requirement();
        String what = requirement.subject().getName() + " (" + requirement.getLessonType()
                + "), " + requirement.groups().stream()
                .map(g -> g.getName()).reduce((a, b) -> a + ", " + b).orElse("?");

        if (problem.roomsOf(s).length == 0) {
            return what + ": нет аудитории типа «"
                    + requirement.getRequiredClassroomType().getRussianName()
                    + "» вместимостью не менее " + requirement.studentCount() + " мест";
        }

        int total = 0;
        int teacherUnavailable = 0;
        int teacherBusy = 0;
        int groupBusy = 0;
        int overloaded = 0;
        int noRoom = 0;
        int duration = series.duration();
        int teacher = problem.teacherOf(s);

        for (int alt = 0; alt < series.alternatives().size(); alt++) {
            int mask = mask(s, alt);

            for (int day = 0; day < TimeGrid.DAYS; day++) {
                for (int pair = 1; pair + duration - 1 <= TimeGrid.PAIRS; pair++) {
                    total++;

                    if (!problem.isTeacherAllowed(teacher, mask, day, pair, duration)) {
                        teacherUnavailable++;
                    } else if (!teachers.isFree(teacher, mask, day, pair, duration)) {
                        teacherBusy++;
                    } else if (!allGroupsFree(s, mask, day, pair, duration)) {
                        groupBusy++;
                    } else if (!allowedByRules(s, alt, day, pair)) {
                        overloaded++;
                    } else {
                        noRoom++;
                    }
                }
            }
        }

        return what + ": из " + total + " вариантов времени — преподаватель недоступен: "
                + teacherUnavailable + ", преподаватель занят: " + teacherBusy
                + ", группа занята: " + groupBusy
                + ", превышена нагрузка дня: " + overloaded
                + ", нет свободной аудитории: " + noRoom;
    }

    private boolean allGroupsFree(int s, int mask, int day, int pair, int duration) {
        for (int g : problem.groupsOf(s)) {
            if (!groups.isFree(g, mask, day, pair, duration)) {
                return false;
            }
        }

        return true;
    }
}
