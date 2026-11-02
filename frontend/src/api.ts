type CommandResult = {
  output: string;
  exitCode: number;
};

export async function createFile(): Promise<CommandResult> {
  const response = await fetch('/api/commands/touch', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: '{}',
    signal: AbortSignal.timeout(10_000),
  });

  if (!response.ok) {
    const problem: { detail?: string } = await response.json();
    throw new Error(problem.detail || `Request mislukt (HTTP ${response.status}).`);
  }

  return response.json();
}
