self.addEventListener('install', (event) => {
  event.waitUntil(self.skipWaiting());
});

self.addEventListener('activate', (event) => {
  // Vervang bestaande tokenworkers zonder nog streamverzoeken te onderscheppen.
  event.waitUntil((async () => {
    await self.clients.claim();
    await self.registration.unregister();
  })());
});
