export async function retireAudioStreamWorker(): Promise<void> {
  if (!('serviceWorker' in navigator)) return;
  const registration = await navigator.serviceWorker.getRegistration();
  const scriptUrl = new URL('/audio-stream-worker.js', window.location.origin).href;
  if (registration && [registration.active, registration.waiting, registration.installing]
    .some((worker) => worker?.scriptURL === scriptUrl)) {
    await registration.update();
  }
}
