import { cleanup, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import AuthGate from './AuthGate';
import { initializeAuth } from './auth';

vi.mock('./auth', () => ({ initializeAuth: vi.fn() }));
vi.mock('./App', () => ({ default: () => <button>Muziek ophalen</button> }));

afterEach(() => {
  cleanup();
  vi.resetAllMocks();
});

describe('Keycloak login gate', () => {
  it('hides actions until login succeeds', async () => {
    let finish!: (authenticated: boolean) => void;
    vi.mocked(initializeAuth).mockReturnValue(new Promise((resolve) => { finish = resolve; }));
    render(<AuthGate />);
    expect(screen.queryByRole('button')).toBeNull();
    expect(screen.getByRole('status').textContent).toBe('Inloggen via Keycloak...');
    finish(true);
    await waitFor(() => expect(screen.getByRole('button')).toBeDefined());
  });

  it('keeps actions hidden when login fails', async () => {
    vi.mocked(initializeAuth).mockRejectedValue(new Error('Keycloak niet bereikbaar'));
    render(<AuthGate />);
    await waitFor(() => expect(screen.getByRole('alert').textContent).toContain('Keycloak niet bereikbaar'));
    expect(screen.queryByRole('button')).toBeNull();
  });

  it('keeps actions hidden when the user is not authenticated', async () => {
    vi.mocked(initializeAuth).mockResolvedValue(false);
    render(<AuthGate />);
    await waitFor(() => expect(screen.getByRole('alert').textContent).toContain('Je bent niet ingelogd'));
    expect(screen.queryByRole('button')).toBeNull();
  });
});
