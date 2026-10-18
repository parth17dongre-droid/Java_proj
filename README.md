# EV.Bolts: slot booking + wallet payment

Spring Boot 4 (Java 21) + MySQL 8 + React. One backend app (`com.example.demo`) that also scans the wallet module (`com.evcharge.wallet`).

## Run it

1. MySQL: `CREATE DATABASE springboot_db;`
2. `cp backend/src/main/resources/application.properties.template backend/src/main/resources/application.properties` and fill in DB user/password, `jwt.secret` (`openssl rand -base64 32`), `seed.admin-password`.
3. `cd backend && ./mvnw spring-boot:run` (port 8081). On startup:
   - Hibernate creates `users`, `vehicles`, `ev_charging_stations`, `ev_charging_slots`.
   - `WalletSchemaInitializer` runs `resources/wallet_schema.sql`: `wallets`, `bookings`, `bills`, `wallet_transactions`, the function/procedure/triggers and the revenue + reconciliation views (idempotent). If MySQL has binary logging on, the DB user needs `SUPER` or `SET GLOBAL log_bin_trust_function_creators=1` to create triggers.
   - `DataSeeder` adds 3 Pune stations with 5 slots each (FAST/SLOW, power, rate) and, if `seed.admin-password` is set, an `admin` account.
4. `cd frontend && npm install && npm run dev` (Vite proxies `/api` to 8081).

## Flow

```
signup            -> wallet created (same transaction)
Find Stations     -> Select & Hold: slot AVAILABLE -> SOFT_LOCKED for 5 min (atomic UPDATE, one winner)
Pay & Book        -> POST /api/bookings {slotId, durationMinutes}, ONE transaction:
                     booking + bill (advance = power_kw x hours x rate, fn_advance_amount)
                     -> debit wallet (ADVANCE_DEBIT) -> bill PAID -> slot BOOKED
                     not enough balance -> everything rolls back, the hold stays, UI links to /wallet
My Bookings       -> Start charging (BOOKED -> IN_PROGRESS; DB trigger refuses unless bill PAID)
                  -> Cancel while BOOKED: full REFUND, slot freed
Admin tab         -> enter kWh used -> POST /api/bills/{id}/settle: final = kWh x rate,
                     REFUND of the unused advance (or FINAL_DEBIT for extra), booking COMPLETED, slot freed
```

The user always comes from the JWT (subject = username); no endpoint takes a `userId`. Roles: `users.role` (`USER` default, `ADMIN`), carried in the JWT; `@PreAuthorize("hasRole('ADMIN')")` is enforced (`@EnableMethodSecurity`).

## API

| Method and path | Who | Purpose |
|---|---|---|
| GET `/api/ev-stations/nearby?lat&lng` | user | stations with slots, nearest first |
| POST `/api/ev-stations/slots/{id}/lock` / `/release` | user | hold / free a slot |
| POST `/api/bookings` | user | confirm the hold and pay the advance |
| GET `/api/bookings/me` | user | my bookings with bill status |
| POST `/api/bookings/{id}/start` / `/cancel` | user | unlock charger / cancel with refund |
| GET `/api/wallet`, `/api/wallet/transactions` | user | balance, statement |
| POST `/api/wallet/topup` | user | card or UPI top-up via the simulated gateway |
| GET `/api/admin/bookings` | admin | live bookings |
| POST `/api/bills/{id}/settle` | admin | final bill `{ "energyKwh": 20 }` |
| GET `/api/admin/gateway-log` | admin | recent gateway attempts and worker threads |

Errors come back as `{ "error": "...", "message": "..." }`.

## Test data (simulated gateway)

Cards: `4111 1111 1111 1111` approved, `4000 0000 0000 0002` declined, any future expiry, any 3-digit CVV. UPI: `aarav@okbank` approved, `fail@okbank` declined.

## Tests

`EV_DB_TEST=true ./mvnw test -Dtest=SlotLockConcurrencyTest` (needs the MySQL above): 8 threads race for one slot, exactly one wins.

## Where the money rules live

| Rule | Where |
|---|---|
| No double booking | atomic `tryLock`, `findByIdForUpdate`, unique `bookings.active_slot_id` |
| Pay before unlock | trigger `trg_bookings_start_needs_payment` |
| Ledger is append-only | triggers `trg_wtxn_no_update` / `_no_delete` |
| Balances add up | view `v_wallet_reconciliation` (every row `is_consistent = 1`) |
| Revenue | `v_daily_revenue`, `v_weekly_revenue`, `v_monthly_revenue`, `v_revenue_by_charger_type` (COMPLETED bookings only) |
| Lock order (no deadlocks) | slot -> booking -> bill -> wallet |
| Java concepts | `PaymentMethod` -> `CardPayment`/`UpiPayment`, `PaymentException` hierarchy, `PaymentGateway` thread pool, `ConcurrentHashMap`, JDBC via `JdbcTemplate` |
