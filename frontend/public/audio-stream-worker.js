self.addEventListener('install', (event) => {
  event.waitUntil(self.skipWaiting());
});

self.addEventListener('activate', (event) => {
  event.waitUntil(self.clients.claim());
});

self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url);
  if (url.origin !== self.location.origin || url.pathname !== '/api/music/stream' || event.request.method !== 'GET') return;

  event.respondWith((async () => {
    const client = await self.clients.get(event.clientId);
    if (!client) return new Response('Geen actieve speler.', { status: 503 });

    const token = await new Promise((resolve) => {
      const channel = new MessageChannel();
      const timeout = setTimeout(() => {
        channel.port1.close();
        resolve(null);
      }, 10000);
      channel.port1.onmessage = (message) => {
        clearTimeout(timeout);
        channel.port1.close();
        resolve(message.data?.token ?? null);
      };
      client.postMessage({ type: 'audio-access-token' }, [channel.port2]);
    });
    if (!token) return new Response('Geen toegang tot de stream.', { status: 401 });

    const headers = new Headers(event.request.headers);
    headers.set('Authorization', `Bearer ${token}`);
    return fetch(new Request(event.request, { headers }));
  })());
});
