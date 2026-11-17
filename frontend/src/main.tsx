import { createRoot } from 'react-dom/client';
import AuthGate from './AuthGate';
import { retireAudioStreamWorker } from './audioStream';
import './style.css';

retireAudioStreamWorker().catch((error: unknown) => {
  console.error('Oude audio-serviceworker bijwerken mislukt:', error);
});

createRoot(document.getElementById('root')!).render(<AuthGate />);
