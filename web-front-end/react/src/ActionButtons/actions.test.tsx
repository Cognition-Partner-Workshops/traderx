import React from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { CreateTradeButton } from './CreateTradeButton';
import { CreateAccount } from './CreateAccount';
import { CreateAccountUser } from './CreateAccountUser';

beforeEach(() => {
  global.fetch = jest.fn();
  jest.spyOn(Math, 'random').mockReturnValue(0.123);
});
afterEach(() => { jest.restoreAllMocks(); jest.useRealTimers(); });

const openTrade = async () => {
  render(<CreateTradeButton accountId={42} />);
  fireEvent.click(screen.getByRole('button', { name: 'Create New Trade' }));
  await waitFor(() => expect(fetch).toHaveBeenCalledWith('http://localhost:18085/stocks'));
};
const fillTrade = async (side: string) => {
  fireEvent.mouseDown(screen.getByRole('combobox'));
  fireEvent.click(await screen.findByRole('option', { name: 'IBM' }));
  fireEvent.change(screen.getByRole('spinbutton'), { target: { value: '25' } });
  fireEvent.click(screen.getByRole('button', { name: side }));
};

test.each(['Buy', 'Sell'])('submits a %s trade for the selected account and closes after success', async side => {
  (fetch as jest.Mock).mockResolvedValueOnce({ json: async () => [{ ticker: 'IBM' }] }).mockResolvedValueOnce({ ok: true });
  await openTrade();
  await fillTrade(side);
  jest.useFakeTimers();
  fireEvent.click(screen.getByRole('button', { name: 'Submit' }));
  await act(async () => {});
  expect(fetch).toHaveBeenLastCalledWith('http://localhost:18092/trade/', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ id: 'TRADE-123000', security: 'IBM', quantity: 25, accountId: 42, side })
  });
  expect(screen.getByText('Trade Created!')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Submit' })).not.toBeInTheDocument();
  await act(async () => { jest.advanceTimersByTime(2000); });
  expect(screen.queryByText('Trade Created!')).not.toBeInTheDocument();
  expect(screen.queryByRole('spinbutton')).not.toBeInTheDocument();
});

test('failed trade HTTP response leaves the ticket available for retry', async () => {
  (fetch as jest.Mock).mockResolvedValueOnce({ json: async () => [{ ticker: 'IBM' }] }).mockResolvedValue({ ok: false });
  await openTrade();
  await fillTrade('Buy');
  fireEvent.click(screen.getByRole('button', { name: 'Submit' }));
  await waitFor(() => expect(fetch).toHaveBeenCalledTimes(2));
  expect(screen.getByRole('spinbutton')).toHaveValue(25);
  expect(screen.getByRole('button', { name: 'Submit' })).toBeEnabled();
  expect(screen.queryByText('Trade Created!')).not.toBeInTheDocument();
});

test('trade request rejection displays the failure and retains entered data', async () => {
  (fetch as jest.Mock).mockResolvedValueOnce({ json: async () => [{ ticker: 'IBM' }] }).mockRejectedValue('Service unavailable');
  await openTrade();
  await fillTrade('Sell');
  fireEvent.click(screen.getByRole('button', { name: 'Submit' }));
  expect(await screen.findByText('Service unavailable')).toBeInTheDocument();
  expect(screen.getByRole('spinbutton')).toHaveValue(25);
});

test('reference-data rejection leaves an empty selector and permits closing the ticket', async () => {
  (fetch as jest.Mock).mockRejectedValue(new Error('offline'));
  await openTrade();
  fireEvent.mouseDown(screen.getByRole('combobox'));
  expect(screen.queryAllByRole('option')).toHaveLength(0);
  fireEvent.keyDown(screen.getByRole('listbox'), { key: 'Escape' });
  fireEvent.keyDown(screen.getByRole('spinbutton'), { key: 'Escape' });
  await waitFor(() => expect(screen.queryByRole('spinbutton')).not.toBeInTheDocument());
});

test('account creation posts the entered display name and generated id then closes', async () => {
  (fetch as jest.Mock).mockResolvedValue({ ok: true });
  render(<CreateAccount />);
  fireEvent.click(screen.getByRole('button', { name: 'Create Account' }));
  fireEvent.change(screen.getByLabelText(/Display Name/), { target: { value: 'Retirement' } });
  fireEvent.click(screen.getByRole('button', { name: 'Submit' }));
  await waitFor(() => expect(fetch).toHaveBeenCalledWith('http://localhost:18088/account/', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ id: 1230, displayName: 'Retirement' })
  }));
  await waitFor(() => expect(screen.queryByLabelText(/Display Name/)).not.toBeInTheDocument());
});

test('account display name is required before a request can be sent', async () => {
  render(<CreateAccount />);
  fireEvent.click(screen.getByRole('button', { name: 'Create Account' }));
  fireEvent.submit(screen.getByRole('button', { name: 'Submit' }).closest('form')!);
  expect(await screen.findAllByText(/required property/)).not.toHaveLength(0);
  expect(fetch).not.toHaveBeenCalled();
});

test('account network failure retains the form for retry', async () => {
  (fetch as jest.Mock).mockRejectedValue(new Error('offline'));
  render(<CreateAccount />);
  fireEvent.click(screen.getByRole('button', { name: 'Create Account' }));
  fireEvent.change(screen.getByLabelText(/Display Name/), { target: { value: 'Retirement' } });
  fireEvent.click(screen.getByRole('button', { name: 'Submit' }));
  await waitFor(() => expect(fetch).toHaveBeenCalledTimes(1));
  expect(screen.getByLabelText(/Display Name/)).toHaveValue('Retirement');
});

test.each([true, false])('account user searches by entered full name (network success: %s)', async success => {
  (fetch as jest.Mock).mockImplementation(() => success ? Promise.resolve({ json: async () => [{ fullName: 'Ada Lovelace' }, { fullName: 'Ada Smith' }] }) : Promise.reject(new Error('offline')));
  render(<CreateAccountUser accountId={42} />);
  fireEvent.click(screen.getByRole('button', { name: 'Create Account User' }));
  await act(async () => {
    fireEvent.change(screen.getByLabelText('Full Name'), { target: { value: 'Ada' } });
  });
  await waitFor(() => expect(fetch).toHaveBeenCalledWith('http://localhost:18095/People/GetMatchingPeople?SearchText=Ada'));
  expect(screen.getByLabelText('Full Name')).toHaveValue('Ada');
});

test.todo('trade ticket should reject missing security, side and non-positive quantity before submitting');
test.todo('trade network Error objects should render an error message rather than crash React');
test.todo('account creation should keep the form open on unsuccessful HTTP responses');
test.todo('account-user required schema should list username and fullName separately, allowing valid submissions');
test.todo('account-user submission should send username and fullName, not an absent displayName');
test.todo('matching people should be displayed for selection rather than only stored in unused state');
