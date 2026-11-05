import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { AccountService } from './account.service';
import { PositionService } from './position.service';
import { SymbolService } from './symbols.service';
import { UserService } from './user.service';
import { environment as env } from '../../environments/environment';

describe('Business HTTP contracts', () => {
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  const cases = [
    { name: 'accounts', service: AccountService, call: (s: any) => s.getAccounts(), url: () => env.accountUrl + '/account/', retries: 2 },
    { name: 'account users', service: AccountService, call: (s: any) => s.getAccountUsers(), url: () => env.accountUrl + '/accountuser/', retries: 0 },
    { name: 'trades', service: PositionService, call: (s: any) => s.getTrades(7), url: () => env.positionsUrl + '/trades/7', retries: 0 },
    { name: 'positions', service: PositionService, call: (s: any) => s.getPositions(7), url: () => env.positionsUrl + '/positions/7', retries: 0 },
    { name: 'stocks', service: SymbolService, call: (s: any) => s.getStocks(), url: () => env.refrenceDataUrl + '/stocks', retries: 2 }
  ];
  cases.forEach(c => {
    [[], [{ id: 7 }]].forEach(rows => it(c.name + ' GET returns ' + JSON.stringify(rows), () => {
      const next = jasmine.createSpy('next');
      c.call(TestBed.inject(c.service as any)).subscribe(next);
      const req = http.expectOne(c.url());
      expect(req.request.method).toBe('GET');
      req.flush(rows);
      expect(next).toHaveBeenCalledOnceWith(rows);
    }));
    it(c.name + ' propagates HTTP failure after configured retries', () => {
      const error = jasmine.createSpy('error');
      c.call(TestBed.inject(c.service as any)).subscribe({ error });
      for (let attempt = 0; attempt <= c.retries; attempt++) {
        http.expectOne(c.url()).flush('unavailable', { status: 503, statusText: 'Unavailable' });
      }
      expect(error).toHaveBeenCalledWith(jasmine.objectContaining({ status: 503 }));
    });
  });
  [
    { name: 'account', service: AccountService, call: (s: any, body: any) => s.addAccount(body), url: () => env.accountUrl + '/account/', body: { id: 7, displayName: 'Savings' } },
    { name: 'assignment', service: AccountService, call: (s: any, body: any) => s.addAccountUser(body), url: () => env.accountUrl + '/accountuser/', body: { accountId: 7, username: 'ada' } },
    { name: 'ticket', service: SymbolService, call: (s: any, body: any) => s.createTicket(body), url: () => env.tradesUrl, body: { accountId: 7, security: 'IBM', quantity: 25, side: 'Sell' } }
  ].forEach(c => {
    it(c.name + ' POST sends exact body and returns server response', () => {
      const next = jasmine.createSpy('next');
      c.call(TestBed.inject(c.service as any), c.body).subscribe(next);
      const req = http.expectOne(c.url());
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(c.body);
      req.flush({ accepted: true });
      expect(next).toHaveBeenCalledOnceWith({ accepted: true });
    });
    it(c.name + ' POST propagates errors without retry', () => {
      const error = jasmine.createSpy('error');
      c.call(TestBed.inject(c.service as any), c.body).subscribe({ error });
      http.expectOne(c.url()).flush('invalid', { status: 400, statusText: 'Bad Request' });
      expect(error).toHaveBeenCalledWith(jasmine.objectContaining({ status: 400 }));
    });
  });
  it('user search encodes query parameters and extracts people', () => {
    const next = jasmine.createSpy('next');
    TestBed.inject(UserService).getUsers('Ada & Bob').subscribe(next);
    const req = http.expectOne(r => r.url === env.peopleUrl + '/People/GetMatchingPeople');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('SearchText')).toBe('Ada & Bob');
    expect(req.request.params.get('Take')).toBe('10');
    req.flush({ people: [{ logonId: 'ada' }] });
    expect(next).toHaveBeenCalledOnceWith([{ logonId: 'ada' }]);
  });
  it('user search defaults absent people to an empty list', () => {
    const next = jasmine.createSpy('next');
    TestBed.inject(UserService).getUsers('Ada').subscribe(next);
    http.expectOne(r => r.url.endsWith('/People/GetMatchingPeople')).flush({});
    expect(next).toHaveBeenCalledOnceWith([]);
  });
  it('user search forwards HTTP errors', () => {
    const error = jasmine.createSpy('error');
    TestBed.inject(UserService).getUsers('Ada').subscribe({ error });
    http.expectOne(r => r.url.endsWith('/People/GetMatchingPeople')).flush('error', { status: 500, statusText: 'Error' });
    expect(error).toHaveBeenCalledWith(jasmine.objectContaining({ status: 500 }));
  });
});
