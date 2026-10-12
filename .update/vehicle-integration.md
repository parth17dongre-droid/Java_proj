# Vehicle registration integration

## Database (`database/schema.sql`)
- Added `vehicles` (id, user_id FK -> users.user_id, make, model, year, battery_capacity_kwh, max_charging_power_kw) with CHECKs mirroring `VehicleRequest` validation.
- Added `vehicle_connectors` (vehicle_id FK ON DELETE CASCADE, connector_type enum CHECK) for `Vehicle.connectorTypes`.
- `Vehicle` entity already existed and matches; no new entities.

## Backend
- `VehicleService`: user lookup `findByEmail` -> `findByUsername` (JWT subject is the username; previously every call threw "User not found").

## Frontend
- `Vehiclesetuppage.jsx`: fixed CSS import casing; connectors now use backend enum values; submit POSTs to `/api/vehicles` via `apiFetch` (`batteryCapacityKwh`, `connectorTypes: [..]`), with loading + error display; removed `plate` (not stored).
- `App.jsx`: protected route `/vehicle-setup`; back/continue -> `/dashboard`.

## Verified
- `npm run build` passes; `./mvnw compile` passes. Not run end-to-end (needs MySQL + login).

## Known issues / not done
- `schema.sql` `users` (user_id, password_hash, phone, role) does NOT match the backend `Users` entity (id, username, hashed_password, refresh_token, created_acc_at). Use `ddl-auto=update` on an empty DB, or align `users` before loading schema.sql.
- Nothing links to `/vehicle-setup` yet; open it by URL.
- Plate number dropped; add a column + DTO field if needed.

## Dashboard rewrite
- `DashboardPage.jsx` + new `DashboardPage.css` (no inline styles; reference JSX used only for layout/theme, no copied data).
- Sidebar with "Add Vehicle" (-> `/vehicle-setup`), greeting from the logged-in user, stats and vehicle cards all computed from `GET /api/vehicles`.
- New `GET /api/users/me` (`UserController`) returns name/username/email for the greeting.
- Sessions, stations and wallet from the reference were left out: the backend has no endpoints for them, and showing static data was ruled out. Add them as those APIs appear.
- Inline SVG icons instead of lucide-react (not installed).
