import { useCallback, useEffect, useState } from 'react';
import './WalletPage.css';

const API = import.meta.env.VITE_API_URL ?? 'http://localhost:8080';
// Change this if your LoginPage stores the JWT under a different key.
const getToken = () => localStorage.getItem('token');

async function api(path, options = {}) {
  const res = await fetch(`${API}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${getToken()}`,
      ...options.headers,
    },
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.message || 'Something went wrong');
  return data;
}

const rupees = (n) => `₹${Number(n).toFixed(2)}`;
const isCredit = (type) => type === 'TOPUP' || type === 'REFUND';
const when = (iso) => new Date(iso).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' });
const QUICK_AMOUNTS = [100, 500, 1000, 2000];

export default function WalletPage() {
  const [balance, setBalance] = useState(null);
  const [txns, setTxns] = useState([]);
  const [pending, setPending] = useState([]);
  const [amount, setAmount] = useState('500');
  const [method, setMethod] = useState('CARD');
  const [card, setCard] = useState({ cardNumber: '', expiry: '', cvv: '' });
  const [upiId, setUpiId] = useState('');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState(null); // { type: 'ok' | 'error', text }

  const load = useCallback(async () => {
    try {
      const [w, t, p] = await Promise.all([
        api('/api/wallet'),
        api('/api/wallet/transactions?limit=20'),
        api('/api/wallet/pending-bills'),
      ]);
      setBalance(w.balance);
      setTxns(t);
      setPending(p);
    } catch (e) {
      setMessage({ type: 'error', text: e.message });
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  async function handleTopUp(e) {
    e.preventDefault();
    setBusy(true);
    setMessage(null);
    try {
      const body = { amount: Number(amount), method, ...(method === 'CARD' ? card : { upiId }) };
      const res = await api('/api/wallet/topup', { method: 'POST', body: JSON.stringify(body) });
      setCard({ cardNumber: '', expiry: '', cvv: '' }); // never keep card details around
      setMessage({ type: 'ok', text: `Added ${rupees(amount)} (ref ${res.gatewayTxnId})` });
      await load();
    } catch (err) {
      setMessage({ type: 'error', text: err.message });
    } finally {
      setBusy(false);
    }
  }

  async function handlePay(billId) {
    setBusy(true);
    setMessage(null);
    try {
      const res = await api(`/api/bills/${billId}/pay`, { method: 'POST' });
      setMessage({ type: 'ok', text: `Paid. Charger unlocks at your slot time. Balance ${rupees(res.newBalance)}` });
      await load();
    } catch (err) {
      setMessage({ type: 'error', text: err.message });
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="wallet-page">
      <h1>My Wallet</h1>

      {message && <p className={`wallet-msg ${message.type}`}>{message.text}</p>}

      <section className="wallet-card balance-card">
        <span>Available balance</span>
        <strong>{balance === null ? '…' : rupees(balance)}</strong>
      </section>

      {pending.length > 0 && (
        <section className="wallet-card">
          <h2>Pay advance to unlock your charger</h2>
          <ul className="pending-list">
            {pending.map((b) => (
              <li key={b.billId}>
                <div>
                  <strong>{b.chargerCode}</strong> ({b.chargerType}) · {when(b.startTime)}
                  <br />
                  <small>Advance {rupees(b.advanceAmount)}; unused amount is refunded after charging</small>
                </div>
                <button disabled={busy} onClick={() => handlePay(b.billId)}>
                  Pay {rupees(b.advanceAmount)}
                </button>
              </li>
            ))}
          </ul>
        </section>
      )}

      <section className="wallet-card">
        <h2>Add money</h2>
        <form onSubmit={handleTopUp}>
          <div className="chips">
            {QUICK_AMOUNTS.map((a) => (
              <button type="button" key={a} className={Number(amount) === a ? 'chip active' : 'chip'}
                      onClick={() => setAmount(String(a))}>
                ₹{a}
              </button>
            ))}
          </div>
          <label>
            Amount (₹10 to ₹50,000)
            <input type="number" min="10" max="50000" step="0.01" value={amount}
                   onChange={(e) => setAmount(e.target.value)} required />
          </label>

          <div className="tabs">
            <button type="button" className={method === 'CARD' ? 'tab active' : 'tab'} onClick={() => setMethod('CARD')}>Card</button>
            <button type="button" className={method === 'UPI' ? 'tab active' : 'tab'} onClick={() => setMethod('UPI')}>UPI</button>
          </div>

          {method === 'CARD' ? (
            <>
              <label>
                Card number
                <input inputMode="numeric" autoComplete="off" placeholder="4111 1111 1111 1111"
                       value={card.cardNumber} onChange={(e) => setCard({ ...card, cardNumber: e.target.value })} required />
              </label>
              <div className="row">
                <label>
                  Expiry (MM/YY)
                  <input placeholder="12/30" value={card.expiry}
                         onChange={(e) => setCard({ ...card, expiry: e.target.value })} required />
                </label>
                <label>
                  CVV
                  <input type="password" inputMode="numeric" maxLength={4} autoComplete="off" value={card.cvv}
                         onChange={(e) => setCard({ ...card, cvv: e.target.value })} required />
                </label>
              </div>
            </>
          ) : (
            <label>
              UPI ID
              <input placeholder="name@bank" value={upiId} onChange={(e) => setUpiId(e.target.value)} required />
            </label>
          )}

          <p className="hint">Demo gateway: use test cards only, no real payment is made.</p>
          <button type="submit" disabled={busy}>{busy ? 'Processing…' : `Add ${rupees(amount || 0)}`}</button>
        </form>
      </section>

      <section className="wallet-card">
        <h2>Recent activity</h2>
        {txns.length === 0 ? (
          <p>No transactions yet.</p>
        ) : (
          <table>
            <thead>
              <tr><th>When</th><th>Type</th><th>Amount</th><th>Balance</th></tr>
            </thead>
            <tbody>
              {txns.map((t) => (
                <tr key={t.txnId}>
                  <td>{when(t.createdAt)}</td>
                  <td>{t.type.replace('_', ' ')}</td>
                  <td className={isCredit(t.type) ? 'credit' : 'debit'}>
                    {isCredit(t.type) ? '+' : '−'}{rupees(t.amount)}
                  </td>
                  <td>{rupees(t.balanceAfter)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </main>
  );
}
