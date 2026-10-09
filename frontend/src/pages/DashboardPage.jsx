import { useNavigate } from 'react-router-dom';
import { apiFetch } from '../services/apiClient.js';

export default function DashboardPage() {
  const navigate = useNavigate();

  const handleLogout = async () => {
    try {
      // apiFetch will auto-refresh the access token if it's expired,
      // then send the logout request with the fresh token
      await apiFetch('/api/auth/logout', { method: 'POST' }, navigate);
    } catch {
      // if everything fails, still clear locally
    } finally {
      localStorage.removeItem('accessToken');
      localStorage.removeItem('refreshToken');
      navigate('/');
    }
  };

  return (
    <div className="dashboard">
      <header className="dashboard-header">
        <h1>Dashboard</h1>
        <button className="secondary" onClick={handleLogout}>Log out</button>
      </header>

      <div className="dashboard-grid">
        <div className="card">
          <h3>Welcome</h3>
          <p>You are logged in. This is your home page.</p>
        </div>
        <div className="card">
          <h3>Account</h3>
          <p>Profile and settings will go here.</p>
        </div>
        <div className="card">
          <h3>Activity</h3>
          <p>Recent activity will show up here.</p>
        </div>
      </div>
    </div>
  );
}