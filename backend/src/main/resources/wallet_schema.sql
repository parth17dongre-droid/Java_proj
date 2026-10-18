-- =====================================================================
--  EV Charging: bookings, bills, wallet. Run on every startup by
--  WalletSchemaInitializer, AFTER Hibernate has created users and
--  ev_charging_slots (this file only adds objects, never alters those).
--  Idempotent. Statements end with "//" because trigger/function
--  bodies contain ";".
-- =====================================================================

CREATE TABLE IF NOT EXISTS wallets (
    wallet_id   BIGINT         NOT NULL AUTO_INCREMENT,
    user_id     BIGINT         NOT NULL,
    balance     DECIMAL(10,2)  NOT NULL DEFAULT 0.00,
    version     BIGINT         NOT NULL DEFAULT 0,
    updated_at  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_wallets          PRIMARY KEY (wallet_id),
    CONSTRAINT uq_wallets_user     UNIQUE (user_id),
    CONSTRAINT fk_wallets_user     FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_wallets_balance  CHECK (balance >= 0)
) ENGINE = InnoDB //

-- A booking is a paid-for hold on one slot for duration_minutes.
-- active_slot_id is the slot while the booking is live, NULL afterwards; the
-- UNIQUE index on it makes a double booking impossible even if Java has a bug.
CREATE TABLE IF NOT EXISTS bookings (
    booking_id        BIGINT       NOT NULL AUTO_INCREMENT,
    user_id           BIGINT       NOT NULL,
    slot_id           BIGINT       NOT NULL,
    duration_minutes  INT          NOT NULL,
    status            VARCHAR(15)  NOT NULL DEFAULT 'BOOKED',
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    cancelled_at      DATETIME     NULL,
    active_slot_id    BIGINT GENERATED ALWAYS AS
                      (CASE WHEN status IN ('BOOKED', 'IN_PROGRESS') THEN slot_id END) VIRTUAL,
    CONSTRAINT pk_bookings              PRIMARY KEY (booking_id),
    CONSTRAINT fk_bookings_user         FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_bookings_slot         FOREIGN KEY (slot_id) REFERENCES ev_charging_slots (id),
    CONSTRAINT uq_bookings_active_slot  UNIQUE (active_slot_id),
    CONSTRAINT ck_bookings_status       CHECK (status IN ('BOOKED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_bookings_duration     CHECK (duration_minutes BETWEEN 15 AND 480)
) ENGINE = InnoDB //

CREATE TABLE IF NOT EXISTS bills (
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
    CONSTRAINT ck_bills_settled  CHECK (payment_status <> 'SETTLED'
                                        OR (energy_kwh IS NOT NULL AND amount IS NOT NULL AND settled_at IS NOT NULL))
) ENGINE = InnoDB //

CREATE TABLE IF NOT EXISTS wallet_transactions (
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
    CONSTRAINT ck_wtxn_bill_link       CHECK ((type = 'TOPUP' AND bill_id IS NULL)
                                           OR (type <> 'TOPUP' AND bill_id IS NOT NULL))
) ENGINE = InnoDB //

-- Users created before the wallet module existed get an empty wallet.
INSERT IGNORE INTO wallets (user_id) SELECT id FROM users //

-- ---------------------------------------------------------------------
-- Advance = power_kw x hours x rate (worst case: full power all session)
-- ---------------------------------------------------------------------
DROP FUNCTION IF EXISTS fn_advance_amount //
CREATE FUNCTION fn_advance_amount(p_slot_id BIGINT, p_minutes INT)
RETURNS DECIMAL(10,2)
READS SQL DATA
BEGIN
    DECLARE v_amount DECIMAL(10,2);
    SELECT ROUND(power_kw * p_minutes * rate_per_kwh / 60, 2)
      INTO v_amount
      FROM ev_charging_slots
     WHERE id = p_slot_id;
    RETURN v_amount;          -- NULL if the slot does not exist
END //

DROP PROCEDURE IF EXISTS sp_wallet_statement //
CREATE PROCEDURE sp_wallet_statement(IN p_user_id BIGINT)
BEGIN
    SELECT t.txn_id, t.type, t.amount, t.balance_after, t.bill_id, t.created_at
      FROM wallet_transactions t
      JOIN wallets w ON w.wallet_id = t.wallet_id
     WHERE w.user_id = p_user_id
     ORDER BY t.txn_id DESC;
END //

-- The charger stays locked until the advance is paid.
DROP TRIGGER IF EXISTS trg_bookings_start_needs_payment //
CREATE TRIGGER trg_bookings_start_needs_payment
BEFORE UPDATE ON bookings
FOR EACH ROW
BEGIN
    IF NEW.status = 'IN_PROGRESS' AND OLD.status <> 'IN_PROGRESS' THEN
        IF NOT EXISTS (SELECT 1 FROM bills
                        WHERE booking_id = NEW.booking_id
                          AND payment_status = 'PAID') THEN
            SIGNAL SQLSTATE '45000'
               SET MESSAGE_TEXT = 'Charger locked: advance payment not received';
        END IF;
    END IF;
END //

-- The wallet ledger is append-only; corrections are new REFUND / DEBIT rows.
DROP TRIGGER IF EXISTS trg_wtxn_no_update //
CREATE TRIGGER trg_wtxn_no_update
BEFORE UPDATE ON wallet_transactions
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Wallet ledger is append-only: update not allowed';
END //

DROP TRIGGER IF EXISTS trg_wtxn_no_delete //
CREATE TRIGGER trg_wtxn_no_delete
BEFORE DELETE ON wallet_transactions
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Wallet ledger is append-only: delete not allowed';
END //

-- Every wallet balance must equal what its ledger adds up to.
CREATE OR REPLACE VIEW v_wallet_reconciliation AS
SELECT  w.wallet_id,
        w.user_id,
        w.balance                                        AS wallet_balance,
        COALESCE(SUM(CASE WHEN t.type IN ('TOPUP', 'REFUND') THEN t.amount
                          ELSE -t.amount END), 0)        AS ledger_balance,
        (w.balance = COALESCE(SUM(CASE WHEN t.type IN ('TOPUP', 'REFUND') THEN t.amount
                                       ELSE -t.amount END), 0)) AS is_consistent
FROM    wallets w
LEFT JOIN wallet_transactions t ON t.wallet_id = w.wallet_id
GROUP BY w.wallet_id, w.user_id, w.balance //

-- Revenue = final amount of SETTLED bills whose booking COMPLETED. Refunded
-- advances and cancelled bookings are not revenue.
CREATE OR REPLACE VIEW v_daily_revenue AS
SELECT  DATE(b.settled_at) AS revenue_date,
        COUNT(*)           AS sessions,
        SUM(b.energy_kwh)  AS total_kwh,
        SUM(b.amount)      AS revenue
FROM    bills b
JOIN    bookings bk ON bk.booking_id = b.booking_id
WHERE   b.payment_status = 'SETTLED' AND bk.status = 'COMPLETED'
GROUP BY DATE(b.settled_at) //

CREATE OR REPLACE VIEW v_weekly_revenue AS
SELECT  YEARWEEK(b.settled_at, 3) AS year_week,
        DATE_SUB(DATE(MIN(b.settled_at)), INTERVAL WEEKDAY(MIN(b.settled_at)) DAY) AS week_start,
        COUNT(*)           AS sessions,
        SUM(b.energy_kwh)  AS total_kwh,
        SUM(b.amount)      AS revenue
FROM    bills b
JOIN    bookings bk ON bk.booking_id = b.booking_id
WHERE   b.payment_status = 'SETTLED' AND bk.status = 'COMPLETED'
GROUP BY YEARWEEK(b.settled_at, 3) //

CREATE OR REPLACE VIEW v_monthly_revenue AS
SELECT  DATE_FORMAT(b.settled_at, '%Y-%m') AS revenue_month,
        COUNT(*)           AS sessions,
        SUM(b.energy_kwh)  AS total_kwh,
        SUM(b.amount)      AS revenue
FROM    bills b
JOIN    bookings bk ON bk.booking_id = b.booking_id
WHERE   b.payment_status = 'SETTLED' AND bk.status = 'COMPLETED'
GROUP BY DATE_FORMAT(b.settled_at, '%Y-%m') //

CREATE OR REPLACE VIEW v_revenue_by_charger_type AS
SELECT  s.charger_type,
        COUNT(*)           AS sessions,
        SUM(b.energy_kwh)  AS total_kwh,
        SUM(b.amount)      AS revenue
FROM    bills b
JOIN    bookings bk ON bk.booking_id = b.booking_id
JOIN    ev_charging_slots s ON s.id = bk.slot_id
WHERE   b.payment_status = 'SETTLED' AND bk.status = 'COMPLETED'
GROUP BY s.charger_type //
