import { act, renderHook, waitFor } from '@testing-library/react';
import { GetAccounts, GetPeople, GetPositions, GetTrades } from './index';

const cases = [
  ['accounts', () => GetAccounts(), 'http://localhost:18088/account/'],
  ['people', () => GetPeople(), 'http://localhost:18095/People/'],
  ['positions', () => GetPositions(7), 'http://localhost:18090/positions/7'],
  ['trades', () => GetTrades(7), 'http://localhost:18090/trades/7']
] as const;
beforeEach(() => { global.fetch = jest.fn(); });
describe.each(cases)('%s', (_name, hook, url) => {
  test.each([['populated', [{ id: 7 }]], ['empty', []]] as const)('returns parsed %s response', async (_label, rows) => {
    (fetch as jest.Mock).mockResolvedValue({ ok: true, json: async () => rows });
    const { result } = renderHook(hook);
    expect(result.current).toEqual([]);
    await act(async () => {});
    await waitFor(() => expect(result.current).toEqual(rows));
    expect(fetch).toHaveBeenCalledWith(url);
  });
  test('does not parse unsuccessful HTTP responses', async () => {
    const json = jest.fn();
    (fetch as jest.Mock).mockResolvedValue({ ok: false, json });
    const { result } = renderHook(hook);
    await waitFor(() => expect(fetch).toHaveBeenCalledWith(url));
    expect(result.current).toEqual([]);
    expect(json).not.toHaveBeenCalled();
  });
});
test.each([['people', GetPeople], ['positions', () => GetPositions(7)], ['trades', () => GetTrades(7)]] as const)('%s retains empty data on network rejection', async (_name, hook) => {
  (fetch as jest.Mock).mockRejectedValue(new Error('offline'));
  const { result } = renderHook(hook);
  await waitFor(() => expect(fetch).toHaveBeenCalledTimes(1));
  expect(result.current).toEqual([]);
});
test.each([['positions', GetPositions], ['trades', GetTrades]] as const)('%s refetches when the account changes', async (resource, hook) => {
  (fetch as jest.Mock).mockImplementation(async (url: string) => ({ ok: true, json: async () => [{ account: url.split('/').pop() }] }));
  const { result, rerender } = renderHook(({ id }) => hook(id), { initialProps: { id: 7 } });
  await waitFor(() => expect(result.current).toEqual([{ account: '7' }]));
  rerender({ id: 8 });
  await waitFor(() => expect(result.current).toEqual([{ account: '8' }]));
  expect(fetch).toHaveBeenLastCalledWith('http://localhost:18090/' + resource + '/8');
});
test.todo('GetAccounts should catch rejected fetch promises instead of raising an unhandled rejection');
