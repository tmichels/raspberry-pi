import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import App from './App';

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

function clickCreateFile() {
  render(<App />);
  const button = screen.getByRole<HTMLButtonElement>('button', { name: 'Bestand aanmaken' });
  fireEvent.click(button);
  return button;
}

describe('Raspberry Pi bediening', () => {
  it('shows the action and the authentication warning', () => {
    render(<App />);
    expect(screen.getByRole('heading').textContent).toBe('Raspberry Pi bediening');
    expect(screen.getByText(/Deze POC heeft geen authenticatie/)).toBeDefined();
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
      headers: { 'Content-Type': 'application/json' },
      body: '{}',
      signal: expect.any(AbortSignal),
    });
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
      new Response(JSON.stringify({ detail: 'Controleer de logs op de Raspberry Pi.' }), { status: 500 }),
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
});
