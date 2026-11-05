import React, { act } from 'react';
import { render, screen } from '@testing-library/react';
import App from './App';

jest.mock('./socket', () => ({
  socket: { on: jest.fn(), off: jest.fn(), emit: jest.fn() }
}));

const originalFetch = global.fetch;

beforeEach(() => {
  global.fetch = jest.fn().mockResolvedValue({ ok: true, json: async () => [] });
});

afterEach(() => {
  global.fetch = originalFetch;
});

test('renders the trading dashboard', async () => {
  await act(async () => {
    render(<App />);
  });
  expect(screen.getByRole('button', { name: /create new trade/i })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /^create account$/i })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /create account user/i })).toBeInTheDocument();
});
