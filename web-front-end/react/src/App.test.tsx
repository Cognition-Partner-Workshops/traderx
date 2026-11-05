import React from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import App from './App';
import { socket } from './socket';

jest.mock('./socket', () => ({ socket: { on: jest.fn(), off: jest.fn(), emit: jest.fn() } }));
jest.mock('ag-grid-react', () => ({
  AgGridReact: ({ rowData, columnDefs }: any) => <pre data-testid="grid">{JSON.stringify({ rowData, columns: columnDefs.map((c: any) => c.field) })}</pre>
}));
const grids = () => screen.getAllByTestId('grid').map(grid => JSON.parse(grid.textContent!));
const choose = async (name: string) => {
  fireEvent.mouseDown(screen.getByRole('combobox'));
  fireEvent.click(await screen.findByRole('option', { name }));
  await act(async () => {});
};
beforeEach(() => {
  jest.clearAllMocks();
  global.fetch = jest.fn(async (url: any) => ({ ok: true, json: async () =>
    url.endsWith('/account/') ? [{ id: 7, displayName: 'Primary' }, { id: 8, displayName: 'Savings' }] :
    url.includes('/positions/') ? [{ security: 'IBM', quantity: 12 }] : [{ security: 'MSFT', side: 'Buy' }]
  })) as jest.Mock;
});
test('App selects accounts, loads blotters and routes live updates only to the selected account', async () => {
  render(<App />);
  await waitFor(() => expect(grids()[0].rowData).toEqual([{ security: 'MSFT', side: 'Buy' }]));
  expect(grids()[0].columns).toEqual(['security', 'quantity', 'side', 'state', 'updated']);
  expect(grids()[1]).toEqual({ rowData: [{ security: 'IBM', quantity: 12 }], columns: ['security', 'quantity', 'updated'] });
  await choose('Primary');
  expect(screen.getByRole('combobox')).toHaveTextContent('Primary');
  expect(fetch).toHaveBeenCalledWith('http://localhost:18090/trades/7');
  expect(fetch).toHaveBeenCalledWith('http://localhost:18090/positions/7');
  expect(socket.emit).toHaveBeenCalledWith('subscribe', '/accounts/7/trades');
  expect(socket.emit).toHaveBeenCalledWith('subscribe', '/accounts/7/positions');
  expect(socket.emit).not.toHaveBeenCalledWith('unsubscribe', expect.anything());
  await waitFor(() => expect(grids()[0].rowData).toHaveLength(1));
  const publish = (socket.on as jest.Mock).mock.calls[0][1];
  act(() => {
    publish({ topic: '/accounts/99/trades', payload: { security: 'WRONG' } });
    publish({ topic: '/accounts/7/trades', payload: { security: 'AAPL' } });
    publish({ topic: '/accounts/7/positions', payload: { security: 'GOOG' } });
  });
  expect(grids()[0].rowData).toEqual([{ security: 'MSFT', side: 'Buy' }, { security: 'AAPL' }]);
  expect(grids()[1].rowData).toEqual([{ security: 'IBM', quantity: 12 }, { security: 'GOOG' }]);
  await choose('Savings');
  expect(socket.off).toHaveBeenCalledTimes(2);
  expect(socket.off).toHaveBeenLastCalledWith('publish');
  for (const resource of ['trades', 'positions']) {
    expect(socket.emit).toHaveBeenCalledWith('unsubscribe', '/accounts/7/' + resource);
    expect(socket.emit).toHaveBeenCalledWith('subscribe', '/accounts/8/' + resource);
    expect(fetch).toHaveBeenCalledWith('http://localhost:18090/' + resource + '/8');
  }
  await waitFor(() => expect(grids()[0].rowData).toEqual([{ security: 'MSFT', side: 'Buy' }]));
});

test('empty accounts offer only None and leave both blotters empty', async () => {
  (fetch as jest.Mock).mockResolvedValue({ ok: true, json: async () => [] });
  render(<App />);
  fireEvent.mouseDown(screen.getByRole('combobox'));
  expect(await screen.findAllByRole('option')).toHaveLength(1);
  expect(screen.getByRole('option')).toHaveTextContent('None');
  await waitFor(() => expect(grids().map(grid => grid.rowData)).toEqual([[], []]));
});
