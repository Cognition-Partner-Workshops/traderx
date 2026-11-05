import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, test, vi } from 'vitest';
import App from './App';
import { Environment } from './env';

const { socket } = vi.hoisted(() => ({
  socket: { on: vi.fn(), off: vi.fn(), emit: vi.fn() },
}));

vi.mock('socket.io-client', () => ({ io: () => socket }));

beforeEach(() => {
  vi.clearAllMocks();
});

test.each(['populated', 'empty', 'error'])('renders trading controls and grids with %s accounts', async (scenario) => {
  const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
    const url = String(input);
    const accounts = url === `${Environment.account_service_url}/account/`;
    return {
      ok: !(accounts && scenario === 'error'),
      json: async () => accounts && scenario === 'populated'
        ? [{ id: 42, displayName: 'Trading account' }]
        : [],
    } as Response;
  });
  vi.stubGlobal('fetch', fetchMock);
  render(<App />);
  expect(screen.getByRole('combobox')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Create New Trade' })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Create Account' })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Create Account User' })).toBeInTheDocument();
  await waitFor(() => expect(screen.getAllByRole('grid')).toHaveLength(2));
  await waitFor(() => expect(screen.getAllByRole('columnheader', { name: 'Security' })).toHaveLength(2));
  expect(fetchMock).toHaveBeenCalledWith(`${Environment.account_service_url}/account/`);

  const user = userEvent.setup();
  await user.click(screen.getByRole('combobox'));
  expect(screen.getByRole('option', { name: 'None' })).toBeInTheDocument();
  if (scenario === 'populated') {
    await user.click(await screen.findByRole('option', { name: 'Trading account' }));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(`${Environment.position_service_url}/trades/42`));
    expect(fetchMock).toHaveBeenCalledWith(`${Environment.position_service_url}/positions/42`);
    expect(socket.emit).toHaveBeenCalledWith('subscribe', '/accounts/42/trades');
    expect(socket.emit).toHaveBeenCalledWith('subscribe', '/accounts/42/positions');
  } else {
    expect(screen.queryByRole('option', { name: 'Trading account' })).not.toBeInTheDocument();
  }
});
