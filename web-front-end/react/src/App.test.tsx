import React from 'react';
import { render, screen } from '@testing-library/react';
import App from './App';

jest.mock('./Datatable/Datatable', () => ({ Datatable: () => <main aria-label="Trading blotter" /> }));

test('renders the trading blotter', () => {
  render(<App />);
  expect(screen.getByRole('main', { name: 'Trading blotter' })).toBeInTheDocument();
});
