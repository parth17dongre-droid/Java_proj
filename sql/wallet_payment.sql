-- =====================================================================
--  EV Charging - Wallet & Payment: database extras
--  Run AFTER schema.sql (and seed.sql):
--      mysql -u root -p ev_charging < wallet_payment.sql
--
--  Adds ONLY new objects. No existing table or column is changed.
--  Covers the DBMS rubric items "functions, procedures and triggers".
--  Money movement itself (top-up, pay advance, settle, refund) is done by
--  the Java backend with JDBC transactions; these objects protect and
--  report on that data.
-- =====================================================================

USE ev_charging;

DROP FUNCTION  IF EXISTS fn_advance_amount;
DROP FUNCTION  IF EXISTS fn_final_amount;
DROP PROCEDURE IF EXISTS sp_wallet_statement;
DROP TRIGGER   IF EXISTS trg_bookings_start_needs_payment;
DROP TRIGGER   IF EXISTS trg_wtxn_no_update;
DROP TRIGGER   IF EXISTS trg_wtxn_no_delete;
DROP VIEW      IF EXISTS v_wallet_reconciliation;

DELIMITER //

-- ---------------------------------------------------------------------
-- FUNCTION 1: advance payment for a slot
--   advance = power_kw x slot hours x rate_per_kwh
--   (the "worst case" cost: the charger delivering full power all slot)
-- ---------------------------------------------------------------------
CREATE FUNCTION fn_advance_amount(p_slot_id BIGINT)
RETURNS DECIMAL(10,2)
READS SQL DATA
BEGIN
    DECLARE v_amount DECIMAL(10,2);

    SELECT ROUND(c.power_kw * TIMESTAMPDIFF(MINUTE, s.start_time, s.end_time)
                 * c.rate_per_kwh / 60, 2)
      INTO v_amount
      FROM slots s
      JOIN chargers c ON c.charger_id = s.charger_id
     WHERE s.slot_id = p_slot_id;

    RETURN v_amount;          -- NULL if the slot does not exist
END //

-- ---------------------------------------------------------------------
-- FUNCTION 2: final bill = energy actually consumed x rate
-- ---------------------------------------------------------------------
CREATE FUNCTION fn_final_amount(p_energy_kwh DECIMAL(8,3), p_rate DECIMAL(8,2))
RETURNS DECIMAL(10,2)
DETERMINISTIC
BEGIN
    RETURN ROUND(p_energy_kwh * p_rate, 2);
END //

-- ---------------------------------------------------------------------
-- PROCEDURE: wallet statement for one user, newest entry first
-- ---------------------------------------------------------------------
CREATE PROCEDURE sp_wallet_statement(IN p_user_id BIGINT)
BEGIN
    SELECT t.txn_id, t.type, t.amount, t.balance_after, t.bill_id, t.created_at
      FROM wallet_transactions t
      JOIN wallets w ON w.wallet_id = t.wallet_id
     WHERE w.user_id = p_user_id
     ORDER BY t.txn_id DESC;
END //

-- ---------------------------------------------------------------------
-- TRIGGER 1: the charger stays locked until the advance is paid
--   A booking may only move to IN_PROGRESS (charger unlocked) if its bill
--   is PAID. Even a bug in the Java code cannot bypass this.
-- ---------------------------------------------------------------------
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

-- ---------------------------------------------------------------------
-- TRIGGERS 2 and 3: the wallet ledger is append-only
--   Corrections are made by adding a new REFUND / DEBIT row, never by
--   editing or deleting history.
-- ---------------------------------------------------------------------
CREATE TRIGGER trg_wtxn_no_update
BEFORE UPDATE ON wallet_transactions
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Wallet ledger is append-only: update not allowed';
END //

CREATE TRIGGER trg_wtxn_no_delete
BEFORE DELETE ON wallet_transactions
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Wallet ledger is append-only: delete not allowed';
END //

DELIMITER ;

-- ---------------------------------------------------------------------
-- VIEW: audit check. Every wallet balance must equal what its ledger adds
-- up to (TOPUP and REFUND add money, ADVANCE_DEBIT and FINAL_DEBIT remove it).
-- Any row with is_consistent = 0 means money went missing.
-- ---------------------------------------------------------------------
CREATE VIEW v_wallet_reconciliation AS
SELECT  w.wallet_id,
        w.user_id,
        w.balance                                        AS wallet_balance,
        COALESCE(SUM(CASE WHEN t.type IN ('TOPUP', 'REFUND') THEN t.amount
                          ELSE -t.amount END), 0)        AS ledger_balance,
        (w.balance = COALESCE(SUM(CASE WHEN t.type IN ('TOPUP', 'REFUND') THEN t.amount
                                       ELSE -t.amount END), 0)) AS is_consistent
FROM    wallets w
LEFT JOIN wallet_transactions t ON t.wallet_id = w.wallet_id
GROUP BY w.wallet_id, w.user_id, w.balance;
