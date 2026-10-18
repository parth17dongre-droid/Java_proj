import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { evApi } from '../services/evApi.js';

const rupees = (n) => `₹${Number(n).toFixed(2)}`;
const BADGE = { BOOKED: 'db-badge booked', IN_PROGRESS: 'db-badge progress', COMPLETED: 'db-badge done', CANCELLED: 'db-badge cancelled' };

/** "My bookings" for a user; with admin=true, every live booking with a settle form. */
export default function BookingsPanel({ admin = false, onChange }) {
  const navigate = useNavigate();
  const [rows, setRows] = useState([]);
  const [energy, setEnergy] = useState({});
  const [msg, setMsg] = useState(null); // { ok, text }
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      setRows(await (admin ? evApi.liveBookings(navigate) : evApi.myBookings(navigate)));
    } catch (e) {
      setMsg({ ok: false, text: e.message });
    }
  }, [admin, navigate]);

  useEffect(() => { load(); }, [load]);

  const run = async (fn, okText) => {
    setBusy(true);
    setMsg(null);
    try {
      const res = await fn();
      setMsg({ ok: true, text: okText(res) });
      await load();
      onChange?.();
    } catch (e) {
      setMsg({ ok: false, text: e.message });
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="db-card">
      <div className="db-section-head">
        <div className="db-section-title">{admin ? 'Live sessions (admin)' : 'My bookings'}</div>
        <button className="db-ghost-btn" onClick={load}>Refresh</button>
      </div>
      {msg && <p className={msg.ok ? 'db-ok' : 'db-error'}>{msg.text}</p>}

      {rows.length === 0 ? (
        <div className="db-empty">{admin ? 'No live bookings.' : 'No bookings yet. Find a station and book a slot.'}</div>
      ) : (
        <div className="db-bookings">
          {rows.map((b) => (
            <div className="db-booking" key={b.bookingId}>
              <div>
                <div className="db-vehicle-name">{b.stationName} · Slot {b.slotNumber}</div>
                <div className="db-vehicle-meta">
                  {b.chargerType} · {b.durationMinutes} min · advance {rupees(b.advanceAmount)}
                  {b.amount != null && ` · final ${rupees(b.amount)} (${b.energyKwh} kWh)`}
                </div>
              </div>
              <div className="db-booking-actions">
                <span className={BADGE[b.status]}>{b.status.replace('_', ' ')}</span>
                {!admin && b.status === 'BOOKED' && (
                  <>
                    <button className="db-primary-btn db-small" disabled={busy}
                      onClick={() => run(() => evApi.startBooking(b.bookingId, navigate), () => 'Charger unlocked. Happy charging!')}>
                      Start charging
                    </button>
                    <button className="db-ghost-btn db-small db-danger" disabled={busy}
                      onClick={() => run(() => evApi.cancelBooking(b.bookingId, navigate), (r) => `Cancelled. ${rupees(r.refunded)} refunded to your wallet.`)}>
                      Cancel
                    </button>
                  </>
                )}
                {admin && (
                  <>
                    <input className="db-input" type="number" min="0" step="0.001" placeholder="kWh used"
                      value={energy[b.billId] ?? ''} onChange={(e) => setEnergy({ ...energy, [b.billId]: e.target.value })} />
                    <button className="db-primary-btn db-small" disabled={busy || energy[b.billId] === undefined || energy[b.billId] === ''}
                      onClick={() => run(() => evApi.settle(b.billId, Number(energy[b.billId]), navigate),
                        (r) => `Settled: bill ${rupees(r.finalAmount)}, refunded ${rupees(r.refunded)}, extra charged ${rupees(r.extra)}.` +
                          (Number(r.shortfall) > 0 ? ` Wallet was short by ${rupees(r.shortfall)} (not collected).` : ''))}>
                      Settle
                    </button>
                  </>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}
