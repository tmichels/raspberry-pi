import { afterEach, describe, expect, it, vi } from 'vitest';
import { getAlbumArt } from './api';
import { getAccessToken } from './auth';

vi.mock('./auth', () => ({
  getAccessToken: vi.fn().mockResolvedValue('access-token'),
}));

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  vi.mocked(getAccessToken).mockResolvedValue('access-token');
});

describe('getAlbumArt', () => {
  it('encodes the album path and sends the access token', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response('image', { headers: { 'Content-Type': 'image/jpeg' } }),
    );
    vi.stubGlobal('fetch', fetchMock);
    const timeout = vi.spyOn(AbortSignal, 'timeout');

    const result = await getAlbumArt('Artiest/Album & 100% + café?#');

    expect(await result.text()).toBe('image');
    expect(result.type).toBe('image/jpeg');
    expect(fetchMock).toHaveBeenCalledExactlyOnceWith(
      '/api/music/album-art?path=Artiest%2FAlbum%20%26%20100%25%20%2B%20caf%C3%A9%3F%23',
      {
        headers: { Authorization: 'Bearer access-token' },
        signal: expect.any(AbortSignal),
      },
    );
    expect(timeout).toHaveBeenCalledWith(10_000);
  });

  it('keeps the empty album path as an explicit query parameter', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response('image'));
    vi.stubGlobal('fetch', fetchMock);

    await getAlbumArt('');

    expect(fetchMock.mock.calls[0][0]).toBe('/api/music/album-art?path=');
  });

  it('reports an unsuccessful response', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 404 })));

    await expect(getAlbumArt('Album')).rejects.toThrow('Request mislukt (HTTP 404).');
  });

  it('does not request the image if token refresh fails', async () => {
    vi.mocked(getAccessToken).mockRejectedValueOnce(new Error('Je sessie is verlopen.'));
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    await expect(getAlbumArt('Album')).rejects.toThrow('Je sessie is verlopen.');
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
