import { useState } from 'react';
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import AudioPlayer from './AudioPlayer';
import type { Playback } from './AudioPlayer';
import { getAlbumArt } from './api';
import { prepareAudioStreaming } from './audioStream';

vi.mock('./api', () => ({ getAlbumArt: vi.fn() }));
vi.mock('./audioStream', () => ({
  prepareAudioStreaming: vi.fn().mockResolvedValue(undefined),
  audioStreamUrl: (path: string) => `/stream?path=${encodeURIComponent(path)}`,
}));

const initialPlayback: Playback = {
  artist: 'Artiest',
  album: {
    name: 'Album',
    path: 'Album',
    tags: { COVER_ART: 'ja' },
    tracks: ['Eerste', 'Tweede'].map((title) => ({
      title, path: `${title}.mp3`, tags: {}, status: 'OK', missingTags: [],
    })),
  },
  trackIndex: 0,
  playing: true,
};

function Player({ initial = initialPlayback }: { initial?: Playback }) {
  const [playback, setPlayback] = useState(initial);
  return <AudioPlayer playback={playback} onChange={setPlayback} />;
}

function createSession(unsupported: MediaSessionAction[] = []) {
  const handlers = new Map<MediaSessionAction, MediaSessionActionHandler>();
  const session = {
    metadata: null as MediaMetadata | null,
    playbackState: 'none' as MediaSessionPlaybackState,
    setPositionState: vi.fn(),
    setActionHandler: vi.fn((action: MediaSessionAction, handler: MediaSessionActionHandler | null) => {
      if (unsupported.includes(action)) throw new TypeError('Not supported');
      if (handler) handlers.set(action, handler);
      else handlers.delete(action);
    }),
  };
  vi.stubGlobal('navigator', { mediaSession: session });
  vi.stubGlobal('MediaMetadata', class {
    constructor(init: MediaMetadataInit) {
      Object.assign(this, init);
    }
  });
  return { session, handlers };
}

beforeEach(() => {
  vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue(undefined);
  vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => {});
  vi.spyOn(HTMLMediaElement.prototype, 'load').mockImplementation(() => {});
  vi.mocked(getAlbumArt).mockResolvedValue(new Blob(['cover'], { type: 'image/jpeg' }));
  vi.mocked(prepareAudioStreaming).mockResolvedValue(undefined);
  const OriginalURL = URL;
  vi.stubGlobal('URL', class extends OriginalURL {
    static createObjectURL = vi.fn().mockReturnValue('blob:cover');
    static revokeObjectURL = vi.fn();
  });
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  vi.clearAllMocks();
});

describe('Media Session', () => {
  it('shares metadata and artwork and updates them when changing tracks', async () => {
    const { session, handlers } = createSession();
    const { unmount } = render(<Player />);
    await waitFor(() => expect(session.metadata).toMatchObject({
      title: 'Eerste',
      artist: 'Artiest',
      album: 'Album',
      artwork: [{ src: 'blob:cover' }],
    }));
    expect(getAlbumArt).toHaveBeenCalledTimes(1);
    expect(screen.getByAltText('Albumhoes van Album').getAttribute('src')).toBe('blob:cover');
    expect(handlers.has('previoustrack')).toBe(false);
    expect(handlers.has('nexttrack')).toBe(true);

    act(() => handlers.get('nexttrack')!({ action: 'nexttrack' }));
    await waitFor(() => expect(session.metadata?.title).toBe('Tweede'));
    expect(handlers.has('nexttrack')).toBe(false);
    expect(handlers.has('previoustrack')).toBe(true);
    act(() => handlers.get('previoustrack')!({ action: 'previoustrack' }));
    expect(session.metadata?.title).toBe('Eerste');
    expect(getAlbumArt).toHaveBeenCalledTimes(1);

    unmount();
    expect(handlers.size).toBe(0);
    expect(session.metadata).toBeNull();
    expect(session.playbackState).toBe('none');
    expect(session.setPositionState).toHaveBeenLastCalledWith();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:cover');
  });

  it('controls playback and reports actual playback state and seek position', async () => {
    const { session, handlers } = createSession();
    render(<Player />);
    const player = screen.getByRole('complementary', { name: 'Muziekspeler' });
    const audio = player.querySelector('audio')!;
    await waitFor(() => expect(audio.getAttribute('src')).toContain('Eerste.mp3'));
    expect(session.playbackState).toBe('paused');
    Object.defineProperty(audio, 'paused', { configurable: true, value: false });
    fireEvent.play(audio);
    expect(session.playbackState).toBe('playing');
    Object.defineProperty(audio, 'paused', { configurable: true, value: true });
    fireEvent.pause(audio);
    expect(session.playbackState).toBe('paused');

    act(() => handlers.get('pause')!({ action: 'pause' }));
    expect(within(player).getByRole('button', { name: 'Afspelen' })).toBeDefined();
    act(() => handlers.get('play')!({ action: 'play' }));
    expect(within(player).getByRole('button', { name: 'Pauzeren' })).toBeDefined();

    Object.defineProperty(audio, 'duration', { configurable: true, value: 150 });
    fireEvent.durationChange(audio);
    expect(session.setPositionState).toHaveBeenLastCalledWith({ duration: 150, position: 0, playbackRate: 1 });
    act(() => handlers.get('seekto')!({ action: 'seekto', seekTime: 75 }));
    expect(audio.currentTime).toBe(75);
    expect(within(player).getByText('1:15')).toBeDefined();
    fireEvent.timeUpdate(audio);
    expect(session.setPositionState).toHaveBeenLastCalledWith({ duration: 150, position: 75, playbackRate: 1 });
    act(() => handlers.get('seekforward')!({ action: 'seekforward', seekOffset: 100 }));
    expect(audio.currentTime).toBe(150);
    act(() => handlers.get('seekbackward')!({ action: 'seekbackward', seekOffset: 200 }));
    expect(audio.currentTime).toBe(0);
    act(() => handlers.get('seekto')!({ action: 'seekto', seekTime: NaN }));
    expect(audio.currentTime).toBe(0);
    Object.defineProperty(audio, 'duration', { configurable: true, value: Infinity });
    fireEvent.durationChange(audio);
    expect(session.setPositionState).toHaveBeenLastCalledWith();
  });

  it('continues playback when actions or the Media Session API are unsupported', async () => {
    const { handlers } = createSession(['seekto', 'seekforward']);
    const { unmount } = render(<Player />);
    await waitFor(() => expect(screen.getByAltText('Albumhoes van Album')).toBeDefined());
    expect(handlers.has('play')).toBe(true);
    expect(handlers.has('seekto')).toBe(false);
    unmount();
    expect(handlers.size).toBe(0);

    vi.stubGlobal('navigator', {});
    render(<Player />);
    await waitFor(() => expect(screen.getByRole('complementary').querySelector('audio')?.getAttribute('src')).toContain('Eerste.mp3'));
    expect(screen.getByRole('button', { name: 'Pauzeren' })).toBeDefined();
  });

  it('plays without artwork or an unsupported position API', async () => {
    const { session } = createSession();
    Object.defineProperty(session, 'setPositionState', { value: undefined });
    const playback = { ...initialPlayback, album: { ...initialPlayback.album, tags: {} } };
    const { unmount } = render(<Player initial={playback} />);
    await waitFor(() => expect(session.metadata).toMatchObject({ title: 'Eerste', artwork: [] }));
    expect(getAlbumArt).not.toHaveBeenCalled();
    fireEvent.durationChange(screen.getByRole('complementary').querySelector('audio')!);
    unmount();
    expect(session.metadata).toBeNull();
  });

  it('retries failed playback from the device controls', async () => {
    const { handlers } = createSession();
    vi.mocked(prepareAudioStreaming).mockRejectedValueOnce(new Error('Niet beschikbaar'));
    render(<Player />);
    expect((await screen.findByRole('alert')).textContent).toContain('Niet beschikbaar');
    act(() => handlers.get('play')!({ action: 'play' }));
    await waitFor(() => expect(screen.getByRole('complementary').querySelector('audio')?.getAttribute('src')).toContain('Eerste.mp3'));
    expect(screen.queryByRole('alert')).toBeNull();
  });

  it('starts a prepared paused stream directly from the device play action', async () => {
    const { handlers } = createSession();
    const onChange = vi.fn();
    render(<AudioPlayer playback={{ ...initialPlayback, playing: false }} onChange={onChange} />);
    const audio = screen.getByRole('complementary').querySelector('audio')!;
    await waitFor(() => expect(audio.getAttribute('src')).toContain('Eerste.mp3'));
    expect(audio.play).not.toHaveBeenCalled();
    act(() => handlers.get('play')!({ action: 'play' }));
    expect(audio.play).toHaveBeenCalledTimes(1);
    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ playing: true }));
  });

  it('drops stale artwork when changing albums and revokes each object URL', async () => {
    const { session } = createSession();
    let resolveSecond: (blob: Blob) => void = () => {};
    vi.mocked(getAlbumArt)
      .mockResolvedValueOnce(new Blob(['first']))
      .mockReturnValueOnce(new Promise((resolve) => { resolveSecond = resolve; }));
    vi.mocked(URL.createObjectURL).mockReturnValueOnce('blob:first').mockReturnValueOnce('blob:second');
    const second: Playback = {
      ...initialPlayback,
      album: { ...initialPlayback.album, name: 'Ander album', path: 'Ander album' },
    };
    const { rerender, unmount } = render(<AudioPlayer playback={initialPlayback} onChange={vi.fn()} />);
    await waitFor(() => expect(session.metadata?.artwork).toEqual([{ src: 'blob:first' }]));
    rerender(<AudioPlayer playback={second} onChange={vi.fn()} />);
    expect(session.metadata).toMatchObject({ album: 'Ander album', artwork: [] });
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:first');
    await act(async () => resolveSecond(new Blob(['second'])));
    expect(session.metadata?.artwork).toEqual([{ src: 'blob:second' }]);
    unmount();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:second');
  });

  it('ignores a late artwork response after changing albums', async () => {
    const { session } = createSession();
    let resolveFirst: (blob: Blob) => void = () => {};
    vi.mocked(getAlbumArt)
      .mockReturnValueOnce(new Promise((resolve) => { resolveFirst = resolve; }))
      .mockResolvedValueOnce(new Blob(['second']));
    const second: Playback = {
      ...initialPlayback,
      album: { ...initialPlayback.album, name: 'Ander album', path: 'Ander album' },
    };
    const { rerender } = render(<AudioPlayer playback={initialPlayback} onChange={vi.fn()} />);
    rerender(<AudioPlayer playback={second} onChange={vi.fn()} />);
    await waitFor(() => expect(session.metadata).toMatchObject({ album: 'Ander album', artwork: [{ src: 'blob:cover' }] }));
    await act(async () => resolveFirst(new Blob(['first'])));
    expect(URL.createObjectURL).toHaveBeenCalledTimes(1);
    expect(session.metadata).toMatchObject({ album: 'Ander album', artwork: [{ src: 'blob:cover' }] });
  });
});
