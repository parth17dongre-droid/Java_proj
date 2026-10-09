import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { refreshAccessToken } from '../services/authApi.js';
import './AuthPages.css';

export default function IndexPage() {
  const navigate = useNavigate();
  const [checking, setChecking] = useState(true);

  useEffect(() => {
    refreshAccessToken()
      .then(() => navigate('/dashboard', { replace: true }))
      .catch(() => setChecking(false));
  }, []);

  if (checking) return <div className="auth-checking">Loading...</div>;

  return (
    <div className="auth-layout">
      {/* Left panel — same as login/signup */}
      <div className="auth-left">
        <div className="auth-logo">
          <div className="auth-logo-icon">⚡</div>
          <div>
            <div className="auth-logo-name">EV.Bolts</div>
            <div className="auth-logo-sub">EV CHARGING</div>
          </div>
        </div>

        <div className="auth-left-art">
          <div className="charger-card">
            <div className="charger-screen">
              <div className="charger-bar" />
              <div className="charger-bar short" />
              <div className="charger-pct">87%</div>
            </div>
            <div className="charger-plug">⚡</div>
            <div className="charger-cable" />
            <div className="charger-base" />
            <div className="charger-dots">
              <span /><span /><span />
            </div>
          </div>
        </div>

        <div className="auth-left-headline">
          Charge smarter,<br />
          <span className="accent">drive further.</span>
        </div>

        <div className="auth-stats">
          <div className="stat-pill">Fast Charging</div>
          <div className="stat-pill accent-text">99.2%</div>
          <div className="stat-pill accent-text">240kW</div>
        </div>
      </div>

      {/* Right panel */}
      <div className="auth-right">
        <div className="auth-form-wrap">
          <p className="auth-eyebrow">EV CHARGING PLATFORM</p>
          <h1 className="auth-title">The smarter way<br />to charge.</h1>
          <p className="auth-subtitle">
            Manage sessions, track usage, and find stations near you — all in one place.
          </p>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', marginTop: '32px' }}>
            <Link to="/login" className="auth-btn" style={{ textDecoration: 'none', textAlign: 'center' }}>
              Sign In →
            </Link>
            <Link
              to="/signup"
              style={{
                display: 'block',
                textAlign: 'center',
                padding: '15px',
                borderRadius: '12px',
                border: '1.5px solid #243040',
                color: '#ffffff',
                textDecoration: 'none',
                fontSize: '15px',
                fontWeight: '600',
                transition: 'border-color 0.2s',
              }}
              onMouseEnter={e => e.currentTarget.style.borderColor = '#00e676'}
              onMouseLeave={e => e.currentTarget.style.borderColor = '#243040'}
            >
              Create Account
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}