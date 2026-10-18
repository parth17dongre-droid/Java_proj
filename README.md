# EV Charging: Wallet and Payment Gateway

Built on top of the team's `schema.sql` / `seed.sql` (MySQL 8, Spring Boot). No existing table or column is changed.

## Folder layout

```
sql/wallet_payment.sql      functions, triggers, procedure, audit view (run after schema + seed)
sql/demo_wallet.sql         one booking from unpaid to settled, for the DBMS demo
backend/src/main/java/com/evcharge/wallet/
    exception/              PaymentException + 4 subclasses, GlobalExceptionHandler
    gateway/                PaymentMethod (abstract) -> CardPayment, UpiPayment; PaymentGateway (thread pool)
    repository/             WalletRepository, BillRepository (JDBC, JdbcTemplate)
    service/                WalletService (all money movement), PaymentService (top-up)
    controller/             WalletController (REST)
    model/, dto/            records
backend/src/test/.../WalletConcurrencyTest.java
frontend/src/pages/WalletPage.jsx + WalletPage.css
```

## Setup

1. `mysql -u root -p < schema.sql`, then `mysql -u root -p ev_charging < seed.sql`, then `mysql -u root -p ev_charging < sql/wallet_payment.sql`
2. Copy `backend/src/...` into the Spring Boot project. If your base package is not `com.evcharge`, rename the package (IDE: Refactor > Rename).
3. Dependencies in `pom.xml` (most already exist): `spring-boot-starter-web`, `spring-boot-starter-jdbc`, `spring-boot-starter-validation`, `spring-boot-starter-security`, `mysql-connector-j`, `spring-boot-starter-test`. Java 17+.
4. `application.properties`:
   ```
   spring.datasource.url=jdbc:mysql://localhost:3306/ev_charging?serverTimezone=Asia/Kolkata
   spring.datasource.username=ev_app
   spring.datasource.password=change_me
   ```
5. `@PreAuthorize` needs `@EnableMethodSecurity` on your security config (or protect `/api/bills/*/settle` and `/api/admin/**` for ADMIN in your filter chain).
6. Frontend: copy the two files into `frontend/src/pages`, add `<Route path="/wallet" element={<WalletPage />} />`. If LoginPage stores the JWT under a key other than `token`, change `getToken()`.

## Hooks the other modules must call

| Where | What to call |
|---|---|
| Registration (same transaction as inserting the user) | `walletService.createWallet(userId)` |
| Booking creation | insert the bill with `advance_amount = fn_advance_amount(slot_id)` and `rate_per_kwh` copied from the charger |
| Booking cancel (same transaction, after status = CANCELLED) | `walletService.refundCancelledBooking(bookingId)` |
| Charger unlock (booking -> IN_PROGRESS) | nothing extra: the trigger rejects it unless the bill is PAID. Catch the SQL error (SQLSTATE 45000) and show "pay first" |
| Session end | `POST /api/bills/{billId}/settle` with `{ "energyKwh": 20.0 }` |

`settleBill` also marks the booking COMPLETED and frees the slot (the revenue views only count COMPLETED bookings). Tell the booking owner so they do not do it twice.

## API

| Method and path | Who | Purpose |
|---|---|---|
| GET `/api/wallet` | user | balance |
| GET `/api/wallet/transactions?limit=50` | user | statement |
| GET `/api/wallet/pending-bills` | user | unpaid advances |
| POST `/api/wallet/topup` | user | card or UPI top-up |
| POST `/api/bills/{id}/pay` | user | pay advance from wallet (unlocks charger) |
| POST `/api/bills/{id}/settle` | admin | final bill, refund or extra debit |
| GET `/api/admin/gateway-log` | admin | recent gateway attempts and worker threads |

The user always comes from the JWT (assumed subject = email, see `WalletController.currentUserId`).

## Test data for the simulated gateway

Cards: `4111 1111 1111 1111` approved, `4000 0000 0000 0002` declined. Any future expiry (MM/YY), any 3-digit CVV.
UPI: `aarav@okbank` approved, `fail@okbank` declined. Use test numbers only, never a real card.

## How money flows

```
top-up:   card/UPI -> PaymentGateway (worker thread) approved? -> credit wallet + TOPUP ledger row
pay:      lock booking -> lock bill -> lock wallet -> debit advance + ADVANCE_DEBIT row -> bill PAID
settle:   final = energy x rate; advance - final > 0 -> REFUND row, < 0 -> FINAL_DEBIT row; bill SETTLED
cancel:   PAID bill -> full REFUND row, bill SETTLED at 0
```
Each of pay, settle and cancel is one database transaction. Locks are always taken in the order booking, bill, wallet, so parallel requests queue instead of deadlocking.

## Requirements covered

| Project requirement | Where |
|---|---|
| 2 Billing on energy consumed | `settleBill`, `fn_final_amount` |
| 3 Pay bill from account, 8 Add money to wallet | `payAdvance`, `PaymentService.topUp` |
| 5 Concurrent bookings (payment side) | row locks, `WalletConcurrencyTest` |
| 7 Charger locked until paid | trigger `trg_bookings_start_needs_payment` |
| 10 Cancellation | `refundCancelledBooking` |
| 11 Fast/slow billing | rate is copied from the charger onto the bill |
| 12 Revenue | teammate's `v_daily_revenue` etc. (settled sessions feed them) |

Not in this module: login (4), booking and modify (1, 6), slot display (9), maintenance (13).

## Java rubric (10 marks)

| Concept | Where |
|---|---|
| Inheritance | `PaymentMethod` -> `CardPayment`, `UpiPayment`; `PaymentException` -> 4 subclasses |
| Collections | `ConcurrentHashMap` of gateway transactions, `List` results |
| Exception handling | custom exceptions, `GlobalExceptionHandler`, rollback on failure |
| JDBC CRUD | `WalletRepository`, `BillRepository` via `JdbcTemplate` (create wallet and ledger rows, read, update balance and bill status, delete unused wallet) |
| GUI | `WalletPage.jsx` (React). Check with your faculty that a web UI counts as the GUI |
| Thread | `PaymentGateway` fixed pool of 4 named workers; `WalletConcurrencyTest` |

## DBMS rubric points for the report

- Functions: `fn_advance_amount`, `fn_final_amount`. Procedure: `sp_wallet_statement`. Triggers: `trg_bookings_start_needs_payment`, `trg_wtxn_no_update`, `trg_wtxn_no_delete`. View: `v_wallet_reconciliation`.
- Functional dependencies: `wallet_id -> user_id, balance`; `txn_id -> wallet_id, bill_id, type, amount, balance_after`; `bill_id -> booking_id, rate_per_kwh, advance_amount, ...`. Every determinant is a key, so these tables are in BCNF.
- Deliberate redundancy to explain: `bills.rate_per_kwh` (copy of `chargers.rate_per_kwh`, so old bills never change), `bills.amount` (energy x rate), `wallet_transactions.balance_after` (snapshot). The reconciliation view proves they stay consistent.
- Anomalies without the ledger: lost update (two debits overwrite each other) is prevented by `FOR UPDATE` row locks.

## Known limits

- The gateway is a simulation. If a real gateway charged a card and the credit step then failed, money would be taken but not credited; real systems add webhooks and retries.
- Only the booking row is checked for "paid before IN_PROGRESS" at DB level; whether the charger hardware actually unlocks is outside this project.
