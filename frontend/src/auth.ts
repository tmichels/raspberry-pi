import Keycloak from 'keycloak-js';

const keycloak = new Keycloak({
  url: import.meta.env.VITE_KEYCLOAK_URL || 'http://192.168.178.96:9000',
  realm: 'donna',
  clientId: 'pi',
});

let initialization: Promise<boolean> | undefined;

export function initializeAuth(): Promise<boolean> {
  initialization ??= keycloak.init({
    onLoad: 'login-required',
    pkceMethod: 'S256',
    checkLoginIframe: false,
    redirectUri: window.location.origin,
  });
  return initialization;
}

export function getUsername(): string {
  return keycloak.tokenParsed?.preferred_username || 'gebruiker';
}

export async function getAccessToken(): Promise<string> {
  try {
    await keycloak.updateToken(30);
  } catch {
    keycloak.clearToken();
    throw new Error('Je sessie is verlopen. Log opnieuw in door de pagina te herladen.');
  }
  if (!keycloak.authenticated || !keycloak.token) {
    throw new Error('Je bent niet ingelogd. Herlaad de pagina om in te loggen.');
  }
  return keycloak.token;
}

export function logout(): Promise<void> {
  return keycloak.logout({ redirectUri: window.location.origin });
}
