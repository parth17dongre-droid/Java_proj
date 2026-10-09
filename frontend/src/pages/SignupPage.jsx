import { useState, useEffect, useRef } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { signup, refreshAccessToken } from '../services/authApi.js';
import {
  validatePassword,
  validateUsername,
  validateEmail,
  validateConfirmPassword,
} from '../utils/validation.js';
import './AuthPages.css';

const initialForm = {
  name: '',
  email: '',
  username: '',
  password: '',
  confirmPassword: '',
};

export default function SignupPage() {
  const navigate = useNavigate();
  const [form, setForm] = useState(initialForm);
  const [errors, setErrors] = useState({});
  const [serverError, setServerError] = useState('');
  const [loading, setLoading] = useState(false);
  const [checking, setChecking] = useState(true);
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
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

  const validate = () => {
    const newErrors = {};
    if (!form.name.trim()) newErrors.name = 'Name is required.';
    const usernameError = validateUsername(form.username);
    if (usernameError) newErrors.username = usernameError;
    const emailError = validateEmail(form.email);
    if (emailError) newErrors.email = emailError;
    const passwordError = validatePassword(form.password);
    if (passwordError) newErrors.password = passwordError;
    const confirmError = validateConfirmPassword(form.password, form.confirmPassword);
    if (confirmError) newErrors.confirmPassword = confirmError;
    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
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
    setServerError('');
    if (!validate()) return;
    setLoading(true);
    try {
      await signup(form);
      spawnBolts();
      setTimeout(() => navigate('/dashboard'), 650);
    } catch (err) {
      setServerError(err.message);
    } finally {
      setLoading(false);
    }
  };

  if (checking) return <div className="auth-checking">Loading...</div>;

  return (
    <>
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
            <p className="auth-eyebrow">GET STARTED</p>
            <h1 className="auth-title">Create your<br />account</h1>
            <p className="auth-subtitle">
              Join thousands of EV drivers managing their charging smarter.
            </p>

            <form onSubmit={handleSubmit} className="auth-form" noValidate>
              <div className="field-group">
                <label className="field-label">FULL NAME</label>
                <input
                  className={`field-input ${errors.name ? 'input-error' : ''}`}
                  name="name"
                  placeholder="Alex Johnson"
                  value={form.name}
                  onChange={handleChange}
                />
                {errors.name && <span className="field-error">{errors.name}</span>}
              </div>

              <div className="field-group">
                <label className="field-label">USERNAME</label>
                <input
                  className={`field-input ${errors.username ? 'input-error' : ''}`}
                  name="username"
                  placeholder="alex_j"
                  value={form.username}
                  onChange={handleChange}
                />
                {errors.username && <span className="field-error">{errors.username}</span>}
              </div>

              <div className="field-group">
                <label className="field-label">EMAIL ADDRESS</label>
                <input
                  className={`field-input ${errors.email ? 'input-error' : ''}`}
                  type="email"
                  name="email"
                  placeholder="you@example.com"
                  value={form.email}
                  onChange={handleChange}
                />
                {errors.email && <span className="field-error">{errors.email}</span>}
              </div>

              <div className="field-row">
                <div className="field-group">
                  <label className="field-label">PASSWORD</label>
                  <div className="field-input-wrap">
                    <input
                      className={`field-input ${errors.password ? 'input-error' : ''}`}
                      type={showPassword ? 'text' : 'password'}
                      name="password"
                      placeholder="••••••••"
                      value={form.password}
                      onChange={handleChange}
                    />
                    <button
                      type="button"
                      className="toggle-pw"
                      onClick={() => setShowPassword(!showPassword)}
                    >
                      {showPassword ? '🙈' : '👁'}
                    </button>
                  </div>
                  {errors.password && <span className="field-error">{errors.password}</span>}
                </div>

                <div className="field-group">
                  <label className="field-label">CONFIRM PASSWORD</label>
                  <div className="field-input-wrap">
                    <input
                      className={`field-input ${errors.confirmPassword ? 'input-error' : ''}`}
                      type={showConfirm ? 'text' : 'password'}
                      name="confirmPassword"
                      placeholder="••••••••"
                      value={form.confirmPassword}
                      onChange={handleChange}
                    />
                    <button
                      type="button"
                      className="toggle-pw"
                      onClick={() => setShowConfirm(!showConfirm)}
                    >
                      {showConfirm ? '🙈' : '👁'}
                    </button>
                  </div>
                  {errors.confirmPassword && <span className="field-error">{errors.confirmPassword}</span>}
                </div>
              </div>

              {serverError && <p className="field-error">{serverError}</p>}

              <button
                ref={btnRef}
                type="submit"
                className="auth-btn"
                disabled={loading}
              >
                {loading ? 'Creating account...' : 'Create Account →'}
              </button>
            </form>

            <div className="auth-divider"><span>OR</span></div>

            <p className="auth-switch">
              Already have an account? <Link to="/login" className="auth-link">Sign in</Link>
            </p>
          </div>
        </div>
      </div>
    </>
  );
}