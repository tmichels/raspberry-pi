import useAlbumArt from './useAlbumArt';

type AlbumArtProps = {
  albumName: string;
  albumPath: string;
};

export default function AlbumArt({ albumName, albumPath }: AlbumArtProps) {
  const art = useAlbumArt(albumPath);
  return <AlbumArtImage albumName={albumName} imageUrl={art?.imageUrl} error={art?.error} />;
}

export function AlbumArtImage({ albumName, imageUrl, error }: {
  albumName: string;
  imageUrl?: string;
  error?: string;
}) {
  if (error) return <p role="alert">{error}</p>;
  if (!imageUrl) return <p role="status">Albumhoes wordt opgehaald...</p>;
  return <img className="album-art" src={imageUrl} alt={`Albumhoes van ${albumName}`} />;
}
