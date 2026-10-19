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