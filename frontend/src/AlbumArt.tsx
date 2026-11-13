import { useEffect, useState } from 'react';
import { getAlbumArt } from './api';

type AlbumArtProps = {
  albumName: string;
  albumPath: string;
};

export default function AlbumArt({ albumName, albumPath }: AlbumArtProps) {
  const [imageUrl, setImageUrl] = useState<string>();
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    let objectUrl: string | undefined;

    getAlbumArt(albumPath).then((image) => {
      if (!active) return;
      objectUrl = URL.createObjectURL(image);
      setImageUrl(objectUrl);
    }).catch((failure: unknown) => {
      if (active) {
        setError(`Albumhoes ophalen mislukt: ${failure instanceof Error ? failure.message : String(failure)}`);
      }
    });

    return () => {
      active = false;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [albumPath]);

  if (error) return <p role="alert">{error}</p>;
  if (!imageUrl) return <p role="status">Albumhoes wordt opgehaald...</p>;
  return <img className="album-art" src={imageUrl} alt={`Albumhoes van ${albumName}`} />;
}
