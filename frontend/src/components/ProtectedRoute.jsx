import { useEffect, useState, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { refreshAccessToken } from '../services/authApi.js';

export default function ProtectedRoute({ children }) {
  const navigate = useNavigate();
  const [status, setStatus] = useState('checking'); // 'checking' | 'allowed' | 'denied'
  const hasChecked = useRef(false);

  useEffect(() => {
    if (hasChecked.current) return;
    hasChecked.current = true;

    refreshAccessToken()
      .then(() => setStatus('allowed'))
      .catch(() => {
        setStatus('denied');
        navigate('/', { replace: true });
      });
  }, []); // empty deps — run once only

  if (status === 'checking') return <div className="auth-checking">Loading...</div>;
  if (status === 'denied') return null;
  return children;
}