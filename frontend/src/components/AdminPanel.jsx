import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { evApi } from '../services/evApi.js';

const PERIODS = { daily: 'Daily', weekly: 'Weekly', monthly: 'Monthly', type: 'By charger type' };
const LABEL = (k) => k.replace(/_/g, ' ');

/** Admin tools: revenue report (SQL views) and slot maintenance switch. */
export default function AdminPanel({ refreshKey = 0 }) {
  const navigate = useNavigate();
  const [period, setPeriod] = useState('daily');
  const [rows, setRows] = useState([]);
  const [stations, setStations] = useState([]);
  const [msg, setMsg] = useState(null); // { ok, text }

  const loadRevenue = useCallback(async () => {
    try {
      setRows(await evApi.revenue(period, navigate));
    } catch (e) {
      setMsg({ ok: false, text: e.message });
    }
  }, [period, navigate]);

  const loadStations = useCallback(async () => {
    try {
      setStations(await evApi.getNearbyStations(18.5204, 73.8567, navigate));
    } catch (e) {
      setMsg({ ok: false, text: e.message });
    }
  }, [navigate]);

  useEffect(() => { loadRevenue(); }, [loadRevenue, refreshKey]);
  useEffect(() => { loadStations(); }, [loadStations]);

  const toggle = async (slot) => {
    setMsg(null);
    try {
      const res = await evApi.setMaintenance(slot.id, slot.status !== 'MAINTENANCE', navigate);
      setMsg({ ok: true, text: res.message });
    } catch (e) {
      setMsg({ ok: false, text: e.message });
    }
    loadStations();
    loadRevenue();
  };

  const cols = rows.length ? Object.keys(rows[0]) : [];
  const fmt = (k, v) => (k === 'revenue' && v != null ? `₹${Number(v).toFixed(2)}` : String(v ?? ''));

  return (
    <>
      {msg && <p className={msg.ok ? 'db-ok' : 'db-error'}>{msg.text}</p>}

      <section className="db-card" style={{ marginTop: 16 }}>
        <div className="db-section-head">
          <div className="db-section-title">Revenue</div>
          <select className="db-input" style={{ width: 'auto' }} value={period} onChange={(e) => setPeriod(e.target.value)}>
            {Object.entries(PERIODS).map(([k, l]) => <option key={k} value={k}>{l}</option>)}
          </select>
        </div>
        {rows.length === 0 ? (
          <div className="db-empty">No settled sessions yet.</div>
        ) : (
          <table className="db-table">
            <thead><tr>{cols.map((c) => <th key={c}>{LABEL(c)}</th>)}</tr></thead>
            <tbody>
              {rows.map((r, i) => <tr key={i}>{cols.map((c) => <td key={c}>{fmt(c, r[c])}</td>)}</tr>)}
            </tbody>
          </table>
        )}
      </section>

      <section className="db-card" style={{ marginTop: 16 }}>
        <div className="db-section-head"><div className="db-section-title">Slot maintenance</div></div>
        <p className="db-subtitle" style={{ marginTop: 0 }}>
          Putting a slot under maintenance cancels its booking and refunds the user.
        </p>
        {stations.map((st) => (
          <div key={st.id} style={{ marginTop: 12 }}>
            <div className="db-vehicle-name">{st.name}</div>
            <div className="db-bookings">
              {st.slots.map((sl) => (
                <div className="db-booking" key={sl.id}>
                  <div>
                    <strong>{sl.slotNumber}</strong> · {sl.chargerType} · {sl.powerKw} kW
                    <span className="db-vehicle-meta"> · {sl.status.replace('_', ' ')}</span>
                  </div>
                  <button className={`db-ghost-btn db-small ${sl.status === 'MAINTENANCE' ? '' : 'db-danger'}`} onClick={() => toggle(sl)}
                    disabled={sl.status === 'SOFT_LOCKED'}>
                    {sl.status === 'MAINTENANCE' ? 'Back in service' : 'Maintenance'}
                  </button>
                </div>
              ))}
            </div>
          </div>
        ))}
      </section>
    </>
  );
}
