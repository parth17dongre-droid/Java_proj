const BASE_URL = '/api/auth';

async function request(path, options = {}) {
  const response = await fetch(`${BASE_URL}${path}`, {
    credentials: 'include',
    headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
    ...options,
  });

  const text = await response.text();
  const data = text ? JSON.parse(text) : null;

  if (!response.ok) {
    const error = new Error(data?.message || `Request failed (${response.status})`);
    error.status = response.status;
    throw error;
  }
  return data;
}

export function signup({ name, username, email, password }) {
  return request('/signup', {
    method: 'POST',
    body: JSON.stringify({ name, username, email, password }),
  });
}

export function login({ username, password }) {
  return request('/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  });
}

export function logout() {
  return request('/logout', { method: 'POST' });
}

export function refreshAccessToken() {
  return request('/refresh', { method: 'POST' });
}