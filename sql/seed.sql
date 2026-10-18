-- =====================================================================
--  EV Charging Slot Booking System — demo seed data
--  Run AFTER schema.sql:   mysql -u root -p ev_charging < seed.sql
--
--  All dates are relative to the day you run this script (CURDATE()),
--  so there are always upcoming slots to book during a demo.
--
--  Demo logins (passwords are BCrypt-hashed below):
--    admin@evcharge.in   / Admin@123   (ADMIN)
--    aarav@example.com   / User@123    (USER)
--    sneha@example.com   / User@123    (USER)
--  Change these before showing the project to anyone outside the team.
-- =====================================================================

USE ev_charging;

START TRANSACTION;

-- ---------------------------------------------------------------------
-- Users + one wallet each (registration always creates both together)
-- ---------------------------------------------------------------------
INSERT INTO users (user_id, name, email, phone, password_hash, role) VALUES
 (1, 'Station Admin', 'admin@evcharge.in', '9000000001', '$2b$10$QtK1E.ow2Lwjq7Z572cx3.L6cMWgZF.wJ9fo.0PuAb43A4CohhnDu', 'ADMIN'),
 (2, 'Aarav Patil',   'aarav@example.com', '9000000002', '$2b$10$XeA/GzbvjI0QWYKNP7aQz.z7sUyygwHb9sDPsUYjN/T3FF1CWlStm', 'USER'),
 (3, 'Sneha Kulkarni','sneha@example.com', '9000000003', '$2b$10$XeA/GzbvjI0QWYKNP7aQz.z7sUyygwHb9sDPsUYjN/T3FF1CWlStm', 'USER');

-- Final balances already reflect the ledger entries further down.
INSERT INTO wallets (wallet_id, user_id, balance) VALUES
 (1, 1,   0.00),
 (2, 2, 911.00),
 (3, 3, 438.00);

-- ---------------------------------------------------------------------
-- Chargers: 2 FAST, 2 SLOW. SC-02 is under maintenance, so none of its
-- slots should appear in v_available_slots.
-- ---------------------------------------------------------------------
INSERT INTO chargers (charger_id, charger_code, location, type, power_kw, rate_per_kwh, status) VALUES
 (1, 'FC-01', 'Bay A, Ground Floor', 'FAST', 30.00, 18.00, 'ACTIVE'),
 (2, 'FC-02', 'Bay A, Ground Floor', 'FAST', 30.00, 18.00, 'ACTIVE'),
 (3, 'SC-01', 'Bay B, Basement',     'SLOW',  7.40, 10.00, 'ACTIVE'),
 (4, 'SC-02', 'Bay B, Basement',     'SLOW',  7.40, 10.00, 'MAINTENANCE');

-- ---------------------------------------------------------------------
-- Past slots (used by the completed bookings below)
-- ---------------------------------------------------------------------
INSERT INTO slots (slot_id, charger_id, start_time, end_time, status) VALUES
 (1, 1, CURDATE() - INTERVAL 7 DAY + INTERVAL 10 HOUR, CURDATE() - INTERVAL 7 DAY + INTERVAL 11 HOUR, 'AVAILABLE'),
 (2, 3, CURDATE() - INTERVAL 2 DAY + INTERVAL 14 HOUR, CURDATE() - INTERVAL 2 DAY + INTERVAL 16 HOUR, 'AVAILABLE'),
 (3, 3, CURDATE() - INTERVAL 1 DAY + INTERVAL  9 HOUR, CURDATE() - INTERVAL 1 DAY + INTERVAL 10 HOUR, 'AVAILABLE');

-- ---------------------------------------------------------------------
-- Upcoming slots: every charger, 1-hour slots starting 08:00 ... 20:00,
-- for the next 3 days (13 slots x 4 chargers x 3 days = 156 rows).
-- ---------------------------------------------------------------------
INSERT INTO slots (charger_id, start_time, end_time, status)
WITH RECURSIVE
  days  (d) AS (SELECT 1 UNION ALL SELECT d + 1 FROM days  WHERE d < 3),
  hours (h) AS (SELECT 8 UNION ALL SELECT h + 1 FROM hours WHERE h < 20)
SELECT c.charger_id,
       CURDATE() + INTERVAL days.d DAY + INTERVAL hours.h HOUR,
       CURDATE() + INTERVAL days.d DAY + INTERVAL hours.h + 1 HOUR,
       'AVAILABLE'
FROM chargers c
CROSS JOIN days
CROSS JOIN hours;

-- ---------------------------------------------------------------------
-- Bookings
--   1-3 : completed sessions in the past (feed the revenue reports)
--   4-5 : tomorrow, BOOKED but not yet paid -> charger still locked
-- ---------------------------------------------------------------------
INSERT INTO bookings (booking_id, user_id, slot_id, status, created_at) VALUES
 (1, 2, 1, 'COMPLETED', CURDATE() - INTERVAL 8 DAY + INTERVAL 18 HOUR),
 (2, 2, 2, 'COMPLETED', CURDATE() - INTERVAL 3 DAY + INTERVAL 20 HOUR),
 (3, 3, 3, 'COMPLETED', CURDATE() - INTERVAL 2 DAY + INTERVAL 21 HOUR);

INSERT INTO bookings (booking_id, user_id, slot_id, status)
SELECT 4, 2, s.slot_id, 'BOOKED'
FROM slots s JOIN chargers c ON c.charger_id = s.charger_id
WHERE c.charger_code = 'FC-01' AND s.start_time = CURDATE() + INTERVAL 1 DAY + INTERVAL 12 HOUR;

INSERT INTO bookings (booking_id, user_id, slot_id, status)
SELECT 5, 3, s.slot_id, 'BOOKED'
FROM slots s JOIN chargers c ON c.charger_id = s.charger_id
WHERE c.charger_code = 'SC-01' AND s.start_time = CURDATE() + INTERVAL 1 DAY + INTERVAL 10 HOUR;

UPDATE slots SET status = 'BOOKED'
WHERE slot_id IN (SELECT slot_id FROM bookings WHERE booking_id IN (4, 5));

-- ---------------------------------------------------------------------
-- Bills
--   advance = power_kw x hours x rate     (paid before unlock)
--   amount  = energy_kwh x rate           (final, after the session)
--   1: FC-01 1h  -> advance 30x1x18 = 540.00 | 24.5 kWh -> 441.00 | refund 99.00
--   2: SC-01 2h  -> advance 7.4x2x10 = 148.00 | 14.8 kWh -> 148.00 | no refund
--   3: SC-01 1h  -> advance 7.4x1x10 = 74.00  |  6.2 kWh ->  62.00 | refund 12.00
--   4: FC-01 1h  -> advance 540.00, PENDING (not paid yet)
--   5: SC-01 1h  -> advance  74.00, PENDING (not paid yet)
-- ---------------------------------------------------------------------
INSERT INTO bills (bill_id, booking_id, rate_per_kwh, advance_amount, energy_kwh, amount, payment_status, created_at, paid_at, settled_at) VALUES
 (1, 1, 18.00, 540.00, 24.500, 441.00, 'SETTLED',
     CURDATE() - INTERVAL 8 DAY + INTERVAL 18 HOUR,
     CURDATE() - INTERVAL 7 DAY + INTERVAL 10 HOUR,
     CURDATE() - INTERVAL 7 DAY + INTERVAL 11 HOUR),
 (2, 2, 10.00, 148.00, 14.800, 148.00, 'SETTLED',
     CURDATE() - INTERVAL 3 DAY + INTERVAL 20 HOUR,
     CURDATE() - INTERVAL 2 DAY + INTERVAL 14 HOUR,
     CURDATE() - INTERVAL 2 DAY + INTERVAL 16 HOUR),
 (3, 3, 10.00,  74.00,  6.200,  62.00, 'SETTLED',
     CURDATE() - INTERVAL 2 DAY + INTERVAL 21 HOUR,
     CURDATE() - INTERVAL 1 DAY + INTERVAL 9 HOUR,
     CURDATE() - INTERVAL 1 DAY + INTERVAL 10 HOUR),
 (4, 4, 18.00, 540.00, NULL, NULL, 'PENDING', NOW(), NULL, NULL),
 (5, 5, 10.00,  74.00, NULL, NULL, 'PENDING', NOW(), NULL, NULL);

-- ---------------------------------------------------------------------
-- Wallet ledger — balance_after must walk exactly to wallets.balance
--   Wallet 2: +1500 -> 1500, -540 -> 960, +99 -> 1059, -148 -> 911
--   Wallet 3: +500  -> 500,  -74  -> 426, +12 -> 438
-- ---------------------------------------------------------------------
INSERT INTO wallet_transactions (wallet_id, bill_id, type, amount, balance_after, created_at) VALUES
 (2, NULL, 'TOPUP',         1500.00, 1500.00, CURDATE() - INTERVAL 10 DAY + INTERVAL 9 HOUR),
 (2, 1,    'ADVANCE_DEBIT',  540.00,  960.00, CURDATE() - INTERVAL 7 DAY  + INTERVAL 10 HOUR),
 (2, 1,    'REFUND',          99.00, 1059.00, CURDATE() - INTERVAL 7 DAY  + INTERVAL 11 HOUR),
 (2, 2,    'ADVANCE_DEBIT',  148.00,  911.00, CURDATE() - INTERVAL 2 DAY  + INTERVAL 14 HOUR),
 (3, NULL, 'TOPUP',          500.00,  500.00, CURDATE() - INTERVAL 5 DAY  + INTERVAL 19 HOUR),
 (3, 3,    'ADVANCE_DEBIT',   74.00,  426.00, CURDATE() - INTERVAL 1 DAY  + INTERVAL 9 HOUR),
 (3, 3,    'REFUND',          12.00,  438.00, CURDATE() - INTERVAL 1 DAY  + INTERVAL 10 HOUR);

COMMIT;
