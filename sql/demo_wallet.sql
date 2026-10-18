-- =====================================================================
--  Demo for the DBMS viva: one booking from "unpaid" to "settled".
--  Needs schema.sql + seed.sql + wallet_payment.sql already loaded.
--  Uses seed booking 4 / bill 4 (user 2 Aarav, wallet 2, FC-01, 1 hour).
--
--  Run it block by block in MySQL Workbench, or all at once:
--      mysql -u root -p ev_charging < demo_wallet.sql
--  The lines marked EXPECTED ERROR are commented out; run them by hand to
--  show the database rejecting bad actions.
-- =====================================================================
USE ev_charging;

-- 1. Function: advance for the slot of booking 4 (30 kW x 1 h x Rs 18 = 540)
SELECT fn_advance_amount(slot_id) AS advance_for_slot
FROM bookings WHERE booking_id = 4;

-- 2. Before payment: wallet and bill
SELECT balance AS wallet_before FROM wallets WHERE wallet_id = 2;          -- 911.00
SELECT bill_id, advance_amount, payment_status FROM bills WHERE bill_id = 4; -- PENDING

-- EXPECTED ERROR (trigger): unlocking the charger before paying
-- UPDATE bookings SET status = 'IN_PROGRESS' WHERE booking_id = 4;
--   -> ERROR 1644: Charger locked: advance payment not received

-- 3. Pay the advance. This is the same sequence the Java code runs in ONE transaction.
START TRANSACTION;
    SELECT bill_id FROM bills   WHERE bill_id = 4   FOR UPDATE;       -- lock the bill first
    SELECT balance FROM wallets WHERE wallet_id = 2 FOR UPDATE;       -- then the wallet
    UPDATE wallets SET balance = balance - 540.00, version = version + 1 WHERE wallet_id = 2;
    INSERT INTO wallet_transactions (wallet_id, bill_id, type, amount, balance_after)
         VALUES (2, 4, 'ADVANCE_DEBIT', 540.00, 371.00);              -- 911 - 540
    UPDATE bills SET payment_status = 'PAID', paid_at = NOW() WHERE bill_id = 4;
COMMIT;

-- 4. Now the trigger lets the session start (charger unlocked)
UPDATE bookings SET status = 'IN_PROGRESS' WHERE booking_id = 4;

-- 5. Session ends: charger reports 20.000 kWh. Final = 20 x 18 = 360, refund 540 - 360 = 180.
SELECT fn_final_amount(20.000, 18.00) AS final_amount;                -- 360.00
START TRANSACTION;
    SELECT bill_id FROM bills   WHERE bill_id = 4   FOR UPDATE;
    SELECT balance FROM wallets WHERE wallet_id = 2 FOR UPDATE;
    UPDATE wallets SET balance = balance + 180.00, version = version + 1 WHERE wallet_id = 2;
    INSERT INTO wallet_transactions (wallet_id, bill_id, type, amount, balance_after)
         VALUES (2, 4, 'REFUND', 180.00, 551.00);                     -- 371 + 180
    UPDATE bills SET energy_kwh = 20.000, amount = 360.00,
                     payment_status = 'SETTLED', settled_at = NOW() WHERE bill_id = 4;
    UPDATE bookings SET status = 'COMPLETED' WHERE booking_id = 4;
COMMIT;

-- 6. Procedure: statement, newest first
CALL sp_wallet_statement(2);

-- 7. Revenue views now include the new session
SELECT * FROM v_daily_revenue;
SELECT * FROM v_revenue_by_charger_type;

-- 8. Audit view: every wallet should show is_consistent = 1
SELECT * FROM v_wallet_reconciliation;

-- EXPECTED ERROR (trigger): editing or deleting history
-- UPDATE wallet_transactions SET amount = 1 WHERE txn_id = 1;
--   -> ERROR 1644: Wallet ledger is append-only: update not allowed
-- DELETE FROM wallet_transactions WHERE txn_id = 1;
--   -> ERROR 1644: Wallet ledger is append-only: delete not allowed

-- EXPECTED ERROR (CHECK constraint): spending more than the balance
-- UPDATE wallets SET balance = balance - 99999 WHERE wallet_id = 2;
--   -> ERROR 3819: Check constraint 'ck_wallets_balance' is violated
