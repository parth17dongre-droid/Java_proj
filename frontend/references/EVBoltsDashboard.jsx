import React, { useState } from "react";
import {
  LayoutDashboard,
  Activity,
  MapPin,
  Wallet,
  Plus,
  LogOut,
  Bell,
  User,
  Settings,
  Zap,
  X,
  Car,
  Clock,
  ArrowUpRight,
  ArrowDownLeft,
  Battery,
  ChevronDown,
} from "lucide-react";

/* ---------- Theme tokens (taken from the login screen) ---------- */
const C = {
  bg: "#0A0E0C",
  surface: "#0F1412",
  surface2: "#141C18",
  border: "#1F2B25",
  green: "#00E676",
  greenSoft: "rgba(0, 230, 118, 0.12)",
  amber: "#FFC93C",
  text: "#EEF4F1",
  muted: "#7D8C85",
  danger: "#FF6B6B",
};

const css = `
  * { box-sizing: border-box; }
  .ev-root button { font-family: inherit; cursor: pointer; }
  .ev-root input, .ev-root select { font-family: inherit; }
  .ev-nav-item {
    display: flex; align-items: center; gap: 12px; width: 100%;
    padding: 11px 14px; border-radius: 10px; border: 1px solid transparent;
    background: transparent; color: ${C.muted}; font-size: 14px; font-weight: 500;
    text-align: left; transition: background .2s, color .2s;
  }
  .ev-nav-item:hover { color: ${C.text}; background: ${C.surface2}; }
  .ev-nav-item.active { color: ${C.green}; background: ${C.greenSoft}; border-color: rgba(0,230,118,.25); }
  .ev-btn-primary {
    display: flex; align-items: center; justify-content: center; gap: 8px; width: 100%;
    padding: 12px 16px; border-radius: 12px; border: none;
    background: ${C.green}; color: #04140A; font-weight: 700; font-size: 14px;
    transition: filter .2s;
  }
  .ev-btn-primary:hover { filter: brightness(1.08); }
  .ev-btn-ghost {
    display: inline-flex; align-items: center; gap: 8px; padding: 10px 16px;
    border-radius: 10px; border: 1px solid ${C.border}; background: transparent;
    color: ${C.text}; font-size: 13px; font-weight: 600;
  }
  .ev-btn-ghost:hover { border-color: ${C.green}; color: ${C.green}; }
  .ev-icon-btn {
    width: 40px; height: 40px; display: grid; place-items: center; border-radius: 10px;
    border: 1px solid ${C.border}; background: ${C.surface}; color: ${C.muted};
  }
  .ev-icon-btn:hover { color: ${C.text}; border-color: #2c3b34; }
  .ev-card {
    background: ${C.surface}; border: 1px solid ${C.border}; border-radius: 16px; padding: 22px;
  }
  .ev-row {
    display: flex; align-items: center; justify-content: space-between; gap: 12px;
    padding: 14px 0; border-bottom: 1px solid ${C.border};
  }
  .ev-row:last-child { border-bottom: none; }
  .ev-input {
    width: 100%; padding: 11px 13px; border-radius: 10px; border: 1px solid ${C.border};
    background: ${C.surface2}; color: ${C.text}; font-size: 14px; outline: none;
  }
  .ev-input:focus { border-color: ${C.green}; }
  .ev-label { font-size: 12px; color: ${C.muted}; margin-bottom: 6px; display: block; }
  .ev-grid-stats { display: grid; grid-template-columns: repeat(2, 1fr); gap: 14px; }
  .ev-grid-main { display: grid; grid-template-columns: 1.1fr 1fr; gap: 18px; }
  .ev-grid-bottom { display: grid; grid-template-columns: 1fr 1fr; gap: 18px; }
  .ev-grid-vehicles { display: grid; grid-template-columns: repeat(auto-fill, minmax(230px, 1fr)); gap: 14px; }
  .ev-dropdown {
    position: absolute; right: 0; top: 50px; width: 220px; background: ${C.surface2};
    border: 1px solid ${C.border}; border-radius: 12px; padding: 8px; z-index: 20;
    box-shadow: 0 16px 40px rgba(0,0,0,.45);
  }
  .ev-dropdown-item {
    display: flex; align-items: center; gap: 10px; width: 100%; padding: 10px;
    border-radius: 8px; border: none; background: transparent; color: ${C.text}; font-size: 13px; text-align: left;
  }
  .ev-dropdown-item:hover { background: ${C.surface}; }
  @media (max-width: 1000px) {
    .ev-grid-main, .ev-grid-bottom { grid-template-columns: 1fr; }
  }
  @media (max-width: 760px) {
    .ev-sidebar { display: none !important; }
    .ev-mobile-only { display: flex !important; }
    .ev-main { padding: 18px !important; }
  }
`;

/* ---------- Sample data ---------- */
const initialVehicles = [
  { id: 1, name: "Tesla Model Y", plate: "MH 12 AB 4521", battery: 87, capacityKwh: 75, range: "420 km", connector: "CCS2" },
  { id: 2, name: "Tata Nexon EV", plate: "MH 14 CD 9087", battery: 54, capacityKwh: 40.5, range: "312 km", connector: "CCS2" },
];

const recentSessions = [
  { id: 1, station: "EV.Bolts Koregaon Park", date: "Today, 9:14 AM", energy: "34.2 kWh", cost: "₹612", duration: "32 min" },
  { id: 2, station: "EV.Bolts Baner Hub", date: "Oct 9, 6:40 PM", energy: "18.6 kWh", cost: "₹335", duration: "21 min" },
  { id: 3, station: "EV.Bolts Hinjewadi Phase 2", date: "Oct 6, 12:05 PM", energy: "42.0 kWh", cost: "₹756", duration: "44 min" },
  { id: 4, station: "EV.Bolts Viman Nagar", date: "Oct 3, 8:20 AM", energy: "25.1 kWh", cost: "₹452", duration: "27 min" },
];

const stations = [
  { id: 1, name: "Koregaon Park Fast Hub", distance: "1.2 km", power: "240 kW", free: 3, total: 6 },
  { id: 2, name: "Baner Highstreet", distance: "3.8 km", power: "120 kW", free: 0, total: 4 },
  { id: 3, name: "Hinjewadi Phase 2", distance: "6.5 km", power: "240 kW", free: 5, total: 8 },
  { id: 4, name: "Viman Nagar Plaza", distance: "7.1 km", power: "60 kW", free: 2, total: 2 },
];

const transactions = [
  { id: 1, label: "Charging at Koregaon Park", date: "Today", amount: -612, type: "debit" },
  { id: 2, label: "Wallet top-up (UPI)", date: "Oct 9", amount: 2000, type: "credit" },
  { id: 3, label: "Charging at Hinjewadi Phase 2", date: "Oct 6", amount: -756, type: "debit" },
  { id: 4, label: "Referral bonus", date: "Oct 2", amount: 150, type: "credit" },
];

const NAV = [
  { key: "dashboard", label: "Dashboard", icon: LayoutDashboard },
  { key: "sessions", label: "Charging Sessions", icon: Activity },
  { key: "stations", label: "Find Stations", icon: MapPin },
  { key: "wallet", label: "Wallet", icon: Wallet },
];

/* ---------- Small components ---------- */
function ChargeRing({ percent, size = 170 }) {
  const stroke = 10;
  const r = (size - stroke) / 2;
  const circ = 2 * Math.PI * r;
  const offset = circ * (1 - percent / 100);
  return (
    <div style={{ position: "relative", width: size, height: size, margin: "0 auto" }}>
      <div
        style={{
          position: "absolute", inset: -18, borderRadius: "50%",
          background: "radial-gradient(circle, rgba(0,230,118,.18), transparent 65%)",
        }}
      />
      <svg width={size} height={size} style={{ transform: "rotate(-90deg)" }}>
        <circle cx={size / 2} cy={size / 2} r={r} stroke={C.border} strokeWidth={stroke} fill="none" />
        <circle
          cx={size / 2} cy={size / 2} r={r} stroke={C.green} strokeWidth={stroke} fill="none"
          strokeLinecap="round" strokeDasharray={circ} strokeDashoffset={offset}
          style={{ transition: "stroke-dashoffset .6s ease" }}
        />
      </svg>
      <div
        style={{
          position: "absolute", inset: 0, display: "flex", flexDirection: "column",
          alignItems: "center", justifyContent: "center",
        }}
      >
        <div style={{ fontSize: 40, fontWeight: 800, letterSpacing: -1 }}>{percent}%</div>
        <div style={{ fontSize: 12, color: C.muted, marginTop: 2 }}>Charged</div>
      </div>
    </div>
  );
}

function StatCard({ label, value, hint, icon: Icon }) {
  return (
    <div className="ev-card">
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <span style={{ fontSize: 13, color: C.muted }}>{label}</span>
        <span style={{ color: C.green }}><Icon size={18} /></span>
      </div>
      <div style={{ fontSize: 26, fontWeight: 700, marginTop: 10, letterSpacing: -0.5 }}>{value}</div>
      <div style={{ fontSize: 12, color: C.muted, marginTop: 4 }}>{hint}</div>
    </div>
  );
}

function AddVehicleModal({ onClose, onSave }) {
  const [form, setForm] = useState({ name: "", plate: "", capacityKwh: "", range: "", connector: "CCS2" });
  const [error, setError] = useState("");

  const update = (key) => (e) => setForm({ ...form, [key]: e.target.value });

  const submit = () => {
    if (!form.name.trim() || !form.plate.trim()) {
      setError("Vehicle name and registration number are required.");
      return;
    }
    onSave({
      id: Date.now(),
      name: form.name.trim(),
      plate: form.plate.trim().toUpperCase(),
      battery: 0,
      capacityKwh: Number(form.capacityKwh) || 0,
      range: form.range ? `${form.range} km` : "—",
      connector: form.connector,
    });
    onClose();
  };

  return (
    <div
      onClick={onClose}
      style={{
        position: "fixed", inset: 0, background: "rgba(0,0,0,.6)", display: "grid",
        placeItems: "center", zIndex: 50, padding: 20,
      }}
    >
      <div
        className="ev-card"
        onClick={(e) => e.stopPropagation()}
        style={{ width: "100%", maxWidth: 440, padding: 26, background: C.surface2 }}
      >
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
          <div>
            <div style={{ fontSize: 18, fontWeight: 700 }}>Add a vehicle</div>
            <div style={{ fontSize: 13, color: C.muted, marginTop: 4 }}>Save your EV to charge faster.</div>
          </div>
          <button className="ev-icon-btn" onClick={onClose} aria-label="Close"><X size={18} /></button>
        </div>

        <div style={{ display: "grid", gap: 14 }}>
          <div>
            <label className="ev-label">Vehicle name</label>
            <input className="ev-input" placeholder="e.g. Tesla Model 3" value={form.name} onChange={update("name")} />
          </div>
          <div>
            <label className="ev-label">Registration number</label>
            <input className="ev-input" placeholder="MH 12 AB 1234" value={form.plate} onChange={update("plate")} />
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
            <div>
              <label className="ev-label">Battery (kWh)</label>
              <input className="ev-input" type="number" placeholder="60" value={form.capacityKwh} onChange={update("capacityKwh")} />
            </div>
            <div>
              <label className="ev-label">Range (km)</label>
              <input className="ev-input" type="number" placeholder="400" value={form.range} onChange={update("range")} />
            </div>
          </div>
          <div>
            <label className="ev-label">Connector type</label>
            <select className="ev-input" value={form.connector} onChange={update("connector")}>
              <option>CCS2</option>
              <option>CHAdeMO</option>
              <option>Type 2 (AC)</option>
              <option>Bharat DC-001</option>
            </select>
          </div>
          {error && <div style={{ fontSize: 13, color: C.danger }}>{error}</div>}
          <button className="ev-btn-primary" onClick={submit} style={{ marginTop: 6 }}>
            <Plus size={16} /> Save vehicle
          </button>
        </div>
      </div>
    </div>
  );
}

/* ---------- Views ---------- */
function DashboardView({ vehicles, onOpenWallet }) {
  const active = vehicles[0];
  return (
    <>
      <div style={{ marginBottom: 26 }}>
        <h1 style={{ fontSize: 28, fontWeight: 800, letterSpacing: -0.6, margin: 0 }}>Good afternoon, Aarav.</h1>
        <p style={{ color: C.muted, margin: "6px 0 0", fontSize: 14 }}>
          {active ? `${active.name} is at ${active.battery}%. Here is your charging overview.` : "Add a vehicle to start tracking your charging."}
        </p>
      </div>

      <div className="ev-grid-main" style={{ marginBottom: 18 }}>
        <div className="ev-card" style={{ display: "flex", flexDirection: "column", gap: 20 }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <div>
              <div style={{ fontSize: 13, color: C.muted }}>Active session</div>
              <div style={{ fontSize: 16, fontWeight: 600, marginTop: 4 }}>EV.Bolts Koregaon Park · Bay 3</div>
            </div>
            <span style={{ fontSize: 12, color: C.green, background: C.greenSoft, padding: "5px 10px", borderRadius: 999 }}>
              ● Charging
            </span>
          </div>
          <ChargeRing percent={active ? active.battery : 0} />
          <div style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 10, textAlign: "center" }}>
            {[
              ["Power", "240 kW"],
              ["Added", "34.2 kWh"],
              ["Time left", "18 min"],
            ].map(([k, v]) => (
              <div key={k} style={{ background: C.surface2, borderRadius: 12, padding: "12px 6px" }}>
                <div style={{ fontSize: 11, color: C.muted }}>{k}</div>
                <div style={{ fontSize: 15, fontWeight: 700, marginTop: 4 }}>{v}</div>
              </div>
            ))}
          </div>
          <button className="ev-btn-ghost" style={{ justifyContent: "center" }}>Stop charging</button>
        </div>

        <div className="ev-grid-stats">
          <StatCard label="Energy this month" value="412 kWh" hint="↑ 12% vs September" icon={Zap} />
          <StatCard label="Sessions" value="18" hint="Avg. 26 min each" icon={Activity} />
          <StatCard label="Total spent" value="₹6,240" hint="Across 4 stations" icon={ArrowUpRight} />
          <StatCard label="Wallet balance" value="₹1,284" hint="Tap to add money" icon={Wallet} />
        </div>
      </div>

      <div className="ev-grid-bottom" style={{ marginBottom: 18 }}>
        <div className="ev-card">
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 6 }}>
            <div style={{ fontSize: 16, fontWeight: 700 }}>Recent sessions</div>
            <span style={{ fontSize: 12, color: C.muted }}>Last 4</span>
          </div>
          {recentSessions.map((s) => (
            <div className="ev-row" key={s.id}>
              <div>
                <div style={{ fontSize: 14, fontWeight: 600 }}>{s.station}</div>
                <div style={{ fontSize: 12, color: C.muted, marginTop: 3 }}>{s.date} · {s.duration}</div>
              </div>
              <div style={{ textAlign: "right" }}>
                <div style={{ fontSize: 14, fontWeight: 600 }}>{s.cost}</div>
                <div style={{ fontSize: 12, color: C.muted, marginTop: 3 }}>{s.energy}</div>
              </div>
            </div>
          ))}
        </div>

        <div className="ev-card">
          <div style={{ fontSize: 16, fontWeight: 700, marginBottom: 6 }}>Stations near you</div>
          {stations.slice(0, 4).map((st) => (
            <div className="ev-row" key={st.id}>
              <div>
                <div style={{ fontSize: 14, fontWeight: 600 }}>{st.name}</div>
                <div style={{ fontSize: 12, color: C.muted, marginTop: 3 }}>{st.distance} · {st.power}</div>
              </div>
              <span
                style={{
                  fontSize: 12, fontWeight: 600, padding: "4px 10px", borderRadius: 999,
                  color: st.free > 0 ? C.green : C.muted,
                  background: st.free > 0 ? C.greenSoft : C.surface2,
                }}
              >
                {st.free > 0 ? `${st.free} free` : "Busy"}
              </span>
            </div>
          ))}
        </div>
      </div>

      <div className="ev-card">
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
          <div style={{ fontSize: 16, fontWeight: 700 }}>My vehicles</div>
          <button className="ev-btn-ghost" onClick={onOpenWallet} style={{ display: "none" }} />
        </div>
        {vehicles.length === 0 ? (
          <div style={{ color: C.muted, fontSize: 14 }}>No vehicles yet. Use "Add Vehicle" in the sidebar.</div>
        ) : (
          <div className="ev-grid-vehicles">
            {vehicles.map((v) => (
              <div key={v.id} style={{ background: C.surface2, border: `1px solid ${C.border}`, borderRadius: 14, padding: 16 }}>
                <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                  <div className="ev-icon-btn" style={{ width: 36, height: 36, color: C.green }}><Car size={18} /></div>
                  <div>
                    <div style={{ fontSize: 14, fontWeight: 600 }}>{v.name}</div>
                    <div style={{ fontSize: 12, color: C.muted }}>{v.plate}</div>
                  </div>
                </div>
                <div style={{ display: "flex", justifyContent: "space-between", marginTop: 14, fontSize: 12, color: C.muted }}>
                  <span>{v.capacityKwh ? `${v.capacityKwh} kWh` : "—"}</span>
                  <span>{v.range}</span>
                  <span>{v.connector}</span>
                </div>
                <div style={{ height: 6, background: C.border, borderRadius: 999, marginTop: 12, overflow: "hidden" }}>
                  <div style={{ width: `${v.battery}%`, height: "100%", background: C.green, borderRadius: 999 }} />
                </div>
                <div style={{ fontSize: 12, color: C.muted, marginTop: 6 }}>{v.battery}% charged</div>
              </div>
            ))}
          </div>
        )}
      </div>
    </>
  );
}

function SessionsView() {
  const all = [...recentSessions, { id: 5, station: "EV.Bolts Aundh Road", date: "Sep 28, 7:10 PM", energy: "29.8 kWh", cost: "₹537", duration: "30 min" }];
  return (
    <>
      <h1 style={{ fontSize: 28, fontWeight: 800, letterSpacing: -0.6, margin: "0 0 22px" }}>Charging sessions</h1>
      <div className="ev-card">
        {all.map((s) => (
          <div className="ev-row" key={s.id}>
            <div>
              <div style={{ fontSize: 14, fontWeight: 600 }}>{s.station}</div>
              <div style={{ fontSize: 12, color: C.muted, marginTop: 3 }}>
                <Clock size={11} style={{ verticalAlign: "-1px", marginRight: 4 }} />
                {s.date} · {s.duration}
              </div>
            </div>
            <div style={{ textAlign: "right" }}>
              <div style={{ fontSize: 14, fontWeight: 600 }}>{s.cost}</div>
              <div style={{ fontSize: 12, color: C.muted, marginTop: 3 }}>{s.energy}</div>
            </div>
          </div>
        ))}
      </div>
    </>
  );
}

function StationsView() {
  return (
    <>
      <h1 style={{ fontSize: 28, fontWeight: 800, letterSpacing: -0.6, margin: "0 0 22px" }}>Find stations</h1>
      <div className="ev-grid-vehicles" style={{ gridTemplateColumns: "repeat(auto-fill, minmax(260px, 1fr))" }}>
        {stations.map((st) => (
          <div className="ev-card" key={st.id}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
              <div>
                <div style={{ fontSize: 15, fontWeight: 700 }}>{st.name}</div>
                <div style={{ fontSize: 12, color: C.muted, marginTop: 4 }}>{st.distance} away</div>
              </div>
              <span style={{ color: C.green }}><MapPin size={18} /></span>
            </div>
            <div style={{ display: "flex", gap: 8, marginTop: 16, flexWrap: "wrap" }}>
              <span className="ev-btn-ghost" style={{ padding: "6px 10px", fontSize: 12, cursor: "default" }}>{st.power}</span>
              <span className="ev-btn-ghost" style={{ padding: "6px 10px", fontSize: 12, cursor: "default" }}>
                {st.free}/{st.total} free
              </span>
            </div>
            <button className="ev-btn-primary" style={{ marginTop: 16, padding: 10 }} disabled={st.free === 0}>
              {st.free === 0 ? "Fully occupied" : "Start charging"}
            </button>
          </div>
        ))}
      </div>
    </>
  );
}

function WalletView({ balance, onAdd }) {
  return (
    <>
      <h1 style={{ fontSize: 28, fontWeight: 800, letterSpacing: -0.6, margin: "0 0 22px" }}>Wallet</h1>
      <div className="ev-grid-main">
        <div className="ev-card" style={{ background: "linear-gradient(160deg, #0F2A1D 0%, #0F1412 70%)" }}>
          <div style={{ fontSize: 13, color: C.muted }}>Available balance</div>
          <div style={{ fontSize: 40, fontWeight: 800, letterSpacing: -1, marginTop: 8 }}>₹{balance.toLocaleString("en-IN", { minimumFractionDigits: 2 })}</div>
          <div style={{ fontSize: 13, color: C.muted, marginTop: 6 }}>Use it to pay for charging at any EV.Bolts station.</div>
          <div style={{ fontSize: 13, color: C.muted, marginTop: 26, marginBottom: 10 }}>Add money</div>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 10 }}>
            {[500, 1000, 2000].map((amt) => (
              <button key={amt} className="ev-btn-ghost" style={{ justifyContent: "center" }} onClick={() => onAdd(amt)}>
                + ₹{amt}
              </button>
            ))}
          </div>
        </div>

        <div className="ev-card">
          <div style={{ fontSize: 16, fontWeight: 700, marginBottom: 6 }}>Transactions</div>
          {transactions.map((t) => (
            <div className="ev-row" key={t.id}>
              <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
                <span
                  style={{
                    width: 34, height: 34, borderRadius: 10, display: "grid", placeItems: "center",
                    background: t.type === "credit" ? C.greenSoft : C.surface2,
                    color: t.type === "credit" ? C.green : C.muted,
                  }}
                >
                  {t.type === "credit" ? <ArrowDownLeft size={16} /> : <ArrowUpRight size={16} />}
                </span>
                <div>
                  <div style={{ fontSize: 14, fontWeight: 600 }}>{t.label}</div>
                  <div style={{ fontSize: 12, color: C.muted, marginTop: 3 }}>{t.date}</div>
                </div>
              </div>
              <div style={{ fontSize: 14, fontWeight: 700, color: t.type === "credit" ? C.green : C.text }}>
                {t.amount > 0 ? "+" : "−"}₹{Math.abs(t.amount)}
              </div>
            </div>
          ))}
        </div>
      </div>
    </>
  );
}

/* ---------- Main shell ---------- */
export default function EVBoltsDashboard({ onLogout }) {
  const [view, setView] = useState("dashboard");
  const [vehicles, setVehicles] = useState(initialVehicles);
  const [showAddVehicle, setShowAddVehicle] = useState(false);
  const [showProfile, setShowProfile] = useState(false);
  const [balance, setBalance] = useState(1284.5);

  const titles = { dashboard: "Dashboard", sessions: "Charging Sessions", stations: "Find Stations", wallet: "Wallet" };

  return (
    <div className="ev-root" style={{ minHeight: "100vh", background: C.bg, color: C.text, display: "flex" }}>
      <style>{css}</style>

      {/* Sidebar */}
      <aside
        className="ev-sidebar"
        style={{
          width: 250, flexShrink: 0, background: C.surface, borderRight: `1px solid ${C.border}`,
          padding: "24px 16px", display: "flex", flexDirection: "column", position: "sticky", top: 0, height: "100vh",
        }}
      >
        <div style={{ display: "flex", alignItems: "center", gap: 12, padding: "0 8px", marginBottom: 30 }}>
          <div
            style={{
              width: 42, height: 42, borderRadius: 12, border: `1px solid ${C.green}`,
              display: "grid", placeItems: "center", color: C.amber, background: C.bg,
            }}
          >
            <Zap size={20} fill={C.amber} />
          </div>
          <div>
            <div style={{ color: C.green, fontWeight: 800, fontSize: 18, letterSpacing: -0.3 }}>EV.Bolts</div>
            <div style={{ fontSize: 10, color: C.muted, letterSpacing: 2 }}>EV CHARGING</div>
          </div>
        </div>

        <button className="ev-btn-primary" onClick={() => setShowAddVehicle(true)} style={{ marginBottom: 22 }}>
          <Plus size={16} /> Add Vehicle
        </button>

        <nav style={{ display: "grid", gap: 4 }}>
          {NAV.map(({ key, label, icon: Icon }) => (
            <button key={key} className={`ev-nav-item ${view === key ? "active" : ""}`} onClick={() => setView(key)}>
              <Icon size={18} />
              {label}
              {key === "wallet" && (
                <span style={{ marginLeft: "auto", fontSize: 12, color: C.muted }}>₹{Math.round(balance)}</span>
              )}
            </button>
          ))}
        </nav>

        <div style={{ marginTop: "auto", borderTop: `1px solid ${C.border}`, paddingTop: 16 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 10, padding: "0 8px 14px" }}>
            <div
              style={{
                width: 34, height: 34, borderRadius: "50%", background: C.greenSoft, color: C.green,
                display: "grid", placeItems: "center", fontWeight: 700, fontSize: 13,
              }}
            >
              AR
            </div>
            <div>
              <div style={{ fontSize: 13, fontWeight: 600 }}>Aarav Rao</div>
              <div style={{ fontSize: 11, color: C.muted }}>aarav@example.com</div>
            </div>
          </div>
          <button className="ev-nav-item" onClick={onLogout} style={{ color: C.danger }}>
            <LogOut size={18} /> Log out
          </button>
        </div>
      </aside>

      {/* Main */}
      <div style={{ flex: 1, minWidth: 0, display: "flex", flexDirection: "column" }}>
        <header
          style={{
            display: "flex", justifyContent: "space-between", alignItems: "center",
            padding: "18px 32px", borderBottom: `1px solid ${C.border}`, position: "sticky", top: 0,
            background: "rgba(10,14,12,.85)", backdropFilter: "blur(8px)", zIndex: 10,
          }}
        >
          <div style={{ fontSize: 14, color: C.muted }}>
            EV.Bolts / <span style={{ color: C.text }}>{titles[view]}</span>
          </div>

          <div style={{ display: "flex", alignItems: "center", gap: 10, position: "relative" }}>
            <button className="ev-icon-btn" aria-label="Notifications"><Bell size={18} /></button>
            <button
              onClick={() => setShowProfile((s) => !s)}
              style={{
                display: "flex", alignItems: "center", gap: 8, padding: "4px 10px 4px 4px",
                borderRadius: 999, border: `1px solid ${C.border}`, background: C.surface, color: C.text,
              }}
              aria-label="Profile menu"
            >
              <span
                style={{
                  width: 32, height: 32, borderRadius: "50%", background: C.green, color: "#04140A",
                  display: "grid", placeItems: "center", fontWeight: 800, fontSize: 12,
                }}
              >
                <User size={16} />
              </span>
              <ChevronDown size={14} color={C.muted} />
            </button>

            {showProfile && (
              <div className="ev-dropdown">
                <div style={{ padding: "8px 10px 12px", borderBottom: `1px solid ${C.border}`, marginBottom: 6 }}>
                  <div style={{ fontSize: 14, fontWeight: 600 }}>Aarav Rao</div>
                  <div style={{ fontSize: 12, color: C.muted }}>aarav@example.com</div>
                </div>
                <button className="ev-dropdown-item"><User size={15} /> My profile</button>
                <button className="ev-dropdown-item"><Settings size={15} /> Settings</button>
              </div>
            )}
          </div>
        </header>

        <main className="ev-main" style={{ padding: 32, width: "100%", maxWidth: 1200 }}>
          {view === "dashboard" && <DashboardView vehicles={vehicles} onOpenWallet={() => setView("wallet")} />}
          {view === "sessions" && <SessionsView />}
          {view === "stations" && <StationsView />}
          {view === "wallet" && <WalletView balance={balance} onAdd={(amt) => setBalance((b) => b + amt)} />}
        </main>
      </div>

      {showAddVehicle && (
        <AddVehicleModal
          onClose={() => setShowAddVehicle(false)}
          onSave={(v) => {
            setVehicles((prev) => [...prev, v]);
            setView("dashboard");
          }}
        />
      )}
    </div>
  );
}
