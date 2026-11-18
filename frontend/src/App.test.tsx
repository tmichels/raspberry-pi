import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import App from './App';
import { logout } from './auth';

vi.mock('./auth', () => ({
  getUsername: () => 'donna-user',
  logout: vi.fn().mockResolvedValue(undefined),
}));

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

describe('Raspberry Pi bediening', () => {
  it('shows the signed-in user and only the logout action', () => {
    render(<App />);

    expect(screen.getByRole('heading', { name: 'Raspberry Pi bediening' })).toBeDefined();
    expect(screen.getByText('Ingelogd als donna-user.')).toBeDefined();
    expect(screen.getByRole('button', { name: 'Uitloggen' })).toBeDefined();
    expect(screen.queryByRole('button', { name: 'Muziek ophalen' })).toBeNull();
    expect(screen.getByRole('status').textContent).toBe('');
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

    await waitFor(() => {
      expect(screen.getByRole('status').textContent).toBe('Uitloggen mislukt: Keycloak niet bereikbaar');
    });
  });
});
