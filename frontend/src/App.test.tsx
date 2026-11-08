import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import App from './App';
import { getAccessToken, logout } from './auth';

vi.mock('./auth', () => ({
  getAccessToken: vi.fn().mockResolvedValue('access-token'),
  getUsername: () => 'donna-user',
  logout: vi.fn().mockResolvedValue(undefined),
}));

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  vi.mocked(getAccessToken).mockResolvedValue('access-token');
});

function clickCreateFile() {
  render(<App />);
  const button = screen.getByRole<HTMLButtonElement>('button', { name: 'Bestand aanmaken' });
  fireEvent.click(button);
  return button;
}

function clickGetMusic() {
  render(<App />);
  const button = screen.getByRole<HTMLButtonElement>('button', { name: 'Muziek ophalen' });
  fireEvent.click(button);
  return button;
}

describe('Raspberry Pi bediening', () => {
  it('shows the action and the signed-in user', () => {
    render(<App />);
    expect(screen.getByRole('heading').textContent).toBe('Raspberry Pi bediening');
    expect(screen.getByText('Ingelogd als donna-user.')).toBeDefined();
    expect(screen.getByRole('status').textContent).toBe('');
  });

  it('posts the fixed action and shows a successful result', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ output: '', exitCode: 0 })),
    );
    vi.stubGlobal('fetch', fetchMock);
    const timeout = vi.spyOn(AbortSignal, 'timeout');
    const button = clickCreateFile();

    await waitFor(() => {
      expect(screen.getByRole('status').textContent).toBe(
        'Commando uitgevoerd.\nExitcode: 0\nOutput:\n(geen output)',
      );
    });
    expect(button.disabled).toBe(false);
    expect(fetchMock).toHaveBeenCalledExactlyOnceWith('/api/commands/touch', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: 'Bearer access-token' },
      body: '{}',
      signal: expect.any(AbortSignal),
    });
    expect(timeout).toHaveBeenCalledWith(10_000);
  });

  it('fetches the music endpoint and lets the user browse the library', async () => {
    const music = {
      artists: [{
        name: 'Artiest A',
        albums: [{
          name: 'Album A',
          path: 'Artiest A/Album A',
          tags: { GENRE: 'Jazz' },
          tracks: [{
            title: 'Nummer A',
            path: 'Artiest A/Album A/nummer.mp3',
            status: 'OK',
            missingTags: [],
            tags: { YEAR: '2024' },
          }],
        }],
      }],
    };
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify(music), { headers: { 'Content-Type': 'application/json' } }),
    );
    vi.stubGlobal('fetch', fetchMock);
    const timeout = vi.spyOn(AbortSignal, 'timeout');
    const button = clickGetMusic();

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'Artiesten' })).toBeDefined();
      expect(screen.getByRole('button', { name: /Artiest A/ })).toBeDefined();
    });
    expect(button.disabled).toBe(false);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [endpoint, request] = fetchMock.mock.calls[0];
    expect(endpoint).toBe('/api/music');
    expect(request).toMatchObject({
      headers: { Authorization: 'Bearer access-token' },
      signal: expect.any(AbortSignal),
    });
    expect(request).not.toHaveProperty('method');
    expect(request).not.toHaveProperty('body');
    expect(timeout).toHaveBeenCalledWith(10_000);

    fireEvent.click(screen.getByRole('button', { name: /Artiest A/ }));
    expect(screen.getByRole('heading', { name: 'Artiest A' })).toBeDefined();
    expect(screen.getByRole('button', { name: /Album A/ })).toBeDefined();

    fireEvent.click(screen.getByRole('button', { name: /Album A/ }));
    expect(screen.getByRole('heading', { name: 'Album A' })).toBeDefined();
    expect(screen.getByText('Nummer A')).toBeDefined();
    expect(screen.queryByText('Artiest A/Album A/nummer.mp3')).toBeNull();
    expect(screen.getByText('Jazz')).toBeDefined();
    expect(screen.getByText('2024')).toBeDefined();
    expect(fetchMock).toHaveBeenCalledTimes(1);

    fireEvent.click(screen.getByRole('button', { name: '← Albums van Artiest A' }));
    expect(screen.getByRole('button', { name: /Album A/ })).toBeDefined();
    fireEvent.click(screen.getByRole('button', { name: '← Alle artiesten' }));
    expect(screen.getByRole('button', { name: /Artiest A/ })).toBeDefined();
  });

  it('fetches and displays the cover only when an album with COVER_ART is opened', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({
        artists: [{
          name: 'Artiest',
          albums: [{
            name: 'Album',
            path: 'Artiest/Album & meer',
            tags: { COVER_ART: 'aanwezig', GENRE: 'Jazz' },
            tracks: [],
          }],
        }],
      })))
      .mockResolvedValueOnce(new Response('image', { headers: { 'Content-Type': 'image/jpeg' } }));
    vi.stubGlobal('fetch', fetchMock);
    const createObjectURL = vi.fn().mockReturnValue('blob:album-cover');
    const revokeObjectURL = vi.fn();
    vi.stubGlobal('URL', class extends URL {
      static createObjectURL = createObjectURL;
      static revokeObjectURL = revokeObjectURL;
    });

    clickGetMusic();
    fireEvent.click(await screen.findByRole('button', { name: /Artiest/ }));
    expect(fetchMock).toHaveBeenCalledTimes(1);
    fireEvent.click(screen.getByRole('button', { name: /Album/ }));

    const image = await screen.findByRole<HTMLImageElement>('img', { name: 'Albumhoes van Album' });
    expect(image.getAttribute('src')).toBe('blob:album-cover');
    expect(screen.getByText('Jazz')).toBeDefined();
    expect(screen.queryByText('COVER_ART')).toBeNull();
    expect(screen.queryByText('aanwezig')).toBeNull();
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls[1][0]).toBe('/api/music/album-art?path=Artiest%2FAlbum%20%26%20meer');
    expect(fetchMock.mock.calls[1][1].headers).toEqual({ Authorization: 'Bearer access-token' });
    fireEvent.click(screen.getByRole('button', { name: '← Albums van Artiest' }));
    expect(revokeObjectURL).toHaveBeenCalledExactlyOnceWith('blob:album-cover');
  });

  it('shows an error when the album cover cannot be retrieved', async () => {
    vi.stubGlobal('fetch', vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({
        artists: [{ name: 'Artiest', albums: [{
          name: 'Album', path: 'Album', tags: { COVER_ART: 'yes' }, tracks: [],
        }] }],
      })))
      .mockResolvedValueOnce(new Response(null, { status: 404 })));

    clickGetMusic();
    fireEvent.click(await screen.findByRole('button', { name: /Artiest/ }));
    fireEvent.click(screen.getByRole('button', { name: /Album/ }));

    expect((await screen.findByRole('alert')).textContent).toBe(
      'Albumhoes ophalen mislukt: Request mislukt (HTTP 404).',
    );
    expect(screen.queryByText('COVER_ART')).toBeNull();
  });

  it('does not display or allocate a cover when its request completes after leaving the album', async () => {
    let finishRequest!: (response: Response) => void;
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({
        artists: [{ name: 'Artiest', albums: [{
          name: 'Album', path: 'Album', tags: { COVER_ART: 'yes' }, tracks: [],
        }] }],
      })))
      .mockReturnValueOnce(new Promise<Response>((resolve) => { finishRequest = resolve; }));
    vi.stubGlobal('fetch', fetchMock);
    const createObjectURL = vi.fn();
    vi.stubGlobal('URL', class extends URL {
      static createObjectURL = createObjectURL;
    });

    clickGetMusic();
    fireEvent.click(await screen.findByRole('button', { name: /Artiest/ }));
    fireEvent.click(screen.getByRole('button', { name: /Album/ }));
    await waitFor(() => expect(finishRequest).toBeDefined());
    fireEvent.click(screen.getByRole('button', { name: '← Albums van Artiest' }));
    await act(async () => { finishRequest(new Response('image')); });

    expect(createObjectURL).not.toHaveBeenCalled();
    expect(screen.queryByRole('img')).toBeNull();
  });

  it('disables the button while waiting for the backend', async () => {
    let finishRequest!: (response: Response) => void;
    vi.stubGlobal('fetch', vi.fn().mockReturnValue(new Promise<Response>((resolve) => {
      finishRequest = resolve;
    })));
    const button = clickCreateFile();

    expect(button.disabled).toBe(true);
    expect(screen.getByRole('status').textContent).toBe('Commando wordt uitgevoerd...');
    await waitFor(() => expect(finishRequest).toBeDefined());
    finishRequest(new Response(JSON.stringify({ output: '', exitCode: 0 })));
    await waitFor(() => expect(button.disabled).toBe(false));
  });

  it('shows output and a nonzero exit code as a failed command', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ output: 'Permission denied\n', exitCode: 1 })),
    ));
    clickCreateFile();

    await waitFor(() => {
      expect(screen.getByRole('status').textContent).toBe(
        'Commando mislukt.\nExitcode: 1\nOutput:\nPermission denied\n',
      );
    });
  });

  it('shows the backend problem detail and allows another request', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ detail: 'Controleer de logs op de Raspberry Pi.' }), {
        status: 500, headers: { 'Content-Type': 'application/problem+json' },
      }),
    ));
    const button = clickCreateFile();

    await waitFor(() => {
      expect(screen.getByRole('status').textContent).toBe('Mislukt: Controleer de logs op de Raspberry Pi.');
    });
    expect(button.disabled).toBe(false);
  });

  it('includes the HTTP status when the backend supplies no detail', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      new Response('{}', { status: 503 }),
    ));
    clickCreateFile();

    await waitFor(() => {
      expect(screen.getByRole('status').textContent).toBe('Mislukt: Request mislukt (HTTP 503).');
    });
  });

  it('warns that the command may have run after a timeout', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new DOMException('Timed out', 'TimeoutError')));
    const button = clickCreateFile();

    await waitFor(() => {
      expect(screen.getByRole('status').textContent).toBe(
        'Geen antwoord binnen 10 seconden. Het commando kan al zijn uitgevoerd; controleer de Pi.',
      );
    });
    expect(button.disabled).toBe(false);
  });

  it('shows network failures and enables the button again', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));
    const button = clickCreateFile();

    await waitFor(() => {
      expect(screen.getByRole('status').textContent).toBe('Mislukt: Failed to fetch');
    });
    expect(button.disabled).toBe(false);
  });

  it('does not call the backend when token refresh fails', async () => {
    vi.mocked(getAccessToken).mockRejectedValue(new Error('Je sessie is verlopen.'));
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const button = clickCreateFile();

    await waitFor(() => expect(screen.getByRole('status').textContent).toBe('Mislukt: Je sessie is verlopen.'));
    expect(fetchMock).not.toHaveBeenCalled();
    expect(button.disabled).toBe(false);
  });

  it.each([401, 403])('shows an explicit error for an empty HTTP %s response without retrying', async (status) => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status }));
    vi.stubGlobal('fetch', fetchMock);
    clickCreateFile();

    await waitFor(() => expect(screen.getByRole('status').textContent).toContain(`HTTP ${status}`));
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('logs out through Keycloak', async () => {
    render(<App />);
    fireEvent.click(screen.getByRole('button', { name: 'Uitloggen' }));
    await waitFor(() => expect(logout).toHaveBeenCalledTimes(1));
  });

  it('shows logout failures', async () => {
    vi.mocked(logout).mockRejectedValueOnce(new Error('Keycloak niet bereikbaar'));
    render(<App />);
    fireEvent.click(screen.getByRole('button', { name: 'Uitloggen' }));
    await waitFor(() => expect(screen.getByRole('status').textContent).toBe('Uitloggen mislukt: Keycloak niet bereikbaar'));
  });
});
