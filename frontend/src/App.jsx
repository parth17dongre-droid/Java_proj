import { Routes, Route, useNavigate } from 'react-router-dom';
import IndexPage from './pages/IndexPage.jsx';
import LoginPage from './pages/LoginPage.jsx';
import SignupPage from './pages/SignupPage.jsx';
import DashboardPage from './pages/DashboardPage.jsx';
import VehicleSetupPage from './pages/Vehiclesetuppage.jsx';
import WalletPage from './pages/WalletPage.jsx';
import ProtectedRoute from './components/ProtectedRoute.jsx';

export default function App() {
  const navigate = useNavigate();
  return (
    <Routes>
      <Route path="/" element={<IndexPage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/signup" element={<SignupPage />} />
      <Route
        path="/dashboard"
        element={
          <ProtectedRoute>
            <DashboardPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/wallet"
        element={
          <ProtectedRoute>
            <WalletPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/vehicle-setup"
        element={
          <ProtectedRoute>
            <VehicleSetupPage
              onBack={() => navigate('/dashboard')}
              onContinue={() => navigate('/dashboard')}
            />
          </ProtectedRoute>
        }
      />
    </Routes>
  );
}