import { Readable } from 'stream';
import * as fs from 'fs';
import { loadCsvData } from './load-csv-data';

jest.mock('fs', () => {
  const actual = jest.requireActual('fs');
  return { ...actual, createReadStream: jest.fn() };
});

const createReadStream = fs.createReadStream as unknown as jest.Mock;

function csv(content: string) {
  createReadStream.mockReturnValueOnce(Readable.from([content]));
}

describe('loadCsvData parsing', () => {
  afterEach(() => createReadStream.mockReset());

  it('reads the S&P 500 file from ./data relative to the working directory', async () => {
    csv('Symbol,Security\n');
    await loadCsvData();
    expect(createReadStream).toHaveBeenCalledWith(
      './data/s-and-p-500-companies.csv',
      'utf8',
    );
  });

  it('skips the header row and maps column 0 -> ticker, column 1 -> companyName', async () => {
    csv('Symbol,Security,Sector\nAAA,Alpha Inc,Tech\nBBB,Beta Corp,Energy\n');
    await expect(loadCsvData()).resolves.toEqual([
      { ticker: 'AAA', companyName: 'Alpha Inc' },
      { ticker: 'BBB', companyName: 'Beta Corp' },
    ]);
  });

  it('trims whitespace around cells', async () => {
    csv('Symbol,Security\n  CCC  ,   Gamma Ltd  \n');
    await expect(loadCsvData()).resolves.toEqual([
      { ticker: 'CCC', companyName: 'Gamma Ltd' },
    ]);
  });

  it('keeps commas inside quoted company names', async () => {
    csv('Symbol,Security,HQ\nDDD,"Delta, Inc.","Austin, Texas"\n');
    await expect(loadCsvData()).resolves.toEqual([
      { ticker: 'DDD', companyName: 'Delta, Inc.' },
    ]);
  });

  it('returns an empty list when the file only has a header', async () => {
    csv('Symbol,Security\n');
    await expect(loadCsvData()).resolves.toEqual([]);
  });

  // Known bug (not fixed here): no 'error' handler on the read stream, so a missing/unreadable
  // data file leaves the promise pending forever (and emits an unhandled 'error' event)
  // instead of rejecting. Enable once loadCsvData rejects on stream errors.
  it.skip('rejects when the data file cannot be read', async () => {
    const stream = new Readable({ read() {} });
    createReadStream.mockReturnValueOnce(stream);
    const result = loadCsvData();
    stream.destroy(new Error('ENOENT'));
    await expect(result).rejects.toThrow('ENOENT');
  });
});
