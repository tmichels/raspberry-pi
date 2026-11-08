import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from './App';
import { getAccessToken, logout } from './auth';
import { prepareAudioStreaming } from './audioStream';

vi.mock('./auth', () => ({
  getAccessToken: vi.fn().mockResolvedValue('access-token'),
  getUsername: () => 'donna-user',
  logout: vi.fn().mockResolvedValue(undefined),
}));

vi.mock('./audioStream', () => ({
  prepareAudioStreaming: vi.fn().mockResolvedValue(undefined),
  audioStreamUrl: (path: string) => `/api/music/stream?path=${encodeURIComponent(path)}`,
}));

beforeEach(() => {
  vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue(undefined);
  vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => {});
  vi.spyOn(HTMLMediaElement.prototype, 'load').mockImplementation(() => {});
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  vi.mocked(getAccessToken).mockResolvedValue('access-token');
});

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
    expect(screen.getByRole('button', { name: /^Album A/ })).toBeDefined();

    fireEvent.click(screen.getByRole('button', { name: /^Album A/ }));
    expect(screen.getByRole('button', { name: /^Album A/ }).getAttribute('aria-expanded')).toBe('true');
    expect(screen.getByText('Nummer A')).toBeDefined();
    expect(screen.queryByText('Artiest A/Album A/nummer.mp3')).toBeNull();
    expect(screen.getByText('Jazz')).toBeDefined();
    expect(screen.getByText('2024')).toBeDefined();
    expect(fetchMock).toHaveBeenCalledTimes(1);

    fireEvent.click(screen.getByRole('button', { name: /^Album A/ }));
    expect(screen.getByRole('button', { name: /^Album A/ })).toBeDefined();
    expect(screen.queryByText('Nummer A')).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: '← Alle artiesten' }));
    expect(screen.getByRole('button', { name: /Artiest A/ })).toBeDefined();
  });

  it.each([
    { trackTags: [{ TRACK: '2', YEAR: '2024' }, { TRACK: '12' }], numbered: true },
    { trackTags: [{ TRACK: '2' }, {}], numbered: false },
    { trackTags: [{}, {}], numbered: false },
  ])('shows track numbers before titles only when every track has a TRACK tag: $trackTags', async ({ trackTags, numbered }) => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      artists: [{ name: 'Artiest', albums: [{
        name: 'Album',
        path: 'Album',
        tags: {},
        tracks: trackTags.map((tags, index) => ({
          title: `Nummer ${index + 1}`,
          path: `Album/${index + 1}.mp3`,
          status: index === 1 ? 'MISSING_TAGS' : 'OK',
          missingTags: index === 1 ? ['ARTIST'] : [],
          tags,
        })),
      }] }],
    }))));

    clickGetMusic();
    fireEvent.click(await screen.findByRole('button', { name: /Artiest/ }));
    fireEvent.click(screen.getByRole('button', { name: /^Album/ }));

    const list = screen.getByRole('button', { name: /^Album/ }).closest('li')!.querySelector('ol')!;
    const tracks = within(list).getAllByRole('listitem');
    expect(list.classList.contains('tracks--numbered')).toBe(numbered);
    for (const [index, track] of tracks.entries()) {
      const number = track.querySelector('.track-number');
      expect(number?.textContent ?? null).toBe(numbered ? trackTags[index].TRACK : null);
      expect(within(track).getByText(`Nummer ${index + 1}`)).toBeDefined();
      if (number) {
        expect(number.nextElementSibling?.classList.contains('track-details')).toBe(true);
      }
    }
    expect(within(tracks[1]).getByText('Ontbrekende tags: ARTIST')).toBeDefined();
    if ('TRACK' in trackTags[0]) {
      expect(within(tracks[0]).queryByText('TRACK') !== null).toBe(!numbered);
    }
    if ('YEAR' in trackTags[0]) {
      expect(within(tracks[0]).getByText('2024')).toBeDefined();
      expect(tracks[1].querySelector('.metadata')).toBeNull();
    }
  });

  it('expands one album below its button and keeps the album list visible', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      artists: [{ name: 'Artiest', albums: ['Eerste', 'Tweede'].map((name) => ({
        name,
        path: name,
        tags: {},
        tracks: [{ title: `Nummer ${name}`, path: `${name}/track`, status: 'OK', missingTags: [], tags: {} }],
      })) }],
    })));
    vi.stubGlobal('fetch', fetchMock);

    clickGetMusic();
    fireEvent.click(await screen.findByRole('button', { name: /Artiest/ }));
    const first = screen.getByRole('button', { name: /^Eerste/ });
    const second = screen.getByRole('button', { name: /^Tweede/ });
    expect(first.getAttribute('aria-expanded')).toBe('false');
    const play = screen.getByRole('button', { name: 'Speel Eerste af' });
    expect(play.textContent).toBe('▶');
    expect(screen.getByRole('button', { name: 'Speel Tweede af' })).toBeDefined();
    fireEvent.click(play);
    expect(first.getAttribute('aria-expanded')).toBe('false');
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(screen.getByRole('button', { name: 'Pauzeer Eerste' })).toBeDefined();
    fireEvent.click(first);
    expect(first.getAttribute('aria-expanded')).toBe('true');
    expect(within(first.closest('li')!).getByText('Nummer Eerste')).toBeDefined();
    fireEvent.click(screen.getByRole('button', { name: 'Pauzeer Eerste' }));
    expect(first.getAttribute('aria-expanded')).toBe('true');
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(second.getAttribute('aria-expanded')).toBe('false');

    fireEvent.click(second);
    expect(first.getAttribute('aria-expanded')).toBe('false');
    expect(second.getAttribute('aria-expanded')).toBe('true');
    expect(within(first.closest('li')!).queryByText('Nummer Eerste')).toBeNull();
    expect(within(second.closest('li')!).getByText('Nummer Tweede')).toBeDefined();

    fireEvent.click(screen.getByRole('button', { name: '← Alle artiesten' }));
    fireEvent.click(screen.getByRole('button', { name: /Artiest/ }));
    expect(screen.getByRole('button', { name: /^Tweede/ }).getAttribute('aria-expanded')).toBe('false');
    expect(screen.queryByText('Nummer Tweede')).toBeNull();
  });

  it('plays tracks in order, supports playback controls and keeps playing while browsing', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      artists: [{ name: 'Artiest', albums: [{
        name: 'Album', path: 'Artiest/Album', tags: {},
        tracks: ['Eerste', 'Tweede'].map((title) => ({
          title, path: `Artiest/Album/${title}.mp3`, tags: {}, status: 'OK', missingTags: [],
        })),
      }] }],
    }))));
    clickGetMusic();
    fireEvent.click(await screen.findByRole('button', { name: /Artiest/ }));
    fireEvent.click(screen.getByRole('button', { name: 'Speel Album af' }));
    const player = screen.getByRole('complementary', { name: 'Muziekspeler' });
    const audio = player.querySelector('audio')!;
    await waitFor(() => expect(audio.getAttribute('src')).toBe('/api/music/stream?path=Artiest%2FAlbum%2FEerste.mp3'));
    expect(audio.crossOrigin).toBe('anonymous');
    expect(audio.play).toHaveBeenCalled();
    expect(within(player).getByText('Eerste')).toBeDefined();
    expect(within(player).getByRole<HTMLButtonElement>('button', { name: 'Vorig nummer' }).disabled).toBe(true);
    Object.defineProperty(audio, 'duration', { configurable: true, value: 150 });
    fireEvent.durationChange(audio);
    fireEvent.change(within(player).getByRole('slider', { name: 'Afspeelpositie' }), { target: { value: '75' } });
    expect(audio.currentTime).toBe(75);
    expect(within(player).getByText('1:15')).toBeDefined();
    expect(within(player).getByText('2:30')).toBeDefined();

    fireEvent.click(screen.getByRole('button', { name: /^Album/ }));
    expect(within(screen.getByRole('region', { name: 'Muziekbibliotheek' })).getByText('Eerste', { selector: 'strong' }).closest('li')?.getAttribute('aria-current')).toBe('true');
    fireEvent.click(within(player).getByRole('button', { name: 'Pauzeren' }));
    expect(audio.pause).toHaveBeenCalled();
    expect(screen.getByRole('button', { name: 'Speel Album af' })).toBeDefined();
    fireEvent.click(within(player).getByRole('button', { name: 'Afspelen' }));
    fireEvent.ended(audio);
    await waitFor(() => expect(audio.getAttribute('src')).toContain('Tweede.mp3'));
    expect(within(player).getByText('Tweede')).toBeDefined();
    expect(within(player).getByRole<HTMLButtonElement>('button', { name: 'Volgend nummer' }).disabled).toBe(true);

    fireEvent.click(screen.getByRole('button', { name: '← Alle artiesten' }));
    expect(within(player).getByText('Tweede')).toBeDefined();
    fireEvent.click(within(player).getByRole('button', { name: 'Vorig nummer' }));
    await waitFor(() => expect(audio.getAttribute('src')).toContain('Eerste.mp3'));
    fireEvent.ended(audio);
    await waitFor(() => expect(audio.getAttribute('src')).toContain('Tweede.mp3'));
    fireEvent.ended(audio);
    expect(within(player).getByRole('button', { name: 'Afspelen' })).toBeDefined();
  });

  it('shows stream setup failures and allows retrying', async () => {
    vi.mocked(prepareAudioStreaming).mockRejectedValueOnce(new Error('Niet beschikbaar'));
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      artists: [{ name: 'Artiest', albums: [{
        name: 'Album', path: 'Album', tags: {},
        tracks: [{ title: 'Nummer', path: 'Album/track.mp3', tags: {}, status: 'OK', missingTags: [] }],
      }] }],
    }))));
    clickGetMusic();
    fireEvent.click(await screen.findByRole('button', { name: /Artiest/ }));
    fireEvent.click(screen.getByRole('button', { name: 'Speel Album af' }));
    expect((await screen.findByRole('alert')).textContent).toBe('Afspelen mislukt: Niet beschikbaar');
    fireEvent.click(screen.getByRole('button', { name: 'Afspelen' }));
    await waitFor(() => expect(screen.getByRole('complementary', { name: 'Muziekspeler' }).querySelector('audio')?.getAttribute('src')).toContain('track.mp3'));
    expect(screen.queryByRole('alert')).toBeNull();
  });

  it('switches albums and disables playback for an album without tracks', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      artists: [{ name: 'Artiest', albums: [
        ...['Eerste', 'Tweede'].map((name) => ({
          name, path: name, tags: {},
          tracks: [{ title: `Nummer ${name}`, path: `${name}/track.mp3`, tags: {}, status: 'OK', missingTags: [] }],
        })),
        { name: 'Leeg', path: 'Leeg', tags: {}, tracks: [] },
      ] }],
    }))));
    clickGetMusic();
    fireEvent.click(await screen.findByRole('button', { name: /Artiest/ }));
    expect(screen.getByRole<HTMLButtonElement>('button', { name: 'Speel Leeg af' }).disabled).toBe(true);
    fireEvent.click(screen.getByRole('button', { name: 'Speel Eerste af' }));
    const player = screen.getByRole('complementary', { name: 'Muziekspeler' });
    await waitFor(() => expect(player.querySelector('audio')?.getAttribute('src')).toContain('Eerste%2Ftrack.mp3'));
    fireEvent.click(screen.getByRole('button', { name: 'Speel Tweede af' }));
    await waitFor(() => expect(player.querySelector('audio')?.getAttribute('src')).toContain('Tweede%2Ftrack.mp3'));
    expect(within(player).getByText('Nummer Tweede')).toBeDefined();
    expect(screen.getByRole('button', { name: 'Speel Eerste af' })).toBeDefined();
    expect(screen.getByRole('button', { name: 'Pauzeer Tweede' })).toBeDefined();
  });

  it('reports an audio loading failure and can restart the track', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      artists: [{ name: 'Artiest', albums: [{
        name: 'Album', path: 'Album', tags: {},
        tracks: [{ title: 'Nummer', path: 'Album/track.mp3', tags: {}, status: 'OK', missingTags: [] }],
      }] }],
    }))));
    clickGetMusic();
    fireEvent.click(await screen.findByRole('button', { name: /Artiest/ }));
    fireEvent.click(screen.getByRole('button', { name: 'Speel Album af' }));
    const audio = screen.getByRole('complementary', { name: 'Muziekspeler' }).querySelector('audio')!;
    await waitFor(() => expect(audio.getAttribute('src')).toContain('track.mp3'));
    fireEvent.error(audio);
    expect(screen.getByRole('alert').textContent).toContain('de audiostream kon niet worden geladen');
    expect(screen.getByRole('button', { name: 'Speel Album af' })).toBeDefined();
    fireEvent.click(screen.getByRole('button', { name: 'Speel Album af' }));
    await waitFor(() => expect(screen.queryByRole('alert')).toBeNull());
    await waitFor(() => expect(audio.getAttribute('src')).toContain('track.mp3'));
  });

  it('reports playback permission failures and permits another attempt', async () => {
    vi.mocked(HTMLMediaElement.prototype.play).mockRejectedValueOnce(new Error('Afspelen niet toegestaan'));
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      artists: [{ name: 'Artiest', albums: [{
        name: 'Album', path: 'Album', tags: {},
        tracks: [{ title: 'Nummer', path: 'Album/track.mp3', tags: {}, status: 'OK', missingTags: [] }],
      }] }],
    }))));
    clickGetMusic();
    fireEvent.click(await screen.findByRole('button', { name: /Artiest/ }));
    fireEvent.click(screen.getByRole('button', { name: 'Speel Album af' }));
    expect((await screen.findByRole('alert')).textContent).toBe('Afspelen mislukt: Afspelen niet toegestaan');
    fireEvent.click(screen.getByRole('button', { name: 'Afspelen' }));
    await waitFor(() => expect(HTMLMediaElement.prototype.play).toHaveBeenCalledTimes(2));
    expect(screen.queryByRole('alert')).toBeNull();
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
    fireEvent.click(screen.getByRole('button', { name: /^Album/ }));

    const image = await screen.findByRole<HTMLImageElement>('img', { name: 'Albumhoes van Album' });
    expect(image.getAttribute('src')).toBe('blob:album-cover');
    expect(screen.getByText('Jazz')).toBeDefined();
    expect(screen.queryByText('COVER_ART')).toBeNull();
    expect(screen.queryByText('aanwezig')).toBeNull();
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls[1][0]).toBe('/api/music/album-art?path=Artiest%2FAlbum%20%26%20meer');
    expect(fetchMock.mock.calls[1][1].headers).toEqual({ Authorization: 'Bearer access-token' });
    fireEvent.click(screen.getByRole('button', { name: /^Album/ }));
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
    fireEvent.click(screen.getByRole('button', { name: /^Album/ }));

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
    fireEvent.click(screen.getByRole('button', { name: /^Album/ }));
    await waitFor(() => expect(finishRequest).toBeDefined());
    fireEvent.click(screen.getByRole('button', { name: /^Album/ }));
    await act(async () => { finishRequest(new Response('image')); });

    expect(createObjectURL).not.toHaveBeenCalled();
    expect(screen.queryByRole('img')).toBeNull();
  });

  it('disables the button while waiting for the backend', async () => {
    let finishRequest!: (response: Response) => void;
    vi.stubGlobal('fetch', vi.fn().mockReturnValue(new Promise<Response>((resolve) => {
      finishRequest = resolve;
    })));
    const button = clickGetMusic();

    expect(button.disabled).toBe(true);
    expect(screen.getByRole('status').textContent).toBe('Muziek wordt opgehaald...');
    await waitFor(() => expect(finishRequest).toBeDefined());
    finishRequest(new Response(JSON.stringify({ artists: [] })));
    await waitFor(() => expect(button.disabled).toBe(false));
  });

  it('shows the backend problem detail and allows another request', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ detail: 'Controleer de logs op de Raspberry Pi.' }), {
        status: 500, headers: { 'Content-Type': 'application/problem+json' },
      }),
    ));
    const button = clickGetMusic();

    await waitFor(() => {
      expect(screen.getByRole('status').textContent).toBe('Mislukt: Controleer de logs op de Raspberry Pi.');
    });
    expect(button.disabled).toBe(false);
  });

  it('includes the HTTP status when the backend supplies no detail', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      new Response('{}', { status: 503 }),
    ));
    clickGetMusic();

    await waitFor(() => {
      expect(screen.getByRole('status').textContent).toBe('Mislukt: Request mislukt (HTTP 503).');
    });
  });

  it('shows a timeout when retrieving music', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new DOMException('Timed out', 'TimeoutError')));
    const button = clickGetMusic();

    await waitFor(() => {
      expect(screen.getByRole('status').textContent).toBe(
        'Geen antwoord binnen 10 seconden. Probeer het opnieuw.',
      );
    });
    expect(button.disabled).toBe(false);
  });

  it('shows network failures and enables the button again', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));
    const button = clickGetMusic();

    await waitFor(() => {
      expect(screen.getByRole('status').textContent).toBe('Mislukt: Failed to fetch');
    });
    expect(button.disabled).toBe(false);
  });

  it('does not call the backend when token refresh fails', async () => {
    vi.mocked(getAccessToken).mockRejectedValue(new Error('Je sessie is verlopen.'));
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const button = clickGetMusic();

    await waitFor(() => expect(screen.getByRole('status').textContent).toBe('Mislukt: Je sessie is verlopen.'));
    expect(fetchMock).not.toHaveBeenCalled();
    expect(button.disabled).toBe(false);
  });

  it.each([401, 403])('shows an explicit error for an empty HTTP %s response without retrying', async (status) => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status }));
    vi.stubGlobal('fetch', fetchMock);
    clickGetMusic();

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
