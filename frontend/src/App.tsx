import { useState } from 'react';
import { createFile, getMusic } from './api';
import { getUsername, logout } from './auth';
import type { CommandResult, MusicLibrary } from './api';

export default function App() {
  const [isRunning, setIsRunning] = useState(false);
  const [status, setStatus] = useState('');
  const [music, setMusic] = useState<MusicLibrary>();
  const [selectedArtistIndex, setSelectedArtistIndex] = useState<number | null>(null);
  const [selectedAlbumIndex, setSelectedAlbumIndex] = useState<number | null>(null);
  const selectedArtist = selectedArtistIndex === null ? undefined : music?.artists[selectedArtistIndex];
  const selectedAlbum = selectedAlbumIndex === null ? undefined : selectedArtist?.albums[selectedAlbumIndex];

  async function handleLogout() {
    try {
      await logout();
    } catch (error) {
      setStatus(`Uitloggen mislukt: ${error instanceof Error ? error.message : String(error)}`);
    }
  }

  async function handleCommand(
    run: () => Promise<CommandResult>,
    progressMessage: string,
    successMessage: string,
    failureMessage: string,
  ) {
    setIsRunning(true);
    setStatus(progressMessage);

    try {
      const result = await run();
      const message = result.exitCode === 0 ? successMessage : failureMessage;
      setStatus(
        `${message}\nExitcode: ${result.exitCode}\nOutput:\n${result.output || '(geen output)'}`,
      );
    } catch (error) {
      if ((error instanceof Error || error instanceof DOMException) && error.name === 'TimeoutError') {
        setStatus('Geen antwoord binnen 10 seconden. Het commando kan al zijn uitgevoerd; controleer de Pi.');
      } else {
        setStatus(`Mislukt: ${error instanceof Error ? error.message : String(error)}`);
      }
    } finally {
      setIsRunning(false);
    }
  }

  function handleCreateFile() {
    return handleCommand(
      createFile,
      'Commando wordt uitgevoerd...',
      'Commando uitgevoerd.',
      'Commando mislukt.',
    );
  }

  async function handleGetMusic() {
    setIsRunning(true);
    setMusic(undefined);
    setSelectedArtistIndex(null);
    setSelectedAlbumIndex(null);
    setStatus('Muziek wordt opgehaald...');

    try {
      setMusic(await getMusic());
      setStatus('');
    } catch (error) {
      if ((error instanceof Error || error instanceof DOMException) && error.name === 'TimeoutError') {
        setStatus('Geen antwoord binnen 10 seconden. Probeer het opnieuw.');
      } else {
        setStatus(`Mislukt: ${error instanceof Error ? error.message : String(error)}`);
      }
    } finally {
      setIsRunning(false);
    }
  }

  return (
    <main>
      <h1>Raspberry Pi bediening</h1>
      <p>Ingelogd als {getUsername()}.</p>
      <button type="button" disabled={isRunning} onClick={handleLogout}>Uitloggen</button>
      <p>Maak een bestand met de huidige datum en tijd op de Raspberry Pi.</p>
      <button type="button" disabled={isRunning} onClick={handleCreateFile}>
        Bestand aanmaken
      </button>
      <p>Blader door artiesten, albums en nummers in je muziekbibliotheek.</p>
      <button type="button" disabled={isRunning} onClick={handleGetMusic}>
        Muziek ophalen
      </button>
      <p className="status" role="status" aria-live="polite">{status}</p>
      {music && (
        <section className="music-library" aria-label="Muziekbibliotheek">
          {selectedAlbum && selectedArtist ? (
            <>
              <button
                type="button"
                className="back-button"
                onClick={() => setSelectedAlbumIndex(null)}
              >
                ← Albums van {selectedArtist.name}
              </button>
              <h2>{selectedAlbum.name}</h2>
              <p>{selectedAlbum.tracks.length} nummers</p>
              {Object.keys(selectedAlbum.tags).length > 0 && (
                <dl className="metadata">
                  {Object.entries(selectedAlbum.tags).map(([key, value]) => (
                    <div key={key}><dt>{key}</dt><dd>{value}</dd></div>
                  ))}
                </dl>
              )}
              {selectedAlbum.tracks.length > 0 ? (
                <ol className="music-list tracks">
                  {selectedAlbum.tracks.map((track) => (
                    <li key={track.path}>
                      <strong>{track.title}</strong>
                      {track.status === 'MISSING_TAGS' && (
                        <span className="track-warning">
                          Ontbrekende tags: {track.missingTags.join(', ')}
                        </span>
                      )}
                      {Object.keys(track.tags).length > 0 && (
                        <dl className="metadata">
                          {Object.entries(track.tags).map(([key, value]) => (
                            <div key={key}><dt>{key}</dt><dd>{value}</dd></div>
                          ))}
                        </dl>
                      )}
                    </li>
                  ))}
                </ol>
              ) : <p>Geen nummers gevonden.</p>}
            </>
          ) : selectedArtist ? (
            <>
              <button
                type="button"
                className="back-button"
                onClick={() => setSelectedArtistIndex(null)}
              >
                ← Alle artiesten
              </button>
              <h2>{selectedArtist.name}</h2>
              <p>{selectedArtist.albums.length} albums</p>
              {selectedArtist.albums.length > 0 ? (
                <ul className="music-list">
                  {selectedArtist.albums.map((album, index) => (
                    <li key={album.name}>
                      <button
                        type="button"
                        className="music-item"
                        onClick={() => setSelectedAlbumIndex(index)}
                      >
                        <span>{album.name}</span>
                        <span className="item-count">{album.tracks.length} nummers</span>
                      </button>
                    </li>
                  ))}
                </ul>
              ) : <p>Geen albums gevonden.</p>}
            </>
          ) : (
            <>
              <h2>Artiesten</h2>
              {music.artists.length > 0 ? (
                <ul className="music-list">
                  {music.artists.map((artist, index) => (
                    <li key={artist.name}>
                      <button
                        type="button"
                        className="music-item"
                        onClick={() => setSelectedArtistIndex(index)}
                      >
                        <span>{artist.name}</span>
                        <span className="item-count">{artist.albums.length} albums</span>
                      </button>
                    </li>
                  ))}
                </ul>
              ) : <p>Geen artiesten gevonden.</p>}
            </>
          )}
        </section>
      )}
    </main>
  );
}
