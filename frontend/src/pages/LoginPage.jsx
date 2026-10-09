import { useState, useEffect, useRef } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { login, refreshAccessToken } from '../services/authApi.js';
import './AuthPages.css';

export default function LoginPage() {
  const navigate = useNavigate();
  const [form, setForm] = useState({ username: '', password: '' });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [checking, setChecking] = useState(true);
  const [showPassword, setShowPassword] = useState(false);
  const [bolts, setBolts] = useState([]);
  const btnRef = useRef(null);

  useEffect(() => {
    refreshAccessToken()
      .then(() => navigate('/dashboard', { replace: true }))
      .catch(() => setChecking(false));
  }, []);

  const handleChange = (e) => {
    setForm({ ...form, [e.target.name]: e.target.value });
  };

  const spawnBolts = () => {
    if (!btnRef.current) return;
    const rect = btnRef.current.getBoundingClientRect();
    const newBolts = Array.from({ length: 8 }, (_, i) => ({
      id: Date.now() + i,
      x: rect.left + rect.width / 2,
      y: rect.top + rect.height / 2,
      angle: (i / 8) * 360,
    }));
    setBolts(newBolts);
    setTimeout(() => setBolts([]), 700);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');

    if (!form.username || !form.password) {
      setError('Username and password are required.');
      return;
    }

    setLoading(true);
    try {
      await login(form);
      spawnBolts();
      setTimeout(() => navigate('/dashboard'), 650);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  if (checking) return <div className="auth-checking">Loading...</div>;

  return (
    <>
      {/* Lightning bolts portal */}
      {bolts.map((bolt) => (
        <span
          key={bolt.id}
          className="bolt-particle"
          style={{
            left: bolt.x,
            top: bolt.y,
            '--angle': `${bolt.angle}deg`,
          }}
        >⚡</span>
      ))}

      <div className="auth-layout">
        {/* Left panel */}
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
            <p className="auth-eyebrow">WELCOME BACK</p>
            <h1 className="auth-title">Sign in to your<br />account</h1>
            <p className="auth-subtitle">
              Manage sessions, track usage, and find stations near you.
            </p>

            <form onSubmit={handleSubmit} className="auth-form" noValidate>
              <div className="field-group">
                <label className="field-label">USERNAME</label>
                <input
                  className="field-input"
                  name="username"
                  placeholder="your_username"
                  value={form.username}
                  onChange={handleChange}
                  autoComplete="username"
                />
              </div>

              <div className="field-group">
                <div className="field-label-row">
                  <label className="field-label">PASSWORD</label>
                </div>
                <div className="field-input-wrap">
                  <input
                    className="field-input"
                    type={showPassword ? 'text' : 'password'}
                    name="password"
                    placeholder="••••••••"
                    value={form.password}
                    onChange={handleChange}
                    autoComplete="current-password"
                  />
                  <button
                    type="button"
                    className="toggle-pw"
                    onClick={() => setShowPassword(!showPassword)}
                    aria-label="Toggle password visibility"
                  >
                    {showPassword ? '🙈' : '👁'}
                  </button>
                </div>
              </div>

              {error && <p className="field-error">{error}</p>}

              <button
                ref={btnRef}
                type="submit"
                className="auth-btn"
                disabled={loading}
              >
                {loading ? 'Signing in...' : 'Sign In →'}
              </button>
            </form>

            <div className="auth-divider"><span>OR</span></div>

            <p className="auth-switch">
              No account? <Link to="/signup" className="auth-link">Create one</Link>
            </p>
          </div>
        </div>
      </div>
    </>
  );
}