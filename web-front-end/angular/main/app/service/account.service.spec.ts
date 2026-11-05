import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { AccountService } from './account.service';
import { environment } from 'main/environments/environment';

describe('AccountService write methods', () => {
  let service: AccountService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    service = TestBed.inject(AccountService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('creates accounts with POST', () => {
    const account = { displayName: 'New account' };
    service.addAccount(account).subscribe(result => expect(result).toEqual(account));
    const request = http.expectOne(`${environment.accountUrl}/account/`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(account);
    request.flush(account);
  });

  it('updates accounts with PUT', () => {
    const account = { id: 22, displayName: 'Updated account' };
    service.updateAccount(account).subscribe(result => expect(result).toEqual(account));
    const request = http.expectOne(`${environment.accountUrl}/account/`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(account);
    request.flush(account);
  });

  it('propagates update failures', () => {
    spyOn(console, 'error');
    service.updateAccount({ id: 22, displayName: 'Updated' }).subscribe({
      next: () => fail('Expected an HTTP error'),
      error: error => expect(error.status).toBe(404)
    });
    http.expectOne(`${environment.accountUrl}/account/`).flush('Not found', { status: 404, statusText: 'Not Found' });
  });
});
