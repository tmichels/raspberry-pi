import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import App from './App';
import { getAccessToken, logout } from './auth';

vi.mock('./auth', () => ({
  getAccessToken: vi.fn().mockResolvedValue('access-token'),
  getUsername: () => 'donna-user',
  logout: vi.fn().mockResolvedValue(undefined),
}));

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  vi.mocked(getAccessToken).mockResolvedValue('access-token');
});

function clickCreateFile() {
  render(<App />);
  const button = screen.getByRole<HTMLButtonElement>('button', { name: 'Bestand aanmaken' });
  fireEvent.click(button);
  return button;
}

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

  it('posts the fixed action and shows a successful result', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ output: '', exitCode: 0 })),
    );
    vi.stubGlobal('fetch', fetchMock);
    const timeout = vi.spyOn(AbortSignal, 'timeout');
    const button = clickCreateFile();

    await waitFor(() => {
      expect(screen.getByRole('status').textContent).toBe(
        'Commando uitgevoerd.\nExitcode: 0\nOutput:\n(geen output)',
      );
    });
    expect(button.disabled).toBe(false);
    expect(fetchMock).toHaveBeenCalledExactlyOnceWith('/api/commands/touch', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: 'Bearer access-token' },
      body: '{}',
      signal: expect.any(AbortSignal),
    });
    expect(timeout).toHaveBeenCalledWith(10_000);
  });

  it('fetches the music endpoint and displays its raw JSON response', async () => {
    const rawJson = '{\n  "artists": []\n}';
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(rawJson, { headers: { 'Content-Type': 'application/json' } }),
    );
    vi.stubGlobal('fetch', fetchMock);
    const timeout = vi.spyOn(AbortSignal, 'timeout');
    const button = clickGetMusic();

    await waitFor(() => {
      expect(screen.getByRole('status').textContent).toBe(rawJson);
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
  });

  it('disables the button while waiting for the backend', async () => {
    let finishRequest!: (response: Response) => void;
    vi.stubGlobal('fetch', vi.fn().mockReturnValue(new Promise<Response>((resolve) => {
      finishRequest = resolve;
    })));
    const button = clickCreateFile();

    expect(button.disabled).toBe(true);
    expect(screen.getByRole('status').textContent).toBe('Commando wordt uitgevoerd...');
    await waitFor(() => expect(finishRequest).toBeDefined());
    finishRequest(new Response(JSON.stringify({ output: '', exitCode: 0 })));
    await waitFor(() => expect(button.disabled).toBe(false));
  });

  it('shows output and a nonzero exit code as a failed command', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ output: 'Permission denied\n', exitCode: 1 })),
    ));
    clickCreateFile();

    await waitFor(() => {
      expect(screen.getByRole('status').textContent).toBe(
        'Commando mislukt.\nExitcode: 1\nOutput:\nPermission denied\n',
      );
    });
  });

  it('shows the backend problem detail and allows another request', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ detail: 'Controleer de logs op de Raspberry Pi.' }), {
        status: 500, headers: { 'Content-Type': 'application/problem+json' },
      }),
    ));
    const button = clickCreateFile();

    await waitFor(() => {
      expect(screen.getByRole('status').textContent).toBe('Mislukt: Controleer de logs op de Raspberry Pi.');
    });
    expect(button.disabled).toBe(false);
  });

  it('includes the HTTP status when the backend supplies no detail', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      new Response('{}', { status: 503 }),
    ));
    clickCreateFile();

    await waitFor(() => {
      expect(screen.getByRole('status').textContent).toBe('Mislukt: Request mislukt (HTTP 503).');
    });
  });

  it('warns that the command may have run after a timeout', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new DOMException('Timed out', 'TimeoutError')));
    const button = clickCreateFile();

    await waitFor(() => {
      expect(screen.getByRole('status').textContent).toBe(
        'Geen antwoord binnen 10 seconden. Het commando kan al zijn uitgevoerd; controleer de Pi.',
      );
    });
    expect(button.disabled).toBe(false);
  });

  it('shows network failures and enables the button again', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));
    const button = clickCreateFile();

    await waitFor(() => {
      expect(screen.getByRole('status').textContent).toBe('Mislukt: Failed to fetch');
    });
    expect(button.disabled).toBe(false);
  });

  it('does not call the backend when token refresh fails', async () => {
    vi.mocked(getAccessToken).mockRejectedValue(new Error('Je sessie is verlopen.'));
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const button = clickCreateFile();

    await waitFor(() => expect(screen.getByRole('status').textContent).toBe('Mislukt: Je sessie is verlopen.'));
    expect(fetchMock).not.toHaveBeenCalled();
    expect(button.disabled).toBe(false);
  });

  it.each([401, 403])('shows an explicit error for an empty HTTP %s response without retrying', async (status) => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status }));
    vi.stubGlobal('fetch', fetchMock);
    clickCreateFile();

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
