import React, { useState, useEffect } from 'react';
import { evApi } from '../services/evApi';
import './EvStationFinderPage.css';

const PUNE_DEFAULT_LOCATION = { lat: 18.5204, lng: 73.8567 }; // Central Pune

export default function EvStationFinderPage() {
  const [userLocation, setUserLocation] = useState(PUNE_DEFAULT_LOCATION);
  const [userConnector, setUserConnector] = useState('CCS2');
  const [stations, setStations] = useState([]);
  const [selectedStation, setSelectedStation] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (navigator.geolocation) {
      navigator.geolocation.getCurrentPosition(
        (pos) => {
          setUserLocation({ lat: pos.coords.latitude, lng: pos.coords.longitude });
        },
        () => fetchStations(PUNE_DEFAULT_LOCATION.lat, PUNE_DEFAULT_LOCATION.lng)
      );
    } else {
      fetchStations(PUNE_DEFAULT_LOCATION.lat, PUNE_DEFAULT_LOCATION.lng);
    }
  }, []);

  useEffect(() => {
    fetchStations(userLocation.lat, userLocation.lng);
  }, [userLocation]);

  const fetchStations = async (lat, lng) => {
    setLoading(true);
    try {
      const data = await evApi.getNearbyStations(lat, lng);
      setStations(data);
      if (data.length > 0) {
        setSelectedStation(data[0]); // Automatically select closest station
      }
    } catch (err) {
      console.error("Failed to load stations", err);
    } finally {
      setLoading(false);
    }
  };

  const handleSelectSlot = (slot) => {
    // Placeholder for Phase 2: Soft Lock API trigger
    alert(`Initiating soft lock for Slot ${slot.slotNumber} (${slot.connectorType})`);
  };

  return (
    <div className="ev-finder-container">
      <h2>Pune EV Charging Station Finder</h2>

      <div className="ev-controls">
        <label>
          <strong>Your Vehicle Connector Type: </strong>
          value={userConnector} onChange={(e) => setUserConnector(e.target.value)}
          <select value={userConnector} onChange={(e) => setUserConnector(e.target.value)} className="connector-select">
            <option value="CCS2">CCS2 (Fast DC)</option>
            <option value="TYPE2">Type 2 (AC)</option>
          </select>
        </label>
      </div>

      {loading ? (
        <p>Finding closest charging stations in Pune...</p>
      ) : (
        <div className="ev-content-grid">
          {/* Left Panel: Nearby Stations List */}
          <div className="station-list">
            <h3>Nearby Stations</h3>
            {stations.map((station) => (
              <div
                key={station.id}
                onClick={() => setSelectedStation(station)}
                className={`station-card ${selectedStation?.id === station.id ? 'active' : ''}`}
              >
                <h4>{station.name}</h4>
                <p className="station-address">{station.address}</p>
                <span className="station-distance">📍 {station.distanceKm} km away</span>
              </div>
            ))}
          </div>

          {/* Right Panel: Slot Matrix Visualizer */}
          <div className="slot-visualizer">
            {selectedStation && (
              <div>
                <h3>Slots for {selectedStation.name}</h3>
                <p className="slot-subtitle">Filtering for connector: <strong>{userConnector}</strong></p>

                <div className="slots-grid">
                  {selectedStation.slots.map((slot) => {
                    const isMatching = slot.connectorType === userConnector;
                    const isAvailable = slot.status === 'AVAILABLE' && isMatching;

                    let statusClass = 'slot-mismatched';
                    let statusLabel = `Incompatible (${slot.connectorType})`;

                    if (isMatching) {
                      if (slot.status === 'AVAILABLE') {
                        statusClass = 'slot-available';
                        statusLabel = 'Available';
                      } else if (slot.status === 'SOFT_LOCKED') {
                        statusClass = 'slot-locked';
                        statusLabel = 'In Booking (Soft Locked)';
                      } else {
                        statusClass = 'slot-booked';
                        statusLabel = 'Booked';
                      }
                    }

                    return (
                      <div key={slot.id} className={`slot-card ${statusClass}`}>
                        <h4>Slot {slot.slotNumber}</h4>
                        <p>{statusLabel}</p>
                        {isAvailable && (
                          <button 
                            className="book-btn" 
                            onClick={() => handleSelectSlot(slot)}
                          >
                            Book Slot
                          </button>
                        )}
                      </div>
                    );
                  })}
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}