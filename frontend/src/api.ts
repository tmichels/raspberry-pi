import { getAccessToken } from './auth';

type CommandResult = {
  output: string;
  exitCode: number;
};

export async function createFile(): Promise<CommandResult> {
  const token = await getAccessToken();
  const response = await fetch('/api/commands/touch', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: '{}',
    signal: AbortSignal.timeout(10_000),
  });

  if (!response.ok) {
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

  return response.json();
}
