import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
import { describe, expect, it, vi } from 'vitest';

describe('audio stream serviceworker', () => {
  it('replaces the old worker and unregisters without intercepting requests', async () => {
    type LifecycleEvent = { waitUntil: (promise: Promise<unknown>) => void };
    const handlers: Record<string, (event: LifecycleEvent) => void> = {};
    const worker = {
      skipWaiting: vi.fn().mockResolvedValue(undefined),
      clients: { claim: vi.fn().mockResolvedValue(undefined) },
      registration: { unregister: vi.fn().mockResolvedValue(true) },
      addEventListener: (name: string, handler: (event: LifecycleEvent) => void) => { handlers[name] = handler; },
    };
    runInNewContext(readFileSync('public/audio-stream-worker.js', 'utf8'), { self: worker });
    const waitUntil = vi.fn();
    handlers.install({ waitUntil });
    await waitUntil.mock.calls[0][0];
    expect(worker.skipWaiting).toHaveBeenCalledOnce();

    waitUntil.mockClear();
    handlers.activate({ waitUntil });
    await waitUntil.mock.calls[0][0];
    expect(worker.clients.claim).toHaveBeenCalledOnce();
    expect(worker.registration.unregister).toHaveBeenCalledOnce();
    expect(handlers.fetch).toBeUndefined();
  });
});
