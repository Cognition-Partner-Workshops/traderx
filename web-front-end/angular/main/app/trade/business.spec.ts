import { fakeAsync, tick } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';
import { TradeTicketComponent } from './trade-ticket/trade-ticket.component';
import { TradeComponent } from './trade.component';
import { TradeBlotterComponent } from './trade-blotter/trade-blotter.component';
import { PositionBlotterComponent } from './position-blotter/position-blotter.component';
import { EditAccountComponent } from '../accounts/edit/edit.component';
import { AssignUserToAccountComponent } from '../accounts/user/assign-user.component';
import { AccountComponent } from '../accounts/account.component';

describe('Trade ticket business behaviour', () => {
  let ticket: TradeTicketComponent;
  beforeEach(() => {
    ticket = new TradeTicketComponent();
    ticket.account = { id: 7, displayName: 'Savings' };
    ticket.stocks = [{ ticker: 'IBM' } as any];
    ticket.ngOnInit();
  });
  it('builds the selected account ticket and emits the selected ticker, quantity and side', () => {
    expect(ticket.ticket).toEqual({ accountId: 7, quantity: 0, side: 'Buy', security: '' });
    expect(ticket.filteredStocks).toEqual(ticket.stocks);
    const create = spyOn(ticket.create, 'emit');
    ticket.onSelect({ value: 'IBM company', item: { ticker: 'IBM' } } as any);
    ticket.ticket.quantity = 25;
    ticket.ticket.side = 'Sell';
    ticket.onCreate();
    expect(create).toHaveBeenCalledOnceWith({ accountId: 7, quantity: 25, side: 'Sell', security: 'IBM' });
  });
  it('blocks missing security and zero quantity and clears security on empty blur', () => {
    const create = spyOn(ticket.create, 'emit');
    ticket.ticket.quantity = 10;
    ticket.onCreate();
    ticket.ticket.security = 'IBM';
    ticket.ticket.quantity = 0;
    ticket.onCreate();
    expect(create).not.toHaveBeenCalled();
    ticket.onBlur();
    expect(ticket.ticket.security).toBe('');
    ticket.ticket.security = 'IBM';
    ticket.selectedCompany = 'IBM company';
    ticket.onBlur();
    expect(ticket.ticket.security).toBe('IBM');
  });
  it('cancel emits without creating a trade', () => {
    const cancel = spyOn(ticket.cancel, 'emit');
    const create = spyOn(ticket.create, 'emit');
    ticket.onCancel();
    expect(cancel).toHaveBeenCalledTimes(1);
    expect(create).not.toHaveBeenCalled();
  });
  it('parent submits the ticket through the symbol service and closes the modal', () => {
    const service = { createTicket: jasmine.createSpy().and.returnValue(of({ id: 'T1' })) };
    const parent = new TradeComponent({} as any, service as any, {} as any);
    const hide = jasmine.createSpy('hide');
    parent.modalRef = { hide } as any;
    ticket.create.subscribe(value => parent.createTradeTicket(value));
    ticket.ticket.security = 'IBM';
    ticket.ticket.quantity = 5;
    ticket.onCreate();
    expect(service.createTicket).toHaveBeenCalledOnceWith(ticket.ticket);
    expect(parent.createTicketResponse).toEqual({ id: 'T1' });
    expect(hide).toHaveBeenCalledTimes(1);
  });
  xit('rejects negative quantity before emitting a trade (currently accepts negative quantities)', () => {});
  xit('initial account selection uses an available account (parent selects index 5 even for shorter lists)', () => {});
});

describe('Blotter lifecycle and transactions', () => {
  ['trades', 'positions'].forEach(resource => {
    let component: any;
    let load: Subject<any[]>;
    let service: any;
    let feed: any;
    let api: any;
    let unsubscribe: jasmine.Spy;
    let listener: (data: any) => void;
    const row = { id: 'T1', security: 'IBM', quantity: 5, state: 'New', accountid: 7 };
    beforeEach(() => {
      load = new Subject();
      service = { getTrades: jasmine.createSpy().and.returnValue(load), getPositions: jasmine.createSpy().and.returnValue(load) };
      unsubscribe = jasmine.createSpy('unsubscribe');
      feed = { subscribe: jasmine.createSpy().and.callFake((_topic: string, fn: any) => { listener = fn; return unsubscribe; }) };
      api = { getRowNode: jasmine.createSpy(), applyTransaction: jasmine.createSpy(), sizeColumnsToFit: jasmine.createSpy() };
      component = resource === 'trades' ? new TradeBlotterComponent(feed, service) : new PositionBlotterComponent(service, feed);
      component.onGridReady({ api });
    });
    const change = (id: number) => ({ account: { currentValue: { id }, previousValue: undefined } });
    it(resource + ' loads account rows, ignores unchanged input and unsubscribes on switch/destroy', () => {
      component.ngOnChanges(change(7));
      expect(service[resource === 'trades' ? 'getTrades' : 'getPositions']).toHaveBeenCalledWith(7);
      expect(feed.subscribe).toHaveBeenCalledWith('/accounts/7/' + resource, jasmine.any(Function));
      load.next([row]);
      expect(component[resource]).toEqual([row]);
      expect(component.isPending).toBeFalse();
      const account = { id: 7 };
      component.ngOnChanges({ account: { currentValue: account, previousValue: account } });
      expect(feed.subscribe).toHaveBeenCalledTimes(1);
      component.ngOnChanges(change(8));
      expect(unsubscribe).toHaveBeenCalledTimes(1);
      expect(feed.subscribe).toHaveBeenCalledWith('/accounts/8/' + resource, jasmine.any(Function));
      component.ngOnDestroy();
      expect(unsubscribe).toHaveBeenCalledTimes(2);
    });
    it(resource + ' queues live inserts until initial rows load then inserts at the top', () => {
      component.ngOnChanges(change(7));
      listener(row);
      expect(api.applyTransaction).not.toHaveBeenCalled();
      load.next([]);
      expect(api.applyTransaction).toHaveBeenCalledWith({ add: [jasmine.objectContaining(rowWithoutExtra())], addIndex: 0 });
      expect(component.isPending).toBeFalse();
      function rowWithoutExtra() { return resource === 'trades' ? row : { security: 'IBM', quantity: 5, accountid: 7 }; }
    });
    it(resource + ' merges updates into the located row without losing existing fields', () => {
      const stored = { ...row, preserved: 'keep' };
      api.getRowNode.and.returnValue({ data: stored });
      component.ngOnChanges(change(7));
      load.next([row]);
      listener({ ...row, quantity: 9, state: 'Settled' });
      expect(api.applyTransaction).toHaveBeenCalledWith({ update: [jasmine.objectContaining({
        preserved: 'keep', ...(resource === 'trades' ? { state: 'Settled' } : { quantity: 9 })
      })] });
      expect(component.getRowId({ data: row })).toBe(resource === 'trades' ? 'Trade-T1' : 'Position-IBM');
    });
    xit(resource + ' looks up updates using the same prefixed id as getRowId (currently uses bare id/security)', () => {});
  });
  it('position load failure releases pending state', () => {
    const component = new PositionBlotterComponent({ getPositions: () => throwError(() => new Error('offline')) } as any,
      { subscribe: () => () => {} } as any);
    component.ngOnChanges({ account: { currentValue: { id: 7 } } } as any);
    expect(component.isPending).toBeFalse();
  });
  xit('trade load failure releases pending state and reports an error (currently no error handler)', () => {});
});

describe('Account editing and user assignment', () => {
  let service: any;
  beforeEach(() => { service = { addAccount: jasmine.createSpy().and.returnValue(of({})), addAccountUser: jasmine.createSpy().and.returnValue(of({})) }; });
  it('blocks blank names and creates or updates the entered account then resets', () => {
    const editor = new EditAccountComponent(service);
    const update = spyOn(editor.update, 'emit');
    editor.displayName = ' ';
    editor.add();
    expect(service.addAccount).not.toHaveBeenCalled();
    editor.displayName = 'New savings';
    editor.add();
    expect(service.addAccount).toHaveBeenCalledWith({ displayName: 'New savings' });
    expect(update).toHaveBeenCalledWith(jasmine.objectContaining({ displayName: 'New savings' }));
    editor.account = { id: 7, displayName: 'Old' };
    expect(editor.displayName).toBe('Old');
    editor.displayName = 'Renamed';
    editor.add();
    expect(service.addAccount).toHaveBeenCalledWith({ id: 7, displayName: 'Renamed' });
    expect(editor.accountResponse.msg).toContain('updated');
    expect(editor.displayName).toBeUndefined();
  });
  it('failed account save retains entered name and does not emit update', () => {
    service.addAccount.and.returnValue(throwError(() => new Error('offline')));
    const editor = new EditAccountComponent(service);
    const update = spyOn(editor.update, 'emit');
    editor.displayName = 'Savings';
    editor.add();
    expect(editor.displayName).toBe('Savings');
    expect(editor.accountResponse.msg).toBe('There is some error!');
    expect(update).not.toHaveBeenCalled();
  });
  [undefined, '', 'ab', 'Ada'].forEach(query => it('searches only above two characters: ' + query, () => {
    const users = { getUsers: jasmine.createSpy().and.returnValue(of([{ logonId: 'ada' }])) };
    const assign = new AssignUserToAccountComponent(users as any, service);
    assign.search = query;
    assign.ngOnInit();
    const results = jasmine.createSpy('results');
    assign.users$.subscribe(results);
    if (query === 'Ada') {
      expect(users.getUsers).toHaveBeenCalledOnceWith('Ada');
      expect(results).toHaveBeenCalledWith([{ logonId: 'ada' }]);
    } else {
      expect(users.getUsers).not.toHaveBeenCalled();
      expect(results).toHaveBeenCalledWith([]);
    }
  }));
  it('search errors become empty suggestions', () => {
    const assign = new AssignUserToAccountComponent({ getUsers: () => throwError(() => new Error('offline')) } as any, service);
    assign.search = 'Ada';
    assign.ngOnInit();
    const next = jasmine.createSpy('next');
    assign.users$.subscribe(next);
    expect(next).toHaveBeenCalledOnceWith([]);
  });
  it('assigns selected username to account, emits update and keeps account sticky', () => {
    const assign = new AssignUserToAccountComponent({} as any, service);
    const account = { id: 7, displayName: 'Savings' };
    assign.account = account;
    assign.onSelect({ item: { logonId: 'ada' } } as any);
    const update = spyOn(assign.update, 'emit');
    assign.add();
    expect(service.addAccountUser).toHaveBeenCalledOnceWith({ username: 'ada', accountId: 7 });
    expect(update).toHaveBeenCalledOnceWith(account);
    expect(assign.user).toBeUndefined();
    expect(assign.account).toEqual(account);
    expect(assign.addUserResponse.success).toBeTrue();
  });
  it('assignment failure keeps selection available for retry without emitting', () => {
    service.addAccountUser.and.returnValue(throwError(() => new Error('offline')));
    const assign = new AssignUserToAccountComponent({} as any, service);
    assign.account = { id: 7, displayName: 'Savings' };
    assign.user = { logonId: 'ada' } as any;
    const update = spyOn(assign.update, 'emit');
    assign.add();
    expect(assign.addUserResponse.error).toBeTrue();
    expect(assign.user?.logonId).toBe('ada');
    expect(update).not.toHaveBeenCalled();
  });
  it('account selection refreshes and filters assigned users', fakeAsync(() => {
    const accounts = [{ id: 7, displayName: 'Savings' }];
    const component = new AccountComponent({
      getAccounts: () => of(accounts),
      getAccountUsers: () => of([{ accountId: 7, username: 'ada' }, { accountId: 8, username: 'bob' }])
    } as any);
    component.ngOnInit();
    const accountRows = jasmine.createSpy('accounts');
    const userRows = jasmine.createSpy('users');
    const a = component.accounts$.subscribe(accountRows);
    const u = component.users$.subscribe(userRows);
    component.onGridReady({ api: { getSelectedRows: () => accounts } } as any);
    component.onSelectionChanged();
    tick(200);
    expect(component.selectedAccount).toEqual(accounts[0]);
    expect(accountRows).toHaveBeenCalledWith(accounts);
    expect(userRows).toHaveBeenCalledWith([{ accountId: 7, username: 'ada' }]);
    a.unsubscribe();
    u.unsubscribe();
  }));
  xit('empty grid selection should clear account without dereferencing selectedRows[0].id', () => {});
});
