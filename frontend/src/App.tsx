import { useState } from 'react';
import AlbumDetails from './AlbumDetails';
import { getMusic } from './api';
import { getUsername, logout } from './auth';
import type { MusicLibrary } from './api';

export default function App() {
  const [isRunning, setIsRunning] = useState(false);
  const [status, setStatus] = useState('');
  const [music, setMusic] = useState<MusicLibrary>();
  const [selectedArtistIndex, setSelectedArtistIndex] = useState<number | null>(null);
  const [selectedAlbumIndex, setSelectedAlbumIndex] = useState<number | null>(null);
  const selectedArtist = selectedArtistIndex === null ? undefined : music?.artists[selectedArtistIndex];

  async function handleLogout() {
    try {
      await logout();
    } catch (error) {
      setStatus(`Uitloggen mislukt: ${error instanceof Error ? error.message : String(error)}`);
    }
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
      <p>Blader door artiesten, albums en nummers in je muziekbibliotheek.</p>
      <button type="button" disabled={isRunning} onClick={handleGetMusic}>
        Muziek ophalen
      </button>
      <p className="status" role="status" aria-live="polite">{status}</p>
      {music && (
        <section className="music-library" aria-label="Muziekbibliotheek">
          {selectedArtist ? (
            <>
              <button
                type="button"
                className="back-button"
                onClick={() => {
                  setSelectedArtistIndex(null);
                  setSelectedAlbumIndex(null);
                }}
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
                        aria-expanded={selectedAlbumIndex === index}
                        onClick={() => setSelectedAlbumIndex(selectedAlbumIndex === index ? null : index)}
                      >
                        <span>{album.name}</span>
                        <span className="item-count">{album.tracks.length} nummers</span>
                      </button>
                      {selectedAlbumIndex === index && <AlbumDetails album={album} />}
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
