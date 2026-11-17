import { useEffect, useRef, useState } from 'react';
import { AlbumArtImage } from './AlbumArt';
import { audioStreamUrl, prepareAudioStreaming } from './audioStream';
import useAlbumArt from './useAlbumArt';
import type { MusicAlbum } from './api';

export type Playback = {
  album: MusicAlbum;
  artist: string;
  trackIndex: number;
  playing: boolean;
};

type Props = {
  playback: Playback;
  onChange: (playback: Playback) => void;
};

function formatTime(seconds: number): string {
  if (!Number.isFinite(seconds)) return '0:00';
  return `${Math.floor(seconds / 60)}:${String(Math.floor(seconds % 60)).padStart(2, '0')}`;
}

export default function AudioPlayer({ playback, onChange }: Props) {
  const audioRef = useRef<HTMLAudioElement>(null);
  const playingRef = useRef(playback.playing);
  const playbackRef = useRef(playback);
  const onChangeRef = useRef(onChange);
  const readyRef = useRef(false);
  const loadingRef = useRef(false);
  const playRequestRef = useRef(0);
  const [attempt, setAttempt] = useState(0);
  const [error, setError] = useState('');
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const track = playback.album.tracks[playback.trackIndex];
  const hasAlbumArt = Object.hasOwn(playback.album.tags, 'COVER_ART');
  const albumArt = useAlbumArt(hasAlbumArt ? playback.album.path : undefined);
  playingRef.current = playback.playing;
  playbackRef.current = playback;
  onChangeRef.current = onChange;

  function reportFailure(message: string) {
    setError(`Afspelen mislukt: ${message}`);
    onChangeRef.current({ ...playbackRef.current, playing: false });
  }

  function startPlayback(audio: HTMLAudioElement) {
    const request = ++playRequestRef.current;
    audio.play().catch((failure: unknown) => {
      if (request === playRequestRef.current) {
        reportFailure(failure instanceof Error ? failure.message : String(failure));
      }
    });
  }

  useEffect(() => {
    const audio = audioRef.current!;
    let active = true;
    readyRef.current = false;
    loadingRef.current = true;
    setError('');
    setCurrentTime(0);
    setDuration(0);
    audio.pause();
    audio.removeAttribute('src');
    audio.load();

    prepareAudioStreaming().then(() => {
      if (!active) return;
      audio.src = audioStreamUrl(track.path);
      readyRef.current = true;
      loadingRef.current = false;
      if (playingRef.current) startPlayback(audio);
    }).catch((failure: unknown) => {
      if (active) {
        loadingRef.current = false;
        reportFailure(failure instanceof Error ? failure.message : String(failure));
      }
    });

    return () => {
      active = false;
      playRequestRef.current++;
      readyRef.current = false;
      loadingRef.current = false;
      audio.pause();
      audio.removeAttribute('src');
      audio.load();
    };
  }, [track.path, playback.album.path, attempt]);

  useEffect(() => {
    const audio = audioRef.current!;
    if (!readyRef.current) {
      if (playback.playing && !loadingRef.current) setAttempt((value) => value + 1);
      return;
    }
    if (playback.playing) {
      if (audio.ended) audio.currentTime = 0;
      if (audio.paused) startPlayback(audio);
    } else {
      playRequestRef.current++;
      audio.pause();
    }
  }, [playback.playing]);

  useEffect(() => {
    if (!('mediaSession' in navigator) || typeof MediaMetadata === 'undefined') return;
    navigator.mediaSession.metadata = new MediaMetadata({
      title: track.title,
      artist: playback.artist,
      album: playback.album.name,
      artwork: albumArt?.imageUrl ? [{ src: albumArt.imageUrl }] : [],
    });
  }, [track.title, track.path, playback.artist, playback.album.name, playback.album.path, albumArt?.imageUrl]);

  useEffect(() => {
    if (!('mediaSession' in navigator)) return;
    const session = navigator.mediaSession;
    const audio = audioRef.current!;

    function updatePlaybackState() {
      session.playbackState = audio.paused ? 'paused' : 'playing';
    }

    function updatePosition() {
      if (typeof session.setPositionState !== 'function') return;
      if (!Number.isFinite(audio.duration) || audio.duration <= 0 ||
          !Number.isFinite(audio.currentTime) || !Number.isFinite(audio.playbackRate) || audio.playbackRate === 0) {
        session.setPositionState();
        return;
      }
      session.setPositionState({
        duration: audio.duration,
        position: Math.max(0, Math.min(audio.currentTime, audio.duration)),
        playbackRate: audio.playbackRate,
      });
    }

    updatePlaybackState();
    updatePosition();
    audio.addEventListener('play', updatePlaybackState);
    audio.addEventListener('pause', updatePlaybackState);
    for (const event of ['timeupdate', 'durationchange', 'loadedmetadata', 'seeked', 'ratechange', 'emptied']) {
      audio.addEventListener(event, updatePosition);
    }
    return () => {
      audio.removeEventListener('play', updatePlaybackState);
      audio.removeEventListener('pause', updatePlaybackState);
      for (const event of ['timeupdate', 'durationchange', 'loadedmetadata', 'seeked', 'ratechange', 'emptied']) {
        audio.removeEventListener(event, updatePosition);
      }
      session.metadata = null;
      session.playbackState = 'none';
      if (typeof session.setPositionState === 'function') session.setPositionState();
    };
  }, []);

  useEffect(() => {
    if (!('mediaSession' in navigator)) return;
    const session = navigator.mediaSession;
    const registered: MediaSessionAction[] = [];

    function register(action: MediaSessionAction, handler: MediaSessionActionHandler) {
      try {
        session.setActionHandler(action, handler);
        registered.push(action);
      } catch (error) {
        if (!(error instanceof TypeError) && !(error instanceof DOMException && error.name === 'NotSupportedError')) throw error;
      }
    }

    function seek(time: number) {
      const audio = audioRef.current!;
      if (!Number.isFinite(time) || !Number.isFinite(audio.duration) || audio.duration <= 0) return;
      audio.currentTime = Math.max(0, Math.min(time, audio.duration));
      setCurrentTime(audio.currentTime);
    }

    register('play', () => {
      setError('');
      const audio = audioRef.current!;
      if (readyRef.current && audio.paused) {
        if (audio.ended) audio.currentTime = 0;
        startPlayback(audio);
      }
      onChangeRef.current({ ...playbackRef.current, playing: true });
    });
    register('pause', () => onChangeRef.current({ ...playbackRef.current, playing: false }));
    if (playback.trackIndex > 0) {
      register('previoustrack', () => {
        const current = playbackRef.current;
        if (current.trackIndex > 0) onChangeRef.current({ ...current, trackIndex: current.trackIndex - 1, playing: true });
      });
    }
    if (playback.trackIndex + 1 < playback.album.tracks.length) {
      register('nexttrack', () => {
        const current = playbackRef.current;
        if (current.trackIndex + 1 < current.album.tracks.length) {
          onChangeRef.current({ ...current, trackIndex: current.trackIndex + 1, playing: true });
        }
      });
    }
    register('seekto', (details) => {
      if (details.seekTime !== undefined) seek(details.seekTime);
    });
    register('seekbackward', (details) => seek(audioRef.current!.currentTime - (details.seekOffset ?? 10)));
    register('seekforward', (details) => seek(audioRef.current!.currentTime + (details.seekOffset ?? 10)));

    return () => {
      for (const action of registered) session.setActionHandler(action, null);
    };
  }, [playback.trackIndex, playback.album.tracks.length]);

  function moveTrack(index: number) {
    onChange({ ...playback, trackIndex: index, playing: true });
  }

  return (
    <aside className="audio-player" aria-label="Muziekspeler">
      {hasAlbumArt && (
        <AlbumArtImage albumName={playback.album.name} imageUrl={albumArt?.imageUrl} error={albumArt?.error} />
      )}
      <div className="player-content">
        <strong>{track.title}</strong>
        <div>{playback.artist} — {playback.album.name}</div>
        <audio
          ref={audioRef}
          preload="metadata"
          crossOrigin="anonymous"
          onTimeUpdate={(event) => setCurrentTime(event.currentTarget.currentTime)}
          onDurationChange={(event) => setDuration(Number.isFinite(event.currentTarget.duration) ? event.currentTarget.duration : 0)}
          onEnded={() => {
            if (playback.trackIndex + 1 < playback.album.tracks.length) moveTrack(playback.trackIndex + 1);
            else onChange({ ...playback, trackIndex: 0, playing: false });
          }}
          onError={() => {
            readyRef.current = false;
            reportFailure('de audiostream kon niet worden geladen.');
          }}
        />
        <div className="player-controls">
          <button type="button" aria-label="Vorig nummer" disabled={playback.trackIndex === 0} onClick={() => moveTrack(playback.trackIndex - 1)}>⏮</button>
          <button type="button" aria-label={playback.playing ? 'Pauzeren' : 'Afspelen'} onClick={() => {
            setError('');
            onChange({ ...playback, playing: !playback.playing });
          }}>{playback.playing ? '⏸' : '▶'}</button>
          <button type="button" aria-label="Volgend nummer" disabled={playback.trackIndex + 1 >= playback.album.tracks.length} onClick={() => moveTrack(playback.trackIndex + 1)}>⏭</button>
          <span>{formatTime(currentTime)}</span>
          <input
            type="range"
            aria-label="Afspeelpositie"
            min="0"
            max={duration || 0}
            step="0.1"
            value={Math.min(currentTime, duration)}
            disabled={!duration}
            onChange={(event) => {
              const time = Number(event.currentTarget.value);
              audioRef.current!.currentTime = time;
              setCurrentTime(time);
            }}
          />
          <span>{formatTime(duration)}</span>
        </div>
        {error && <p role="alert">{error}</p>}
      </div>
    </aside>
  );
}
