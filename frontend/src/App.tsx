import { useState } from 'react';
import { getUsername, logout } from './auth';

export default function App() {
  const [status, setStatus] = useState('');

  async function handleLogout() {
    try {
      await logout();
    } catch (error) {
      setStatus(`Uitloggen mislukt: ${error instanceof Error ? error.message : String(error)}`);
    }
  }

  return (
    <main>
      <h1>Raspberry Pi bediening</h1>
      <p>Ingelogd als {getUsername()}.</p>
      <button type="button" onClick={handleLogout}>Uitloggen</button>
      <p className="status" role="status" aria-live="polite">{status}</p>
    </main>
  );
}
