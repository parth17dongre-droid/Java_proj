import { apiJson } from './apiClient.js';

const post = (url, body, navigate) =>
  apiJson(url, { method: 'POST', ...(body ? { body: JSON.stringify(body) } : {}) }, navigate);

export const evApi = {
  getNearbyStations: (lat, lng, navigate) =>
    apiJson(`/api/ev-stations/nearby?lat=${lat}&lng=${lng}`, {}, navigate),

  // slot hold (5 minutes)
  lockSlot: (slotId, navigate) => post(`/api/ev-stations/slots/${slotId}/lock`, null, navigate),
  releaseLock: (slotId, navigate) => post(`/api/ev-stations/slots/${slotId}/release`, null, navigate),

  // confirm the hold and pay the advance from the wallet, in one step
  book: (slotId, durationMinutes, navigate) => post('/api/bookings', { slotId, durationMinutes }, navigate),
  myBookings: (navigate) => apiJson('/api/bookings/me', {}, navigate),
  cancelBooking: (id, navigate) => post(`/api/bookings/${id}/cancel`, null, navigate),
  startBooking: (id, navigate) => post(`/api/bookings/${id}/start`, null, navigate),

  // admin
  liveBookings: (navigate) => apiJson('/api/admin/bookings', {}, navigate),
  settle: (billId, energyKwh, navigate) => post(`/api/bills/${billId}/settle`, { energyKwh }, navigate),

  setMaintenance: (slotId, enabled, navigate) => post(`/api/admin/slots/${slotId}/maintenance`, { enabled }, navigate),
  revenue: (period, navigate) => apiJson(`/api/admin/revenue?period=${period}`, {}, navigate),

  getWalletBalance: (navigate) => apiJson('/api/wallet', {}, navigate),
};
