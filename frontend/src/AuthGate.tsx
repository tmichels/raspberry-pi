import { useEffect, useState } from 'react';
import App from './App';
import { initializeAuth } from './auth';

export default function AuthGate() {
  const [authenticated, setAuthenticated] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    initializeAuth().then((result) => {
      if (!active) return;
      if (result) setAuthenticated(true);
      else setError('Je bent niet ingelogd. Herlaad de pagina om opnieuw in te loggen.');
    }).catch((cause: unknown) => {
      if (active) {
        setError(`Inloggen mislukt: ${cause instanceof Error ? cause.message : String(cause)}. Herlaad de pagina om opnieuw te proberen.`);
      }
    });
    return () => { active = false; };
  }, []);

  if (authenticated) return <App />;

  return (
    <main>
      <h1>Raspberry Pi bediening</h1>
      <p role={error ? 'alert' : 'status'}>{error || 'Inloggen via Keycloak...'}</p>
    </main>
  );
}
