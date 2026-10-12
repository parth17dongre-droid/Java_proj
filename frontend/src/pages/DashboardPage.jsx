import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { apiFetch } from '../services/apiClient.js';
import './DashboardPage.css';

const CONNECTOR_LABELS = {
  TYPE_1: 'Type 1',
  TYPE_2: 'Type 2',
  CCS1: 'CCS1',
  CCS2: 'CCS2',
  CHADEMO: 'CHAdeMO',
  GB_T: 'GB/T',
  BHARAT_AC_001: 'Bharat AC-001',
  BHARAT_DC_001: 'Bharat DC-001',
  TESLA: 'Tesla',
};

const greeting = () => {
  const h = new Date().getHours();
  return h < 12 ? 'Good morning' : h < 18 ? 'Good afternoon' : 'Good evening';
};

const initials = (name = '') =>
  name.split(/\s+/).filter(Boolean).slice(0, 2).map((w) => w[0].toUpperCase()).join('') || '?';

export default function DashboardPage() {
  const navigate = useNavigate();
  const [user, setUser] = useState(null);
  const [vehicles, setVehicles] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const [meRes, vehRes] = await Promise.all([
          apiFetch('/api/users/me', {}, navigate),
          apiFetch('/api/vehicles', {}, navigate),
        ]);
        if (cancelled || !meRes || !vehRes) return;
        if (!meRes.ok || !vehRes.ok) throw new Error('Could not load your data.');
        setUser(await meRes.json());
        setVehicles(await vehRes.json());
      } catch (err) {
        if (!cancelled) setError(err.message);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
  }, [navigate]);

  const handleLogout = async () => {
    try {
      await apiFetch('/api/auth/logout', { method: 'POST' }, navigate);
    } finally {
      navigate('/');
    }
  };

  const addVehicle = () => navigate('/vehicle-setup');
  const totalKwh = vehicles.reduce((sum, v) => sum + (v.batteryCapacityKwh || 0), 0);
  const connectorCount = new Set(vehicles.flatMap((v) => v.connectorTypes)).size;
  const firstName = user?.name?.split(' ')[0];

  return (
    <div className="db-page">
      <aside className="db-sidebar">
        <div className="db-brand">
          <div className="db-logo"><BoltIcon /></div>
          <div>
            <div className="db-brand-name">EV.Bolts</div>
            <div className="db-brand-sub">EV CHARGING</div>
          </div>
        </div>

        <button className="db-primary-btn" onClick={addVehicle}>
          <PlusIcon /> Add Vehicle
        </button>

        <nav className="db-nav">
          <button className="db-nav-item"><GridIcon /> Dashboard</button>
        </nav>

        <div className="db-user">
          {user && (
            <div className="db-user-row">
              <div className="db-avatar">{initials(user.name)}</div>
              <div style={{ minWidth: 0 }}>
                <div className="db-user-name">{user.name}</div>
                {user.email && <div className="db-user-email">{user.email}</div>}
              </div>
            </div>
          )}
          <button className="db-logout" onClick={handleLogout}>
            <LogoutIcon /> Log out
          </button>
        </div>
      </aside>

      <main className="db-content">
        <h1 className="db-title">{greeting()}{firstName ? `, ${firstName}` : ''}.</h1>
        <p className="db-subtitle">
          {loading
            ? 'Loading your dashboard...'
            : vehicles.length
              ? 'Here is your garage overview.'
              : 'Add a vehicle to start charging.'}
        </p>
        {error && <p className="db-error">{error}</p>}

        <div className="db-stats">
          <Stat label="Vehicles" value={vehicles.length} hint="Registered to your account" />
          <Stat label="Total battery" value={`${+totalKwh.toFixed(1)} kWh`} hint="Across all vehicles" />
          <Stat label="Connector types" value={connectorCount} hint="Distinct plugs supported" />
        </div>

        <section className="db-card">
          <div className="db-section-head">
            <div className="db-section-title">My vehicles</div>
            <button className="db-ghost-btn" onClick={addVehicle}>+ Add vehicle</button>
          </div>

          {!loading && vehicles.length === 0 ? (
            <div className="db-empty">No vehicles yet. Click "Add Vehicle" to register one.</div>
          ) : (
            <div className="db-vehicles">
              {vehicles.map((v) => (
                <div className="db-vehicle" key={v.id}>
                  <div className="db-vehicle-head">
                    <div className="db-vehicle-icon"><CarIcon /></div>
                    <div>
                      <div className="db-vehicle-name">{v.make} {v.model}</div>
                      <div className="db-vehicle-meta">{v.year}</div>
                    </div>
                  </div>
                  <div className="db-vehicle-specs">
                    <span>{v.batteryCapacityKwh} kWh battery</span>
                    {v.maxChargingPowerKw && <span>{v.maxChargingPowerKw} kW max</span>}
                  </div>
                  <div className="db-tags">
                    {v.connectorTypes.map((c) => (
                      <span className="db-tag" key={c}>{CONNECTOR_LABELS[c] || c}</span>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          )}
        </section>
      </main>
    </div>
  );
}

function Stat({ label, value, hint }) {
  return (
    <div className="db-card">
      <div className="db-stat-label">{label}</div>
      <div className="db-stat-value">{value}</div>
      <div className="db-stat-hint">{hint}</div>
    </div>
  );
}

const icon = (path) => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    {path}
  </svg>
);
const BoltIcon = () => icon(<path d="M13 2 4 14h7l-1 8 9-12h-7l1-8z" />);
const PlusIcon = () => icon(<path d="M12 5v14M5 12h14" />);
const GridIcon = () => icon(<path d="M3 3h7v7H3zM14 3h7v7h-7zM14 14h7v7h-7zM3 14h7v7H3z" />);
const LogoutIcon = () => icon(<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9" />);
const CarIcon = () => icon(<path d="M5 17h14M3 13l2-6h14l2 6v4H3zM7 17v2M17 17v2" />);
