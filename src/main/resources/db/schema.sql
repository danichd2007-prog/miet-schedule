-- =====================================================================
-- Схема БД university_schedule (PostgreSQL)
-- Составлена по коду: DatabaseGeneratorTest.load, JdbcClassroomRepository, Main
-- Скрипт идемпотентный: его можно запускать повторно.
-- =====================================================================

-- ---------- Справочники ----------

CREATE TABLE IF NOT EXISTS academic_semesters (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(100) NOT NULL UNIQUE,
    first_monday DATE         NOT NULL
                 CHECK (EXTRACT(ISODOW FROM first_monday) = 1),
    week_count   INT          NOT NULL CHECK (week_count > 0)
);

CREATE TABLE IF NOT EXISTS classroom_types (
    id   BIGSERIAL PRIMARY KEY,
    code VARCHAR(30)  NOT NULL UNIQUE,   -- LECTURE / LABORATORY / COMPUTER / REGULAR
    name VARCHAR(100) NOT NULL
);

CREATE TABLE IF NOT EXISTS classrooms (
    id           BIGSERIAL PRIMARY KEY,
    number       VARCHAR(20)  NOT NULL,
    building     VARCHAR(100) NOT NULL,
    type_id      BIGINT       NOT NULL REFERENCES classroom_types (id),
    capacity     INT          NOT NULL CHECK (capacity > 0),
    is_available BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_classroom UNIQUE (building, number)
);

CREATE TABLE IF NOT EXISTS directions (
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    code VARCHAR(30)  NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS student_groups (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(50) NOT NULL UNIQUE,
    course        INT         NOT NULL CHECK (course BETWEEN 1 AND 6),
    direction_id  BIGINT      NOT NULL REFERENCES directions (id),
    -- Пока не используется кодом; нужно для проверки вместимости аудиторий.
    student_count INT         NOT NULL DEFAULT 25 CHECK (student_count > 0)
);

CREATE TABLE IF NOT EXISTS teachers (
    id          BIGSERIAL PRIMARY KEY,
    first_name  VARCHAR(100) NOT NULL,
    last_name   VARCHAR(100) NOT NULL,
    middle_name VARCHAR(100) NOT NULL DEFAULT '',
    department  VARCHAR(200) NOT NULL,
    CONSTRAINT uq_teacher UNIQUE (last_name, first_name, middle_name, department)
);

CREATE TABLE IF NOT EXISTS subjects (
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL UNIQUE
);

-- ---------- Потоки (лекции для нескольких групп) ----------

CREATE TABLE IF NOT EXISTS streams (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(200) NOT NULL UNIQUE,
    subject_id BIGINT       NOT NULL REFERENCES subjects (id)
);

CREATE TABLE IF NOT EXISTS stream_groups (
    stream_id BIGINT NOT NULL REFERENCES streams (id)        ON DELETE CASCADE,
    group_id  BIGINT NOT NULL REFERENCES student_groups (id) ON DELETE CASCADE,
    PRIMARY KEY (stream_id, group_id)
);

-- ---------- Квалификации преподавателей ----------

CREATE TABLE IF NOT EXISTS teacher_subjects (
    id         BIGSERIAL PRIMARY KEY,
    teacher_id BIGINT NOT NULL REFERENCES teachers (id) ON DELETE CASCADE,
    subject_id BIGINT NOT NULL REFERENCES subjects (id) ON DELETE CASCADE,
    CONSTRAINT uq_teacher_subject UNIQUE (teacher_id, subject_id)
);

CREATE TABLE IF NOT EXISTS teacher_subject_lesson_types (
    teacher_subject_id BIGINT      NOT NULL REFERENCES teacher_subjects (id) ON DELETE CASCADE,
    lesson_type        VARCHAR(20) NOT NULL
        CHECK (lesson_type IN ('LECTURE','SEMINAR','LABORATORY','PRACTICE','CONSULTATION','EXAM')),
    PRIMARY KEY (teacher_subject_id, lesson_type)
);

CREATE TABLE IF NOT EXISTS teacher_availability (
    id          BIGSERIAL PRIMARY KEY,
    teacher_id  BIGINT      NOT NULL REFERENCES teachers (id) ON DELETE CASCADE,
    day_of_week VARCHAR(10) NOT NULL
        CHECK (day_of_week IN ('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY')),
    start_pair  INT         NOT NULL CHECK (start_pair BETWEEN 1 AND 8),
    end_pair    INT         NOT NULL CHECK (end_pair BETWEEN 1 AND 8),
    week_type   VARCHAR(20) NOT NULL
        CHECK (week_type IN ('F_NUMERATOR','F_DENOMINATOR','S_NUMERATOR','S_DENOMINATOR',
                             'NUMERATOR','DENOMINATOR','EVERY_WEEK')),
    CHECK (end_pair >= start_pair)
);

-- ---------- Учебная нагрузка ----------

CREATE TABLE IF NOT EXISTS teaching_requirements (
    id                BIGSERIAL PRIMARY KEY,
    semester_id       BIGINT      NOT NULL REFERENCES academic_semesters (id),
    teacher_subject_id BIGINT     NOT NULL REFERENCES teacher_subjects (id),
    group_id          BIGINT      REFERENCES student_groups (id),
    stream_id         BIGINT      REFERENCES streams (id),
    lesson_type       VARCHAR(20) NOT NULL
        CHECK (lesson_type IN ('LECTURE','SEMINAR','LABORATORY','PRACTICE','CONSULTATION','EXAM')),
    total_pairs       INT         NOT NULL CHECK (total_pairs > 0),
    is_block          BOOLEAN     NOT NULL DEFAULT FALSE,
    classroom_type_id BIGINT      NOT NULL REFERENCES classroom_types (id),
    -- ровно одно из group_id / stream_id
    CONSTRAINT ck_target CHECK ((group_id IS NULL) <> (stream_id IS NULL)),
    -- лекции только для потока, остальное только для группы
    CONSTRAINT ck_lecture_stream CHECK ((lesson_type = 'LECTURE') = (stream_id IS NOT NULL)),
    -- двухпарный блок требует чётного числа пар
    CONSTRAINT ck_block_even CHECK (NOT is_block OR total_pairs % 2 = 0)
);

CREATE INDEX IF NOT EXISTS idx_req_semester  ON teaching_requirements (semester_id);
CREATE INDEX IF NOT EXISTS idx_avail_teacher ON teacher_availability (teacher_id);

-- ---------- Категория преподавателя ----------
-- PART_TIME (совместитель) ставится в расписание первым, его доступность жёсткая.
-- STAFF (штатный) идёт вторым. Столбец добавляется отдельно, чтобы схема
-- применялась и к уже созданной базе.

ALTER TABLE teachers
    ADD COLUMN IF NOT EXISTS category VARCHAR(20) NOT NULL DEFAULT 'STAFF'
        CHECK (category IN ('PART_TIME', 'STAFF'));

-- ---------- Результаты работы алгоритма ----------
-- Один запуск = одна версия расписания семестра. Старые версии не затираются,
-- их можно сравнивать и возвращаться к ним.

CREATE TABLE IF NOT EXISTS schedule_runs (
    id          BIGSERIAL PRIMARY KEY,
    semester_id BIGINT       NOT NULL REFERENCES academic_semesters (id),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    placed      INT          NOT NULL,            -- размещённых занятий
    unplaced    INT          NOT NULL,            -- серий, которые не удалось разместить
    elapsed_ms  BIGINT       NOT NULL,
    note        VARCHAR(200) NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS schedule_lessons (
    id             BIGSERIAL PRIMARY KEY,
    run_id         BIGINT      NOT NULL REFERENCES schedule_runs (id) ON DELETE CASCADE,
    requirement_id BIGINT      NOT NULL REFERENCES teaching_requirements (id),
    week           INT         NOT NULL CHECK (week >= 1),
    day_of_week    VARCHAR(10) NOT NULL
        CHECK (day_of_week IN ('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY')),
    start_pair     INT         NOT NULL CHECK (start_pair BETWEEN 1 AND 8),
    duration       INT         NOT NULL CHECK (duration IN (1, 2)),
    classroom_id   BIGINT      NOT NULL REFERENCES classrooms (id),
    CHECK (start_pair + duration - 1 <= 8)
);

CREATE INDEX IF NOT EXISTS idx_lessons_run      ON schedule_lessons (run_id, week, day_of_week);
CREATE INDEX IF NOT EXISTS idx_lessons_classroom ON schedule_lessons (classroom_id);

-- =====================================================================
-- Тестовые данные (совпадают с ai_schedule.json и Main)
-- =====================================================================

INSERT INTO classroom_types (code, name) VALUES
    ('LECTURE',    'Лекционная'),
    ('LABORATORY', 'Лабораторная'),
    ('COMPUTER',   'Компьютерный класс'),
    ('REGULAR',    'Обычная')
ON CONFLICT (code) DO NOTHING;

-- id = 1 занят намеренно: Main вставляет семестр с id 1 через ON CONFLICT DO NOTHING.
INSERT INTO academic_semesters (id, name, first_monday, week_count)
VALUES (1, 'Тест: осень 2026', '2026-08-31', 16)
ON CONFLICT DO NOTHING;

SELECT setval(pg_get_serial_sequence('academic_semesters', 'id'),
              (SELECT MAX(id) FROM academic_semesters));

INSERT INTO classrooms (number, building, type_id, capacity, is_available)
SELECT v.number, v.building, t.id, v.capacity, TRUE
FROM (VALUES
    ('1201', 'Главный корпус', 'LECTURE',    150),
    ('3229', '3-й корпус',     'REGULAR',     45),
    ('3118', '3-й корпус',     'COMPUTER',    30),
    ('4340', '4-й корпус',     'LABORATORY',  25)
) AS v(number, building, type_code, capacity)
JOIN classroom_types t ON t.code = v.type_code
ON CONFLICT (building, number) DO NOTHING;

INSERT INTO directions (name, code) VALUES
    ('Программная инженерия',          'ПИН'),
    ('Информационная безопасность',    'ИБ'),
    ('Прикладная математика',          'ПМ')
ON CONFLICT (code) DO NOTHING;

-- Группы, которые ждёт DatabaseGeneratorTest (ИБ-22, ПИН-27, ПМ-22)
INSERT INTO student_groups (name, course, direction_id, student_count)
SELECT v.name, v.course, d.id, v.cnt
FROM (VALUES
    ('ПИН-27', 2, 'ПИН', 25),
    ('ИБ-22',  2, 'ИБ',  25),
    ('ПМ-22',  2, 'ПМ',  25)
) AS v(name, course, dir_code, cnt)
JOIN directions d ON d.code = v.dir_code
ON CONFLICT (name) DO NOTHING;

INSERT INTO subjects (name) VALUES
    ('Объектно-ориентированное программирование (Java)'),
    ('Алгоритмы и структуры данных'),
    ('Математический анализ'),
    ('Базы данных (SQL)'),
    ('Архитектура ЭВМ и систем'),
    ('Физика'),
    ('Общая электротехника')
ON CONFLICT (name) DO NOTHING;

-- В JSON даны только инициалы, поэтому first_name / middle_name = инициалы
INSERT INTO teachers (last_name, first_name, middle_name, department) VALUES
    ('Воронова',  'Н.', 'М.', 'Кафедра МИЭТ'),
    ('Лупин',     'С.', 'С.', 'Кафедра МИЭТ'),
    ('Безменов',  'И.', 'В.', 'Кафедра МИЭТ'),
    ('Трофимов',  'А.', 'В.', 'Кафедра МИЭТ'),
    ('Поляков',   'И.', 'А.', 'Кафедра МИЭТ'),
    ('Гужов',     'В.', 'В.', 'Кафедра МИЭТ'),
    ('Камолов',   'М.', 'М.', 'Кафедра МИЭТ')
ON CONFLICT ON CONSTRAINT uq_teacher DO NOTHING;

-- Кто какой предмет ведёт
INSERT INTO teacher_subjects (teacher_id, subject_id)
SELECT t.id, s.id
FROM (VALUES
    ('Воронова', 'Объектно-ориентированное программирование (Java)'),
    ('Лупин',    'Алгоритмы и структуры данных'),
    ('Безменов', 'Математический анализ'),
    ('Трофимов', 'Базы данных (SQL)'),
    ('Поляков',  'Архитектура ЭВМ и систем'),
    ('Гужов',    'Физика'),
    ('Камолов',  'Общая электротехника')
) AS v(last_name, subject_name)
JOIN teachers t ON t.last_name = v.last_name
JOIN subjects s ON s.name = v.subject_name
ON CONFLICT ON CONSTRAINT uq_teacher_subject DO NOTHING;

-- Какие типы занятий им разрешены
INSERT INTO teacher_subject_lesson_types (teacher_subject_id, lesson_type)
SELECT ts.id, v.lesson_type
FROM (VALUES
    ('Воронова', 'Объектно-ориентированное программирование (Java)', 'LABORATORY'),
    ('Лупин',    'Алгоритмы и структуры данных',                      'LECTURE'),
    ('Безменов', 'Математический анализ',                             'LECTURE'),
    ('Трофимов', 'Базы данных (SQL)',                                 'LABORATORY'),
    ('Поляков',  'Архитектура ЭВМ и систем',                          'SEMINAR'),
    ('Гужов',    'Физика',                                            'LECTURE'),
    ('Камолов',  'Общая электротехника',                              'LABORATORY')
) AS v(last_name, subject_name, lesson_type)
JOIN teachers t         ON t.last_name = v.last_name
JOIN subjects s         ON s.name = v.subject_name
JOIN teacher_subjects ts ON ts.teacher_id = t.id AND ts.subject_id = s.id
ON CONFLICT DO NOTHING;

-- Доступность: все дни, пары 1-8, каждую неделю (как в Main)
INSERT INTO teacher_availability (teacher_id, day_of_week, start_pair, end_pair, week_type)
SELECT t.id, d.day, 1, 8, 'EVERY_WEEK'
FROM teachers t
CROSS JOIN (VALUES ('MONDAY'),('TUESDAY'),('WEDNESDAY'),
                   ('THURSDAY'),('FRIDAY'),('SATURDAY')) AS d(day)
WHERE NOT EXISTS (SELECT 1 FROM teacher_availability);

-- Потоки для лекций (пока в каждом потоке одна группа ПИН-27)
INSERT INTO streams (name, subject_id)
SELECT 'Поток ПИН-27: ' || s.name, s.id
FROM subjects s
WHERE s.name IN ('Алгоритмы и структуры данных', 'Математический анализ', 'Физика')
ON CONFLICT (name) DO NOTHING;

INSERT INTO stream_groups (stream_id, group_id)
SELECT st.id, g.id
FROM streams st
JOIN student_groups g ON g.name = 'ПИН-27'
ON CONFLICT DO NOTHING;

-- Нагрузка на семестр. totalPairs считается на весь семестр (16 недель):
--   лекция 1 раз в неделю = 16, лабораторный блок раз в 2 недели = 8 (4 блока по 2 пары),
--   семинар раз в 2 недели = 8.
-- Лекции (для потоков)
INSERT INTO teaching_requirements
    (semester_id, teacher_subject_id, group_id, stream_id, lesson_type, total_pairs, is_block, classroom_type_id)
SELECT 1, ts.id, NULL, st.id, 'LECTURE', 16, FALSE, ct.id
FROM streams st
JOIN teacher_subjects ts ON ts.subject_id = st.subject_id
JOIN classroom_types ct  ON ct.code = 'LECTURE'
WHERE NOT EXISTS (SELECT 1 FROM teaching_requirements);

-- Занятия для групп (лабораторные блоки и семинар)
INSERT INTO teaching_requirements
    (semester_id, teacher_subject_id, group_id, stream_id, lesson_type, total_pairs, is_block, classroom_type_id)
SELECT 1, ts.id, g.id, NULL, v.lesson_type, 8, v.is_block, ct.id
FROM (VALUES
    ('Воронова', 'Объектно-ориентированное программирование (Java)', 'LABORATORY', TRUE,  'COMPUTER'),
    ('Трофимов', 'Базы данных (SQL)',                                 'LABORATORY', TRUE,  'COMPUTER'),
    ('Камолов',  'Общая электротехника',                              'LABORATORY', TRUE,  'LABORATORY'),
    ('Поляков',  'Архитектура ЭВМ и систем',                          'SEMINAR',    FALSE, 'REGULAR')
) AS v(last_name, subject_name, lesson_type, is_block, room_type)
JOIN teachers t          ON t.last_name = v.last_name
JOIN subjects s          ON s.name = v.subject_name
JOIN teacher_subjects ts ON ts.teacher_id = t.id AND ts.subject_id = s.id
JOIN classroom_types ct  ON ct.code = v.room_type
JOIN student_groups g    ON g.name = 'ПИН-27'
WHERE NOT EXISTS (SELECT 1 FROM teaching_requirements WHERE group_id IS NOT NULL);

-- ---------- Демонстрационный совместитель ----------
-- Гужов работает только по вторникам и четвергам (пары 1-6, каждую неделю).
-- Алгоритм обязан поставить его занятия именно в эти дни.

UPDATE teachers SET category = 'PART_TIME' WHERE last_name = 'Гужов';

DELETE FROM teacher_availability
WHERE teacher_id IN (SELECT id FROM teachers WHERE last_name = 'Гужов');

INSERT INTO teacher_availability (teacher_id, day_of_week, start_pair, end_pair, week_type)
SELECT t.id, d.day, 1, 6, 'EVERY_WEEK'
FROM teachers t
CROSS JOIN (VALUES ('TUESDAY'), ('THURSDAY')) AS d(day)
WHERE t.last_name = 'Гужов';
