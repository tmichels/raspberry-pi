import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  configure: vi.fn(),
  init: vi.fn(),
  updateToken: vi.fn(),
  clearToken: vi.fn(),
  logout: vi.fn(),
  keycloak: { authenticated: true, token: 'access-token', tokenParsed: { preferred_username: 'donna-user' } },
}));

vi.mock('keycloak-js', () => ({
  default: class {
    constructor(config: object) { mocks.configure(config); }
    init = mocks.init;
    updateToken = mocks.updateToken;
    clearToken = mocks.clearToken;
    logout = mocks.logout;
    get authenticated() { return mocks.keycloak.authenticated; }
    get token() { return mocks.keycloak.token; }
    get tokenParsed() { return mocks.keycloak.tokenParsed; }
  },
}));

beforeEach(() => {
  vi.resetModules();
  vi.resetAllMocks();
  mocks.keycloak.authenticated = true;
  mocks.keycloak.token = 'access-token';
});

describe('Keycloak adapter', () => {
  it('uses the same HTTPS Keycloak URL in every environment', async () => {
    await import('./auth');
    expect(mocks.configure).toHaveBeenCalledExactlyOnceWith({
      url: 'https://login.thomasmichels.nl',
      realm: 'donna',
      clientId: 'pi',
    });
  });

  it('initializes login-required with PKCE only once', async () => {
    mocks.init.mockResolvedValue(true);
    const { initializeAuth } = await import('./auth');
    await Promise.all([initializeAuth(), initializeAuth()]);
    expect(mocks.init).toHaveBeenCalledExactlyOnceWith({
      onLoad: 'login-required',
      pkceMethod: 'S256',
      checkLoginIframe: false,
      redirectUri: window.location.origin,
    });
  });

  it('refreshes the token before use and returns the current token', async () => {
    mocks.updateToken.mockImplementation(async () => { mocks.keycloak.token = 'refreshed-token'; });
    const { getAccessToken, getUsername } = await import('./auth');
    expect(await getAccessToken()).toBe('refreshed-token');
    expect(mocks.updateToken).toHaveBeenCalledWith(30);
    expect(getUsername()).toBe('donna-user');
  });

  it('clears tokens and reports a refresh failure', async () => {
    mocks.updateToken.mockRejectedValue(new Error('Refresh failed'));
    const { getAccessToken } = await import('./auth');
    await expect(getAccessToken()).rejects.toThrow('Je sessie is verlopen');
    expect(mocks.clearToken).toHaveBeenCalledOnce();
  });

  it('rejects an unauthenticated session', async () => {
    mocks.keycloak.authenticated = false;
    const { getAccessToken } = await import('./auth');
    await expect(getAccessToken()).rejects.toThrow('Je bent niet ingelogd');
  });

  it('returns to the frontend origin after logout', async () => {
    const { logout } = await import('./auth');
    await logout();
    expect(mocks.logout).toHaveBeenCalledWith({ redirectUri: window.location.origin });
  });
});
