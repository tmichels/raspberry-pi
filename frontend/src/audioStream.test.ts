import { afterEach, describe, expect, it, vi } from 'vitest';
import { getAccessToken } from './auth';

vi.mock('./auth', () => ({
  getAccessToken: vi.fn().mockResolvedValue('fresh-token'),
}));

afterEach(() => {
  vi.unstubAllGlobals();
  vi.resetModules();
  vi.clearAllMocks();
});

describe('beveiligde audiostreams', () => {
  it('registers once and supplies a fresh token only to its controlling worker', async () => {
    const channel = new MessageChannel();
    const controller = channel.port1;
    const worker = Object.assign(new EventTarget(), {
      controller,
      register: vi.fn().mockResolvedValue({}),
      ready: Promise.resolve({}),
    });
    vi.stubGlobal('navigator', { serviceWorker: worker });
    const { prepareAudioStreaming, audioStreamUrl } = await import('./audioStream');

    await Promise.all([prepareAudioStreaming(), prepareAudioStreaming()]);
    expect(worker.register).toHaveBeenCalledOnce();
    expect(audioStreamUrl('Artiest/Nummer & meer.mp3')).toBe('/api/music/stream?path=Artiest%2FNummer%20%26%20meer.mp3');
    const port = channel.port2;
    const postMessage = vi.spyOn(port, 'postMessage');
    worker.dispatchEvent(new MessageEvent('message', {
      data: { type: 'audio-access-token' },
      source: controller,
      ports: [port],
    }));
    await vi.waitFor(() => expect(postMessage).toHaveBeenCalledExactlyOnceWith({ token: 'fresh-token' }));
    expect(getAccessToken).toHaveBeenCalledOnce();
    worker.dispatchEvent(new MessageEvent('message', {
      data: { type: 'audio-access-token' },
      source: channel.port2,
      ports: [port],
    }));
    expect(getAccessToken).toHaveBeenCalledOnce();
    vi.mocked(getAccessToken).mockRejectedValueOnce(new Error('Sessie verlopen'));
    worker.dispatchEvent(new MessageEvent('message', {
      data: { type: 'audio-access-token' },
      source: controller,
      ports: [port],
    }));
    await vi.waitFor(() => expect(postMessage).toHaveBeenLastCalledWith({ token: null }));
    channel.port1.close();
    channel.port2.close();
  });

  it('reports an unsupported browser', async () => {
    vi.stubGlobal('navigator', {});
    const { prepareAudioStreaming } = await import('./audioStream');
    await expect(prepareAudioStreaming()).rejects.toThrow('Deze browser ondersteunt geen beveiligde audiostreams.');
  });
});
