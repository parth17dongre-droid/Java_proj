import { apiFetch } from './apiClient.js';

export const evApi = {
  getNearbyStations: async (lat, lng, navigate) => {
    const res = await apiFetch(`/api/ev-stations/nearby?lat=${lat}&lng=${lng}`, {}, navigate);
    if (!res.ok) throw new Error('Failed to load charging stations.');
    return await res.json();
  }
};