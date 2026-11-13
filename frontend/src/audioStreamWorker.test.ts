import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
import { describe, expect, it, vi } from 'vitest';

function createWorker(client?: { postMessage: (message: unknown, ports: MessagePort[]) => void }) {
  type FetchEvent = { clientId?: string; request: Request; respondWith: (response: Promise<Response>) => void };
  const handlers: Record<string, (event: FetchEvent) => void> = {};
  const fetchMock = vi.fn().mockResolvedValue(new Response('audio', { status: 206 }));
  class Channel {
    port1 = { onmessage: (_event: { data: unknown }) => {}, close: () => {} };
    port2 = { postMessage: (data: unknown) => this.port1.onmessage({ data }) };
  }
  runInNewContext(readFileSync('public/audio-stream-worker.js', 'utf8'), {
    self: {
      location: { origin: 'https://example.test' },
      clients: { get: vi.fn().mockResolvedValue(client) },
      addEventListener: (name: string, handler: (event: FetchEvent) => void) => { handlers[name] = handler; },
    },
    URL, Headers, Request, Response, MessageChannel: Channel, setTimeout, clearTimeout, fetch: fetchMock,
  });
  return { handlers, fetchMock };
}

describe('audio stream serviceworker', () => {
  it('forwards the range and renewed token only for the stream endpoint', async () => {
    const client = {
      postMessage: vi.fn((_message: unknown, ports: MessagePort[]) => ports[0].postMessage({ token: 'access-token' })),
    };
    const { handlers, fetchMock } = createWorker(client);
    const respondWith = vi.fn();
    handlers.fetch({
      clientId: 'player',
      request: new Request('https://example.test/api/music/stream?path=track.mp3', {
        headers: { Range: 'bytes=100-' }, mode: 'cors',
      }),
      respondWith,
    });
    const response = await respondWith.mock.calls[0][0];
    expect(response.status).toBe(206);
    const request = fetchMock.mock.calls[0][0] as Request;
    expect(request.headers.get('Range')).toBe('bytes=100-');
    expect(request.headers.get('Authorization')).toBe('Bearer access-token');

    respondWith.mockClear();
    handlers.fetch({ request: new Request('https://example.test/api/music'), respondWith });
    expect(respondWith).not.toHaveBeenCalled();
  });

  it('does not request a stream without an active client or token', async () => {
    for (const client of [undefined, {
      postMessage: (_message: unknown, ports: MessagePort[]) => ports[0].postMessage({ token: null }),
    }]) {
      const { handlers, fetchMock } = createWorker(client);
      const respondWith = vi.fn();
      handlers.fetch({
        clientId: 'player',
        request: new Request('https://example.test/api/music/stream?path=track.mp3'),
        respondWith,
      });
      expect((await respondWith.mock.calls[0][0]).status).toBe(client ? 401 : 503);
      expect(fetchMock).not.toHaveBeenCalled();
    }
  });
});
