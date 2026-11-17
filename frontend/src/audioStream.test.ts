import { afterEach, describe, expect, it, vi } from 'vitest';
import { retireAudioStreamWorker } from './audioStream';

afterEach(() => {
  vi.unstubAllGlobals();
  vi.clearAllMocks();
});

describe('oude audio-serviceworker opruimen', () => {
  it('updates only an existing audio worker so the replacement can unregister itself', async () => {
    const update = vi.fn().mockResolvedValue(undefined);
    const getRegistration = vi.fn().mockResolvedValue({
      active: { scriptURL: new URL('/audio-stream-worker.js', window.location.origin).href },
      update,
    });
    vi.stubGlobal('navigator', { serviceWorker: { getRegistration } });

    await retireAudioStreamWorker();

    expect(getRegistration).toHaveBeenCalledOnce();
    expect(update).toHaveBeenCalledOnce();
  });

  it('does not register anything for new visitors or modify unrelated workers', async () => {
    const update = vi.fn();
    const getRegistration = vi.fn()
      .mockResolvedValueOnce(undefined)
      .mockResolvedValueOnce({ active: { scriptURL: 'https://example.test/other-worker.js' }, update });
    vi.stubGlobal('navigator', { serviceWorker: { getRegistration } });

    await retireAudioStreamWorker();
    await retireAudioStreamWorker();

    expect(update).not.toHaveBeenCalled();
  });

  it('requires no serviceworker support', async () => {
    vi.stubGlobal('navigator', {});
    await expect(retireAudioStreamWorker()).resolves.toBeUndefined();
  });

  it('reports update failures to the caller', async () => {
    vi.stubGlobal('navigator', { serviceWorker: { getRegistration: vi.fn().mockRejectedValue(new Error('Niet beschikbaar')) } });
    await expect(retireAudioStreamWorker()).rejects.toThrow('Niet beschikbaar');
  });
});
