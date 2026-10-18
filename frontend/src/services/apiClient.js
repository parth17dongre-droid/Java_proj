import { refreshAccessToken } from './authApi.js';

export async function apiFetch(url, options = {}, navigate) {
  // Cookie is sent automatically via credentials: include
  let response = await fetch(url, {
    ...options,
    credentials: 'include',
    headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
  });

  if (response.status === 401) {
    // Access token expired — ask backend to refresh using DB refresh token
    try {
      await refreshAccessToken();
      // Backend set a new access_token cookie, retry the original request
      response = await fetch(url, {
        ...options,
        credentials: 'include',
        headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
      });
    } catch {
      // Refresh token is expired or session was logged out
      navigate('/');
      return null;
    }
  }

  return response;
}

// apiFetch + JSON parsing; throws Error(message) using the backend's "error"/"message" field.
export async function apiJson(url, options = {}, navigate) {
  const res = await apiFetch(url, options, navigate);
  if (!res) throw new Error('Your session has expired. Please log in again.');
  const data = await res.json().catch(() => null);
  if (!res.ok) throw new Error(data?.error || data?.message || `Request failed (${res.status})`);
  return data;
}
