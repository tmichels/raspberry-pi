import { useState } from 'react';
import { createFile, getMusic } from './api';
import { getUsername, logout } from './auth';
import type { CommandResult } from './api';

export default function App() {
  const [isRunning, setIsRunning] = useState(false);
  const [status, setStatus] = useState('');

  async function handleLogout() {
    try {
      await logout();
    } catch (error) {
      setStatus(`Uitloggen mislukt: ${error instanceof Error ? error.message : String(error)}`);
    }
  }

  async function handleCommand(
    run: () => Promise<CommandResult>,
    progressMessage: string,
    successMessage: string,
    failureMessage: string,
  ) {
    setIsRunning(true);
    setStatus(progressMessage);

    try {
      const result = await run();
      const message = result.exitCode === 0 ? successMessage : failureMessage;
      setStatus(
        `${message}\nExitcode: ${result.exitCode}\nOutput:\n${result.output || '(geen output)'}`,
      );
    } catch (error) {
      if ((error instanceof Error || error instanceof DOMException) && error.name === 'TimeoutError') {
        setStatus('Geen antwoord binnen 10 seconden. Het commando kan al zijn uitgevoerd; controleer de Pi.');
      } else {
        setStatus(`Mislukt: ${error instanceof Error ? error.message : String(error)}`);
      }
    } finally {
      setIsRunning(false);
    }
  }

  function handleCreateFile() {
    return handleCommand(
      createFile,
      'Commando wordt uitgevoerd...',
      'Commando uitgevoerd.',
      'Commando mislukt.',
    );
  }

  async function handleGetMusic() {
    setIsRunning(true);
    setStatus('Muziek wordt opgehaald...');

    try {
      setStatus(await getMusic());
    } catch (error) {
      if ((error instanceof Error || error instanceof DOMException) && error.name === 'TimeoutError') {
        setStatus('Geen antwoord binnen 10 seconden. Probeer het opnieuw.');
      } else {
        setStatus(`Mislukt: ${error instanceof Error ? error.message : String(error)}`);
      }
    } finally {
      setIsRunning(false);
    }
  }

  return (
    <main>
      <h1>Raspberry Pi bediening</h1>
      <p>Ingelogd als {getUsername()}.</p>
      <button type="button" disabled={isRunning} onClick={handleLogout}>Uitloggen</button>
      <p>Maak een bestand met de huidige datum en tijd op de Raspberry Pi.</p>
      <button type="button" disabled={isRunning} onClick={handleCreateFile}>
        Bestand aanmaken
      </button>
      <p>Toon de ruwe JSON van je muziekbibliotheek.</p>
      <button type="button" disabled={isRunning} onClick={handleGetMusic}>
        Muziek ophalen
      </button>
      <pre className="status" role="status" aria-live="polite">{status}</pre>
    </main>
  );
}
