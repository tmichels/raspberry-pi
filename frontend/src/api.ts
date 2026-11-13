import { getAccessToken } from './auth';

export type MusicTrack = {
  title: string;
  path: string;
  status: 'OK' | 'MISSING_TAGS';
  missingTags: string[];
  tags: Record<string, string>;
};

export type MusicAlbum = {
  name: string;
  path: string;
  tags: Record<string, string>;
  tracks: MusicTrack[];
};

export type MusicArtist = {
  name: string;
  albums: MusicAlbum[];
};

export type MusicLibrary = {
  artists: MusicArtist[];
};

async function throwRequestError(response: Response): Promise<never> {
  if (response.status === 401) {
    throw new Error('Toegang geweigerd (HTTP 401). Herlaad de pagina om opnieuw in te loggen.');
  }
  if (response.status === 403) {
    throw new Error('Je hebt geen toegang tot deze actie (HTTP 403).');
  }
  const problem: { detail?: string } = response.headers.get('content-type')?.includes('json')
    ? await response.json()
    : {};
  throw new Error(problem.detail || `Request mislukt (HTTP ${response.status}).`);
}

export async function getMusic(): Promise<MusicLibrary> {
  const token = await getAccessToken();
  const response = await fetch('/api/music', {
    headers: {
      Authorization: `Bearer ${token}`,
    },
    signal: AbortSignal.timeout(10_000),
  });

  if (!response.ok) {
    await throwRequestError(response);
  }

  return response.json();
}

export async function getAlbumArt(albumPath: string): Promise<Blob> {
  const token = await getAccessToken();
  const response = await fetch(`/api/music/album-art?path=${encodeURIComponent(albumPath)}`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
    signal: AbortSignal.timeout(10_000),
  });

  if (!response.ok) {
    await throwRequestError(response);
  }

  return response.blob();
}
