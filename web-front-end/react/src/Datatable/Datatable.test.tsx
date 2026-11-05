import React from 'react';
import { act, fireEvent, render, screen, within } from '@testing-library/react';
import { Datatable } from './Datatable';
import { socket } from '../socket';

jest.mock('../socket', () => ({ socket: { on: jest.fn(), off: jest.fn(), emit: jest.fn() } }));
jest.mock('../hooks', () => {
  const trades = [{ id: 'trade-1', security: 'IBM', quantity: 10, state: 'Processing', settlementDate: '2026-11-09' }];
  const positions = [{ security: 'IBM', quantity: 10 }];
  return { GetTrades: () => trades, GetPositions: () => positions };
});
jest.mock('../ActionButtons', () => ({ CreateTradeButton: () => null, CreateAccount: () => null, CreateAccountUser: () => null }));
jest.mock('../AccountsDropdown', () => ({ AccountsDropdown: ({ handleChange }: any) => (
  <select aria-label="Account" onChange={handleChange}><option value="0">Choose</option><option value="22214">Account</option></select>
) }));
jest.mock('ag-grid-react', () => ({ AgGridReact: ({ rowData, columnDefs }: any) => (
  <section aria-label={columnDefs.some((c: any) => c.field === 'state') ? 'Trades' : 'Positions'}>
    <header>{columnDefs.map((c: any) => c.field).join(',')}</header>
    {rowData.map((row: any, index: number) => <div key={index} data-testid="row">{JSON.stringify(row)}</div>)}
  </section>
) }));

function subscribe() {
  fireEvent.change(screen.getByLabelText('Account'), { target: { value: '22214' } });
  return (socket.on as jest.Mock).mock.calls.find(([event]) => event === 'publish')[1];
}

beforeEach(() => jest.clearAllMocks());

test('shows the stored historical settlement date next to state', () => {
  render(<Datatable />);
  const grid = screen.getByLabelText('Trades');
  expect(grid).toHaveTextContent('state,settlementDate');
  expect(grid).toHaveTextContent('2026-11-09');
  expect(grid).toHaveTextContent('Processing');
});

test.each(['2026-11-03', '2026-11-06', '2026-11-09'])('preserves live Processing date %s without recalculating it', date => {
  render(<Datatable />);
  const publish = subscribe();
  act(() => publish({ topic: '/accounts/22214/trades', payload: {
    id: 'trade-2', security: 'MS', quantity: 5, state: 'Processing', settlementDate: date
  } }));
  expect(screen.getByLabelText('Trades')).toHaveTextContent(date);
  expect(within(screen.getByLabelText('Trades')).getAllByTestId('row')).toHaveLength(2);
});

test('replaces a Processing row with Settled while preserving date and positions', () => {
  render(<Datatable />);
  const publish = subscribe();
  const positions = screen.getByLabelText('Positions').textContent;
  act(() => publish({ topic: '/accounts/22214/trades', payload: {
    id: 'trade-1', security: 'IBM', quantity: 10, state: 'Settled', settlementDate: '2026-11-09'
  } }));
  const grid = screen.getByLabelText('Trades');
  expect(within(grid).getAllByTestId('row')).toHaveLength(1);
  expect(grid).toHaveTextContent('Settled');
  expect(grid).not.toHaveTextContent('Processing');
  expect(grid).toHaveTextContent('2026-11-09');
  expect(screen.getByLabelText('Positions').textContent).toBe(positions);
});

test('ignores system notifications instead of adding empty trades', () => {
  render(<Datatable />);
  const publish = subscribe();
  act(() => publish({ from: 'System', topic: '/accounts/22214/trades', payload: { message: 'joined' } }));
  expect(within(screen.getByLabelText('Trades')).getAllByTestId('row')).toHaveLength(1);
});
