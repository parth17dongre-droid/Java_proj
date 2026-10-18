-- =====================================================================
--  EV Charging Slot Booking System — MySQL schema
--  JAVA-DBMS Mini Project (Semester 3)
--
--  Target   : MySQL 8.0.16 or newer (CHECK constraints are enforced from 8.0.16)
--  Engine   : InnoDB (row-level locking + foreign keys + transactions)
--  Charset  : utf8mb4
--
--  Naming is chosen so Spring Boot / Hibernate maps it with ZERO extra config:
--    * tables are plural snake_case   (users, wallet_transactions ...)
--    * columns are snake_case         (user_id  <->  userId in Java)
--    * every primary key is BIGINT AUTO_INCREMENT  ->  @GeneratedValue(strategy = IDENTITY)
--    * every status/type column is VARCHAR + CHECK  ->  @Enumerated(EnumType.STRING)
--    * every money column is DECIMAL                ->  java.math.BigDecimal
--    * every timestamp is DATETIME                  ->  java.time.LocalDateTime
--
--  Run:  mysql -u root -p < schema.sql
--  Then: mysql -u root -p ev_charging < seed.sql        (optional demo data)
-- =====================================================================

DROP DATABASE IF EXISTS ev_charging;
CREATE DATABASE ev_charging
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;
USE ev_charging;


-- ---------------------------------------------------------------------
-- 1. USERS — login identity for both EV owners and station admins.
--    password_hash holds a BCrypt hash (never the plain password).
--    role drives JWT authorities: USER -> ROLE_USER, ADMIN -> ROLE_ADMIN.
-- ---------------------------------------------------------------------
CREATE TABLE users (
    user_id        BIGINT        NOT NULL AUTO_INCREMENT,
    name           VARCHAR(100)  NOT NULL,
    email          VARCHAR(150)  NOT NULL,
    phone          VARCHAR(15)   NULL,
    password_hash  VARCHAR(100)  NOT NULL,
    role           VARCHAR(10)   NOT NULL DEFAULT 'USER',
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT pk_users        PRIMARY KEY (user_id),
    CONSTRAINT uq_users_email  UNIQUE (email),
    CONSTRAINT ck_users_role   CHECK (role IN ('USER', 'ADMIN'))
) ENGINE = InnoDB;


-- ---------------------------------------------------------------------
-- 2. WALLETS — exactly one per user (1:1, enforced by UNIQUE on user_id).
--    balance can never go negative: the CHECK makes the database itself
--    reject an over-deduction even if the Java code has a bug.
--    version is for JPA optimistic locking (@Version) so two parallel
--    deductions cannot overwrite each other.
-- ---------------------------------------------------------------------
CREATE TABLE wallets (
    wallet_id   BIGINT         NOT NULL AUTO_INCREMENT,
    user_id     BIGINT         NOT NULL,
    balance     DECIMAL(10,2)  NOT NULL DEFAULT 0.00,
    version     BIGINT         NOT NULL DEFAULT 0,
    updated_at  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT pk_wallets          PRIMARY KEY (wallet_id),
    CONSTRAINT uq_wallets_user     UNIQUE (user_id),
    CONSTRAINT fk_wallets_user     FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT ck_wallets_balance  CHECK (balance >= 0)
) ENGINE = InnoDB;


-- ---------------------------------------------------------------------
-- 3. CHARGERS — the physical charging points.
--    type          : FAST or SLOW (requirement: separate fast/slow billing)
--    power_kw      : rated output, used to estimate the advance payment
--    rate_per_kwh  : price per unit; FAST chargers carry a higher rate
--    status        : ACTIVE or MAINTENANCE. A charger in MAINTENANCE has
--                    all its slots hidden by v_available_slots below.
-- ---------------------------------------------------------------------
CREATE TABLE chargers (
    charger_id    BIGINT         NOT NULL AUTO_INCREMENT,
    charger_code  VARCHAR(20)    NOT NULL,
    location      VARCHAR(150)   NOT NULL,
    type          VARCHAR(10)    NOT NULL,
    power_kw      DECIMAL(6,2)   NOT NULL,
    rate_per_kwh  DECIMAL(8,2)   NOT NULL,
    status        VARCHAR(15)    NOT NULL DEFAULT 'ACTIVE',
    created_at    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_chargers         PRIMARY KEY (charger_id),
    CONSTRAINT uq_chargers_code    UNIQUE (charger_code),
    CONSTRAINT ck_chargers_type    CHECK (type IN ('FAST', 'SLOW')),
    CONSTRAINT ck_chargers_status  CHECK (status IN ('ACTIVE', 'MAINTENANCE')),
    CONSTRAINT ck_chargers_power   CHECK (power_kw > 0),
    CONSTRAINT ck_chargers_rate    CHECK (rate_per_kwh > 0)
) ENGINE = InnoDB;


-- ---------------------------------------------------------------------
-- 4. SLOTS — fixed time windows on a charger that can be booked.
--    status is the 3-state display value from the requirements:
--        AVAILABLE -> BOOKED -> IN_PROGRESS -> AVAILABLE
--    UNIQUE (charger_id, start_time) stops the admin from creating two
--    identical slots on the same charger.
--    version = JPA @Version, first line of defence against double booking.
-- ---------------------------------------------------------------------
CREATE TABLE slots (
    slot_id     BIGINT       NOT NULL AUTO_INCREMENT,
    charger_id  BIGINT       NOT NULL,
    start_time  DATETIME     NOT NULL,
    end_time    DATETIME     NOT NULL,
    status      VARCHAR(15)  NOT NULL DEFAULT 'AVAILABLE',
    version     BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_slots               PRIMARY KEY (slot_id),
    CONSTRAINT uq_slots_charger_time  UNIQUE (charger_id, start_time),
    CONSTRAINT fk_slots_charger       FOREIGN KEY (charger_id) REFERENCES chargers (charger_id),
    CONSTRAINT ck_slots_status        CHECK (status IN ('AVAILABLE', 'BOOKED', 'IN_PROGRESS')),
    CONSTRAINT ck_slots_time          CHECK (end_time > start_time)
) ENGINE = InnoDB;

CREATE INDEX ix_slots_status_start ON slots (status, start_time);


-- ---------------------------------------------------------------------
-- 5. BOOKINGS — a user's reservation of a slot.
--    status lifecycle:
--        BOOKED -> IN_PROGRESS -> COMPLETED
--        BOOKED -> CANCELLED
--    Modify booking = change slot_id on a BOOKED row (inside one transaction).
--
--    CONCURRENCY GUARANTEE (the important bit):
--    active_slot_id is a generated column that equals slot_id while the
--    booking is live (BOOKED / IN_PROGRESS) and NULL once it is COMPLETED
--    or CANCELLED. A UNIQUE index on it means MySQL itself will reject a
--    second live booking for the same slot — even if two requests reach
--    the database at the exact same millisecond. NULLs don't clash, so
--    cancelled/completed history is kept without blocking re-booking.
--    Java never reads or writes this column; do NOT map it in the entity.
-- ---------------------------------------------------------------------
CREATE TABLE bookings (
    booking_id     BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL,
    slot_id        BIGINT       NOT NULL,
    status         VARCHAR(15)  NOT NULL DEFAULT 'BOOKED',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    cancelled_at   DATETIME     NULL,
    active_slot_id BIGINT GENERATED ALWAYS AS
                   (CASE WHEN status IN ('BOOKED', 'IN_PROGRESS') THEN slot_id END) VIRTUAL,

    CONSTRAINT pk_bookings              PRIMARY KEY (booking_id),
    CONSTRAINT fk_bookings_user         FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_bookings_slot         FOREIGN KEY (slot_id) REFERENCES slots (slot_id),
    CONSTRAINT uq_bookings_active_slot  UNIQUE (active_slot_id),
    CONSTRAINT ck_bookings_status       CHECK (status IN ('BOOKED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'))
) ENGINE = InnoDB;

CREATE INDEX ix_bookings_user ON bookings (user_id, created_at);


-- ---------------------------------------------------------------------
-- 6. BILLS — one per booking (1:1, UNIQUE on booking_id).
--    Two-phase billing resolves "pay before unlock" vs "bill on actual energy":
--      advance_amount : estimated cost, paid BEFORE the charger unlocks
--                       (power_kw x slot hours x rate_per_kwh)
--      energy_kwh     : actual energy reported by the charger at the end
--      amount         : final cost = energy_kwh x rate_per_kwh
--    rate_per_kwh is COPIED from the charger at booking time, so a later
--    price change never rewrites an old bill.
--    payment_status:
--      PENDING  -> bill created, nothing paid, charger LOCKED
--      PAID     -> advance paid, charger may UNLOCK
--      SETTLED  -> session over, final amount charged / difference refunded
-- ---------------------------------------------------------------------
CREATE TABLE bills (
    bill_id         BIGINT         NOT NULL AUTO_INCREMENT,
    booking_id      BIGINT         NOT NULL,
    rate_per_kwh    DECIMAL(8,2)   NOT NULL,
    advance_amount  DECIMAL(10,2)  NOT NULL DEFAULT 0.00,
    energy_kwh      DECIMAL(8,3)   NULL,
    amount          DECIMAL(10,2)  NULL,
    payment_status  VARCHAR(10)    NOT NULL DEFAULT 'PENDING',
    created_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paid_at         DATETIME       NULL,
    settled_at      DATETIME       NULL,

    CONSTRAINT pk_bills          PRIMARY KEY (bill_id),
    CONSTRAINT uq_bills_booking  UNIQUE (booking_id),
    CONSTRAINT fk_bills_booking  FOREIGN KEY (booking_id) REFERENCES bookings (booking_id),
    CONSTRAINT ck_bills_status   CHECK (payment_status IN ('PENDING', 'PAID', 'SETTLED')),
    CONSTRAINT ck_bills_values   CHECK (advance_amount >= 0
                                        AND (energy_kwh IS NULL OR energy_kwh >= 0)
                                        AND (amount     IS NULL OR amount     >= 0)),
    -- a SETTLED bill must have its final numbers filled in
    CONSTRAINT ck_bills_settled  CHECK (payment_status <> 'SETTLED'
                                        OR (energy_kwh IS NOT NULL AND amount IS NOT NULL AND settled_at IS NOT NULL))
) ENGINE = InnoDB;

CREATE INDEX ix_bills_settled_at ON bills (settled_at);


-- ---------------------------------------------------------------------
-- 7. WALLET_TRANSACTIONS — append-only ledger of every money movement.
--    (Named wallet_transactions, not "transaction", to avoid clashing with
--     the SQL keyword and with Spring's @Transactional in conversation.)
--    type:
--      TOPUP          money added to wallet            (bill_id NULL)
--      ADVANCE_DEBIT  advance paid before unlock        (bill_id set)
--      FINAL_DEBIT    extra charged if usage > advance  (bill_id set)
--      REFUND         unused advance returned           (bill_id set)
--    amount is always positive; type says which direction it moved.
--    balance_after snapshots the wallet balance right after this entry,
--    which makes the statement screen trivial and audits easy.
-- ---------------------------------------------------------------------
CREATE TABLE wallet_transactions (
    txn_id         BIGINT         NOT NULL AUTO_INCREMENT,
    wallet_id      BIGINT         NOT NULL,
    bill_id        BIGINT         NULL,
    type           VARCHAR(15)    NOT NULL,
    amount         DECIMAL(10,2)  NOT NULL,
    balance_after  DECIMAL(10,2)  NOT NULL,
    created_at     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_wallet_transactions  PRIMARY KEY (txn_id),
    CONSTRAINT fk_wtxn_wallet          FOREIGN KEY (wallet_id) REFERENCES wallets (wallet_id),
    CONSTRAINT fk_wtxn_bill            FOREIGN KEY (bill_id)   REFERENCES bills (bill_id),
    CONSTRAINT ck_wtxn_type            CHECK (type IN ('TOPUP', 'ADVANCE_DEBIT', 'FINAL_DEBIT', 'REFUND')),
    CONSTRAINT ck_wtxn_amount          CHECK (amount > 0),
    -- a top-up is never tied to a bill; every other type must be
    CONSTRAINT ck_wtxn_bill_link       CHECK ((type = 'TOPUP' AND bill_id IS NULL)
                                              OR (type <> 'TOPUP' AND bill_id IS NOT NULL))
) ENGINE = InnoDB;

CREATE INDEX ix_wtxn_wallet_time ON wallet_transactions (wallet_id, created_at);


-- =====================================================================
--  VIEWS — read-only queries the backend can call like tables.
--  Map them with @Entity + @Immutable, or query them with native SQL.
-- =====================================================================

-- What the "Book a slot" screen shows.
-- Hides: past slots, slots already booked/in progress, and every slot of
-- a charger that is under MAINTENANCE (requirement 13 / priority 10).
CREATE OR REPLACE VIEW v_available_slots AS
SELECT  s.slot_id,
        s.charger_id,
        c.charger_code,
        c.location,
        c.type          AS charger_type,
        c.power_kw,
        c.rate_per_kwh,
        s.start_time,
        s.end_time
FROM    slots s
JOIN    chargers c ON c.charger_id = s.charger_id
WHERE   s.status = 'AVAILABLE'
  AND   c.status = 'ACTIVE'
  AND   s.start_time > NOW();

-- Live board for the admin: every upcoming/current slot with its display
-- state. Maintenance chargers show MAINTENANCE instead of their slot state.
CREATE OR REPLACE VIEW v_slot_board AS
SELECT  s.slot_id,
        c.charger_id,
        c.charger_code,
        c.location,
        c.type AS charger_type,
        s.start_time,
        s.end_time,
        CASE WHEN c.status = 'MAINTENANCE' THEN 'MAINTENANCE' ELSE s.status END AS display_status
FROM    slots s
JOIN    chargers c ON c.charger_id = s.charger_id
WHERE   s.end_time > NOW();

-- Revenue = final amount of every SETTLED bill whose booking COMPLETED,
-- grouped by settlement date. Refunded advances are NOT revenue, and a paid
-- booking that was cancelled (bill settled at 0) does not count as a session.
CREATE OR REPLACE VIEW v_daily_revenue AS
SELECT  DATE(b.settled_at)                    AS revenue_date,
        COUNT(*)                              AS sessions,
        SUM(b.energy_kwh)                     AS total_kwh,
        SUM(b.amount)                         AS revenue
FROM    bills b
JOIN    bookings bk ON bk.booking_id = b.booking_id
WHERE   b.payment_status = 'SETTLED'
  AND   bk.status = 'COMPLETED'
GROUP BY DATE(b.settled_at);

CREATE OR REPLACE VIEW v_weekly_revenue AS
SELECT  YEARWEEK(b.settled_at, 3)                                    AS year_week,
        DATE_SUB(DATE(MIN(b.settled_at)),
                 INTERVAL WEEKDAY(MIN(b.settled_at)) DAY)            AS week_start,
        COUNT(*)                                                     AS sessions,
        SUM(b.energy_kwh)                                            AS total_kwh,
        SUM(b.amount)                                                AS revenue
FROM    bills b
JOIN    bookings bk ON bk.booking_id = b.booking_id
WHERE   b.payment_status = 'SETTLED'
  AND   bk.status = 'COMPLETED'
GROUP BY YEARWEEK(b.settled_at, 3);

CREATE OR REPLACE VIEW v_monthly_revenue AS
SELECT  DATE_FORMAT(b.settled_at, '%Y-%m')    AS revenue_month,
        COUNT(*)                              AS sessions,
        SUM(b.energy_kwh)                     AS total_kwh,
        SUM(b.amount)                         AS revenue
FROM    bills b
JOIN    bookings bk ON bk.booking_id = b.booking_id
WHERE   b.payment_status = 'SETTLED'
  AND   bk.status = 'COMPLETED'
GROUP BY DATE_FORMAT(b.settled_at, '%Y-%m');

-- Revenue split by FAST vs SLOW chargers (useful on the admin dashboard).
CREATE OR REPLACE VIEW v_revenue_by_charger_type AS
SELECT  c.type          AS charger_type,
        COUNT(*)        AS sessions,
        SUM(b.energy_kwh) AS total_kwh,
        SUM(b.amount)   AS revenue
FROM    bills b
JOIN    bookings bk ON bk.booking_id = b.booking_id
JOIN    slots s     ON s.slot_id     = bk.slot_id
JOIN    chargers c  ON c.charger_id  = s.charger_id
WHERE   b.payment_status = 'SETTLED'
  AND   bk.status = 'COMPLETED'
GROUP BY c.type;


-- =====================================================================
--  APPLICATION USER (optional but recommended)
--  Spring Boot should NOT log in as root. Uncomment, set a password, run.
-- =====================================================================
-- CREATE USER IF NOT EXISTS 'ev_app'@'localhost' IDENTIFIED BY 'change_me';
-- GRANT SELECT, INSERT, UPDATE, DELETE ON ev_charging.* TO 'ev_app'@'localhost';
-- FLUSH PRIVILEGES;
