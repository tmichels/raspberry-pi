import { useEffect, useState } from 'react';
import { getAlbumArt } from './api';

type AlbumArtState = {
  path: string;
  imageUrl?: string;
  error?: string;
};

export default function useAlbumArt(albumPath?: string) {
  const [state, setState] = useState<AlbumArtState>();

  useEffect(() => {
    setState(undefined);
    if (albumPath === undefined) return;
    let active = true;
    let objectUrl: string | undefined;

    getAlbumArt(albumPath).then((image) => {
      if (!active) return;
      objectUrl = URL.createObjectURL(image);
      setState({ path: albumPath, imageUrl: objectUrl });
    }).catch((failure: unknown) => {
      if (active) {
        setState({
          path: albumPath,
          error: `Albumhoes ophalen mislukt: ${failure instanceof Error ? failure.message : String(failure)}`,
        });
      }
    });

    return () => {
      active = false;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [albumPath]);

  return state?.path === albumPath ? state : undefined;
}
