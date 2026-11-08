import { getAccessToken } from './auth';

let registration: Promise<void> | undefined;

export function prepareAudioStreaming(): Promise<void> {
  if (!('serviceWorker' in navigator)) {
    return Promise.reject(new Error('Deze browser ondersteunt geen beveiligde audiostreams.'));
  }
  if (registration) return registration;

  const worker = navigator.serviceWorker;
  const onMessage = (event: MessageEvent) => {
    if (event.source !== worker.controller || event.data?.type !== 'audio-access-token' || !event.ports[0]) return;
    getAccessToken().then(
      (token) => event.ports[0].postMessage({ token }),
      () => event.ports[0].postMessage({ token: null }),
    );
  };
  worker.addEventListener('message', onMessage);
  registration = (async () => {
    await worker.register('/audio-stream-worker.js');
    await worker.ready;
    if (worker.controller) return;
    await new Promise<void>((resolve, reject) => {
      const timeout = window.setTimeout(() => {
        worker.removeEventListener('controllerchange', onChange);
        reject(new Error('De audiospeler kon niet worden gestart.'));
      }, 10000);
      const onChange = () => {
        if (!worker.controller) return;
        window.clearTimeout(timeout);
        worker.removeEventListener('controllerchange', onChange);
        resolve();
      };
      worker.addEventListener('controllerchange', onChange);
      onChange();
    });
  })().catch((error: unknown) => {
    worker.removeEventListener('message', onMessage);
    registration = undefined;
    throw error;
  });
  return registration;
}

export function audioStreamUrl(path: string): string {
  return `/api/music/stream?path=${encodeURIComponent(path)}`;
}
