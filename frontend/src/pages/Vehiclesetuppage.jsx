import { useState } from "react";
import "./VehicleSetupPage.css";

const CONNECTORS = [
    "Standard 3-pin (portable)",
    "Bharat AC-001",
    "Ather Grid",
    "Ola Hypercharger",
    "Other",
];

const FEATURES = [
    "Two-wheeler charging points",
    "Personalized range estimates",
    "Accurate charging duration",
];

const initialForm = {
    make: "",
    model: "",
    year: "",
    connector: "",
    battery: "",
    plate: "",
};

export default function VehicleSetupPage({ onBack, onContinue }) {
    const [form, setForm] = useState(initialForm);
    const [errors, setErrors] = useState({});

    const handleChange = (e) => {
        const { name, value } = e.target;
        setForm((prev) => ({ ...prev, [name]: value }));
        setErrors((prev) => {
            if (!prev[name]) return prev;
            const { [name]: _removed, ...rest } = prev;
            return rest;
        });
    };

    const validate = () => {
        const next = {};
        const currentYear = new Date().getFullYear();

        if (!form.make.trim()) next.make = "Enter the make";
        if (!form.model.trim()) next.model = "Enter the model";

        const year = Number(form.year);
        if (!year || year < 2010 || year > currentYear + 1) {
            next.year = "Enter a valid year";
        }

        if (!form.connector) next.connector = "Select a connector";

        const battery = Number(form.battery);
        if (!battery || battery <= 0) next.battery = "Enter battery capacity";

        setErrors(next);
        return Object.keys(next).length === 0;
    };

    const handleSubmit = (e) => {
        e.preventDefault();
        if (!validate()) return;

        onContinue?.({
            ...form,
            year: Number(form.year),
            battery: Number(form.battery),
            plate: form.plate.trim().toUpperCase(),
        });
    };

    return (
        <div className="vs-page">
            <header className="vs-topbar">
                <div className="vs-brand">
                    <div className="vs-logo">
                        <BoltIcon />
                    </div>
                    <div className="vs-brand-text">
                        <span className="vs-brand-name">EV.Bolts</span>
                        <span className="vs-brand-sub">Vehicle setup</span>
                    </div>
                </div>

                <button type="button" className="vs-ghost-btn" onClick={onBack}>
                    Back to sign in
                </button>
            </header>

            <main className="vs-main">
                <p className="vs-step">STEP 01 / 02</p>
                <h1 className="vs-title">Tell us about your electric two-wheeler</h1>
                <p className="vs-subtitle">
                    Add your scooter or bike to find nearby compatible charging points.
                </p>

                <div className="vs-card">
                    <aside className="vs-info">
                        <div className="vs-info-icon">
                            <BoltIcon />
                        </div>
                        <h2 className="vs-info-title">Power every ride</h2>
                        <p className="vs-info-text">
                            Whether you ride an Ather, Ola, TVS, or another electric
                            two-wheeler, we'll match you with the right charger.
                        </p>

                        <ul className="vs-checks">
                            {FEATURES.map((feature) => (
                                <li key={feature}>
                                    <CheckIcon />
                                    <span>{feature}</span>
                                </li>
                            ))}
                        </ul>
                    </aside>

                    <form className="vs-form" onSubmit={handleSubmit} noValidate>
                        <div className="vs-grid">
                            <Field label="Make" name="make" error={errors.make}>
                                <input
                                    id="make"
                                    name="make"
                                    className="vs-input"
                                    placeholder="e.g. Ather or Ola"
                                    value={form.make}
                                    onChange={handleChange}
                                />
                            </Field>

                            <Field label="Model" name="model" error={errors.model}>
                                <input
                                    id="model"
                                    name="model"
                                    className="vs-input"
                                    placeholder="e.g. 450X or S1 Pro"
                                    value={form.model}
                                    onChange={handleChange}
                                />
                            </Field>

                            <Field label="Model year" name="year" error={errors.year}>
                                <input
                                    id="year"
                                    name="year"
                                    type="number"
                                    className="vs-input"
                                    placeholder={String(new Date().getFullYear())}
                                    value={form.year}
                                    onChange={handleChange}
                                />
                            </Field>

                            <Field label="Connector type" name="connector" error={errors.connector}>
                                <select
                                    id="connector"
                                    name="connector"
                                    className="vs-input vs-select"
                                    value={form.connector}
                                    onChange={handleChange}
                                >
                                    <option value="">Select connector</option>
                                    {CONNECTORS.map((c) => (
                                        <option key={c} value={c}>
                                            {c}
                                        </option>
                                    ))}
                                </select>
                            </Field>

                            <Field label="Battery capacity" name="battery" error={errors.battery}>
                                <div className="vs-input-wrap">
                                    <input
                                        id="battery"
                                        name="battery"
                                        type="number"
                                        step="0.1"
                                        min="0"
                                        className="vs-input"
                                        placeholder="3.7"
                                        value={form.battery}
                                        onChange={handleChange}
                                    />
                                    <span className="vs-suffix">kWh</span>
                                </div>
                            </Field>

                            <Field label="License plate" name="plate" optional>
                                <input
                                    id="plate"
                                    name="plate"
                                    className="vs-input"
                                    placeholder="ABC 1234"
                                    value={form.plate}
                                    onChange={handleChange}
                                />
                            </Field>
                        </div>

                        <button type="submit" className="vs-primary-btn">
                            Continue to location
                        </button>
                    </form>
                </div>
            </main>
        </div>
    );
}

function Field({ label, name, error, optional, children }) {
    return (
        <div className={`vs-field${error ? " has-error" : ""}`}>
            <label htmlFor={name} className="vs-label">
                {label}
                {optional && <span className="vs-optional"> (optional)</span>}
            </label>
            {children}
            {error && <span className="vs-error">{error}</span>}
        </div>
    );
}

function BoltIcon() {
    return (
        <svg viewBox="0 0 24 24" aria-hidden="true">
            <path
                d="M13 2 3 14h9l-1 8 10-12h-9l1-8z"
                fill="currentColor"
            />
        </svg>
    );
}

function CheckIcon() {
    return (
        <svg className="vs-check-icon" viewBox="0 0 24 24" aria-hidden="true">
            <circle cx="12" cy="12" r="11" fill="currentColor" />
            <path
                d="M7.5 12.5l3 3 6-6.5"
                fill="none"
                stroke="#0a0d12"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
            />
        </svg>
    );
}
