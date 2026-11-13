import AlbumArt from './AlbumArt';
import type { MusicAlbum } from './api';

export default function AlbumDetails({ album, playingTrackPath }: { album: MusicAlbum; playingTrackPath?: string }) {
  const showTrackNumbers = album.tracks.every((track) => Object.hasOwn(track.tags, 'TRACK'));

  return (
    <div className="album-content">
      <div className="album-details">
        <div className="album-info">
          {Object.keys(album.tags).some((key) => key !== 'COVER_ART') && (
            <dl className="metadata">
              {Object.entries(album.tags).filter(([key]) => key !== 'COVER_ART').map(([key, value]) => (
                <div key={key}><dt>{key}</dt><dd>{value}</dd></div>
              ))}
            </dl>
          )}
        </div>
        {Object.hasOwn(album.tags, 'COVER_ART') && (
          <AlbumArt albumName={album.name} albumPath={album.path} />
        )}
      </div>
      {album.tracks.length > 0 ? (
        <ol className={`music-list tracks${showTrackNumbers ? ' tracks--numbered' : ''}`}>
          {album.tracks.map((track) => (
            <li key={track.path} className={track.path === playingTrackPath ? 'track-playing' : undefined} aria-current={track.path === playingTrackPath ? 'true' : undefined}>
              {showTrackNumbers && <span className="track-number">{track.tags.TRACK}</span>}
              <div className="track-details">
                <strong>{track.title}</strong>
                {track.status === 'MISSING_TAGS' && (
                  <span className="track-warning">
                    Ontbrekende tags: {track.missingTags.join(', ')}
                  </span>
                )}
                {Object.keys(track.tags).some((key) => !showTrackNumbers || key !== 'TRACK') && (
                  <dl className="metadata">
                    {Object.entries(track.tags).filter(([key]) => !showTrackNumbers || key !== 'TRACK').map(([key, value]) => (
                      <div key={key}><dt>{key}</dt><dd>{value}</dd></div>
                    ))}
                  </dl>
                )}
              </div>
            </li>
          ))}
        </ol>
      ) : <p>Geen nummers gevonden.</p>}
    </div>
  );
}
