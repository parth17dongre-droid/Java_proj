import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { apiFetch } from '../services/apiClient.js';
import { evApi } from '../services/evApi.js';
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

const PUNE_DEFAULT_LOCATION = { lat: 18.5204, lng: 73.8567 };

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
  const [activeTab, setActiveTab] = useState('dashboard'); // 'dashboard' or 'finder'

  // Ev Finder States
  const [stations, setStations] = useState([]);
  const [selectedStation, setSelectedStation] = useState(null);
  const [userConnector, setUserConnector] = useState('CCS2');
  const [stationsLoading, setStationsLoading] = useState(false);

  // Soft Lock Booking States
  const [activeLock, setActiveLock] = useState(null);
  const [timeLeft, setTimeLeft] = useState(0);
  const [actionError, setActionError] = useState('');
  const [actionSuccess, setActionSuccess] = useState('');

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
        const userData = await meRes.json();
        const vehData = await vehRes.json();
        setUser(userData);
        setVehicles(vehData);
        
        if (vehData.length > 0 && vehData[0].connectorTypes?.length > 0) {
          setUserConnector(vehData[0].connectorTypes[0]);
        }
      } catch (err) {
        if (!cancelled) setError(err.message);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
  }, [navigate]);

  useEffect(() => {
    if (activeTab === 'finder') {
      fetchStations(PUNE_DEFAULT_LOCATION.lat, PUNE_DEFAULT_LOCATION.lng);
    }
  }, [activeTab]);

  // Polling stations data every 5 seconds to get live status updates from other users
  useEffect(() => {
    if (activeTab !== 'finder') return;
    const interval = setInterval(() => {
      fetchStations(PUNE_DEFAULT_LOCATION.lat, PUNE_DEFAULT_LOCATION.lng, false);
    }, 5000);
    return () => clearInterval(interval);
  }, [activeTab, selectedStation?.id]);

  // Countdown timer for active soft lock
  useEffect(() => {
    if (!activeLock) return;
    const timer = setInterval(() => {
      const remaining = Math.max(0, Math.floor((new Date(activeLock.lockExpiryTime).getTime() - Date.now()) / 1000));
      setTimeLeft(remaining);

      if (remaining === 0) {
        setActionError('Your slot hold expired!');
        setActiveLock(null);
        fetchStations(PUNE_DEFAULT_LOCATION.lat, PUNE_DEFAULT_LOCATION.lng, false);
      }
    }, 1000);

    return () => clearInterval(timer);
  }, [activeLock]);

  const fetchStations = async (lat, lng, showLoading = true) => {
    if (showLoading) setStationsLoading(true);
    try {
      const data = await evApi.getNearbyStations(lat, lng, navigate);
      setStations(data);
      if (data.length > 0) {
        setSelectedStation(prev => {
          if (!prev) return data[0];
          return data.find(s => s.id === prev.id) || data[0];
        });
      }
    } catch (err) {
      setError(err.message);
    } finally {
      if (showLoading) setStationsLoading(false);
    }
  };

  const handleLockSlot = async (slotId) => {
    if (!user?.id) return;
    setActionError('');
    setActionSuccess('');
    try {
      const lockedSlot = await evApi.lockSlot(slotId, user.id, navigate);
      setActiveLock({
        slotId: lockedSlot.id,
        lockExpiryTime: lockedSlot.lockExpiryTime
      });
      setActionSuccess(`Slot ${lockedSlot.slotNumber} held successfully! Complete booking within 5 minutes.`);
      fetchStations(PUNE_DEFAULT_LOCATION.lat, PUNE_DEFAULT_LOCATION.lng, false);
    } catch (err) {
      setActionError(err.message);
    }
  };

  const handleConfirmBooking = async (slotId) => {
    if (!user?.id) return;
    setActionError('');
    setActionSuccess('');
    try {
      await evApi.confirmBooking(slotId, user.id, navigate);
      setActionSuccess('Booking confirmed successfully!');
      setActiveLock(null);
      fetchStations(PUNE_DEFAULT_LOCATION.lat, PUNE_DEFAULT_LOCATION.lng, false);
    } catch (err) {
      setActionError(err.message);
    }
  };

  const handleCancelHold = async (slotId) => {
    if (!user?.id) return;
    await evApi.releaseLock(slotId, user.id, navigate);
    setActiveLock(null);
    setActionSuccess('');
    setActionError('');
    fetchStations(PUNE_DEFAULT_LOCATION.lat, PUNE_DEFAULT_LOCATION.lng, false);
  };

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
          <button 
            className={`db-nav-item ${activeTab === 'dashboard' ? 'active-tab' : ''}`} 
            onClick={() => setActiveTab('dashboard')}
          >
            <GridIcon /> Dashboard
          </button>
          <button 
            className={`db-nav-item ${activeTab === 'finder' ? 'active-tab' : ''}`} 
            onClick={() => setActiveTab('finder')}
          >
            <MapPinIcon /> Find Stations
          </button>
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
        {activeTab === 'dashboard' ? (
          <>
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
          </>
        ) : (
          <>
            <h1 className="db-title">EV Charging Stations in Pune</h1>
            <p className="db-subtitle">Find the closest stations and available slots matching your connector type.</p>

            <div className="db-card" style={{ marginTop: '20px', marginBottom: '20px', display: 'flex', alignItems: 'center', gap: '15px' }}>
              <label style={{ fontSize: '14px', fontWeight: '600' }}>Filter Slots By Connector:</label>
              <select 
                value={userConnector} 
                onChange={(e) => setUserConnector(e.target.value)}
                style={{ background: 'var(--db-surface-2)', color: 'var(--db-text)', border: '1px solid var(--db-border)', padding: '8px 12px', borderRadius: '8px' }}
              >
                {Object.entries(CONNECTOR_LABELS).map(([key, label]) => (
                  <option key={key} value={key}>{label}</option>
                ))}
              </select>
            </div>

            {stationsLoading ? (
              <p className="db-empty">Calculating distances to stations across Pune...</p>
            ) : (
              <div className="ev-finder-grid">
                <div className="db-card" style={{ padding: '16px', height: 'fit-content' }}>
                  <h3 className="db-section-title" style={{ marginBottom: '14px' }}>Nearest Stations</h3>
                  <div style={{ display: 'grid', gap: '10px' }}>
                    {stations.map((st) => (
                      <div 
                        key={st.id}
                        onClick={() => setSelectedStation(st)}
                        style={{
                          padding: '12px', borderRadius: '10px', cursor: 'pointer',
                          background: selectedStation?.id === st.id ? 'var(--db-green-soft)' : 'var(--db-surface-2)',
                          border: `1px solid ${selectedStation?.id === st.id ? 'var(--db-green)' : 'var(--db-border)'}`
                        }}
                      >
                        <div style={{ fontWeight: '600', fontSize: '14px' }}>{st.name}</div>
                        <div style={{ fontSize: '12px', color: 'var(--db-muted)', marginTop: '2px' }}>{st.address}</div>
                        <div style={{ fontSize: '12px', fontWeight: '700', color: 'var(--db-green)', marginTop: '6px' }}>📍 {st.distanceKm} km away</div>
                      </div>
                    ))}
                  </div>
                </div>

                <div className="db-card" style={{ padding: '20px' }}>
                  {selectedStation ? (
                    <div>
                      <h3 className="db-section-title">{selectedStation.name} Slots</h3>
                      <p style={{ fontSize: '13px', color: 'var(--db-muted)', marginBottom: '16px' }}>{selectedStation.address}</p>
                      
                      {actionError && <p className="db-error" style={{ marginBottom: '12px' }}>{actionError}</p>}
                      {actionSuccess && <p style={{ color: 'var(--db-green)', fontSize: '13px', marginBottom: '12px', fontWeight: '600' }}>{actionSuccess}</p>}

                      <div className="slots-grid">
                        {selectedStation.slots.map((slot) => {
                          const matches = slot.connectorType === userConnector;
                          const isLockedByMe = activeLock?.slotId === slot.id || (slot.status === 'SOFT_LOCKED' && slot.lockedByUserId === user?.id);

                          let statusBg = 'var(--db-surface-2)';
                          let statusText = 'Incompatible Plug';
                          let statusColor = 'var(--db-muted)';

                          if (matches) {
                            if (slot.status === 'AVAILABLE') {
                              statusBg = 'rgba(0, 230, 118, 0.1)';
                              statusText = 'Available';
                              statusColor = 'var(--db-green)';
                            } else if (slot.status === 'SOFT_LOCKED') {
                              statusBg = 'rgba(255, 193, 7, 0.12)';
                              statusText = isLockedByMe ? `Held for you (${timeLeft}s)` : 'In Booking';
                              statusColor = '#ffc107';
                            } else {
                              statusBg = 'rgba(255, 107, 107, 0.1)';
                              statusText = 'Booked';
                              statusColor = 'var(--db-danger)';
                            }
                          }

                          return (
                            <div key={slot.id} style={{ background: statusBg, border: '1px solid var(--db-border)', borderRadius: '12px', padding: '14px', textAlign: 'center' }}>
                              <div style={{ fontWeight: '700', fontSize: '15px' }}>{slot.slotNumber}</div>
                              <div style={{ fontSize: '11px', color: 'var(--db-muted)', marginTop: '2px' }}>{CONNECTOR_LABELS[slot.connectorType] || slot.connectorType}</div>
                              <div style={{ fontSize: '12px', fontWeight: '600', color: statusColor, marginTop: '8px' }}>{statusText}</div>
                              
                              {matches && slot.status === 'AVAILABLE' && !activeLock && (
                                <button 
                                  className="db-primary-btn" 
                                  style={{ marginTop: '10px', padding: '6px 10px', fontSize: '12px' }}
                                  onClick={() => handleLockSlot(slot.id)}
                                >
                                  Select & Hold
                                </button>
                              )}

                              {isLockedByMe && (
                                <div style={{ display: 'flex', gap: '6px', marginTop: '10px' }}>
                                  <button 
                                    className="db-primary-btn" 
                                    style={{ flex: 1, padding: '6px', fontSize: '11px' }}
                                    onClick={() => handleConfirmBooking(slot.id)}
                                  >
                                    Confirm
                                  </button>
                                  <button 
                                    className="db-ghost-btn" 
                                    style={{ flex: 1, padding: '6px', fontSize: '11px', borderColor: 'var(--db-danger)', color: 'var(--db-danger)' }}
                                    onClick={() => handleCancelHold(slot.id)}
                                  >
                                    Cancel
                                  </button>
                                </div>
                              )}
                            </div>
                          );
                        })}
                      </div>
                    </div>
                  ) : (
                    <p className="db-empty">Select a station to view live slot availability.</p>
                  )}
                </div>
              </div>
            )}
          </>
        )}
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
const MapPinIcon = () => icon(<path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0zM12 10a3 3 0 1 0 0-6 3 3 0 0 0 0 6z" />);
const LogoutIcon = () => icon(<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9" />);
const CarIcon = () => icon(<path d="M5 17h14M3 13l2-6h14l2 6v4H3zM7 17v2M17 17v2" />);