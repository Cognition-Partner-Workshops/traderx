import { ComponentFixture, TestBed, tick, fakeAsync, flush } from '@angular/core/testing';
import { AgGridModule } from 'ag-grid-angular';
import { TradeBlotterComponent } from './trade-blotter.component';
import { PositionService } from 'main/app/service/position.service';
import { MockTradeService, MockTradeFeedService, accounts as dummyAccounts, trades } from 'main/app/test-utils/mocks.service';
import { TradeFeedService } from 'main/app/service/trade-feed.service';
import { State } from 'main/app/model/trade.model';

describe('TradeBlotterComponent', () => {
    let component: TradeBlotterComponent;
    let fixture: ComponentFixture<TradeBlotterComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [TradeBlotterComponent],
            imports: [
                AgGridModule
            ],
            providers: [
                {
                    provide: PositionService,
                    useClass: MockTradeService
                },
                {
                    provide: TradeFeedService,
                    useClass: MockTradeFeedService
                }
            ]
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(TradeBlotterComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should show given trades columns in the grid', async () => {
        const columns = fixture.nativeElement.querySelectorAll('.ag-header-cell');
        const rows = fixture.nativeElement.querySelectorAll('.ag-row');
        expect(columns.length).toEqual(5);
        expect(rows.length).toEqual(0);
    });

    it('should call getTrades on changes and set trades', fakeAsync(() => {
        expect(component.account).not.toBeDefined();
        component.ngOnChanges({ account: { currentValue: dummyAccounts[0] } } as any);
        expect(component.trades.length).toEqual(2);
        fixture.detectChanges();
        tick(100)
        const rows = fixture.nativeElement.querySelectorAll('.ag-center-cols-container .ag-row');
        expect(rows.length).toEqual(2);
        expect(component.pendingTrades.length).toEqual(0);
        flush();
    }));

    it('should call getTrades and subscribe to trade feed service for given account', async () => {
        spyOn((component as any).tradeService, 'getTrades').and.callThrough();
        spyOn((component as any).tradeFeed, 'subscribe').and.callThrough();
        const testAccount = dummyAccounts[0];
        component.ngOnChanges({ account: { currentValue: testAccount } } as any);
        expect((component as any).tradeService.getTrades).toHaveBeenCalledWith(testAccount.id);
        expect((component as any).tradeFeed.subscribe).toHaveBeenCalled();

    });

    it('getRowId should return the prefixed trade id', () => {
        expect(component.getRowId({ data: trades[0] } as any)).toEqual(`Trade-${trades[0].id}`);
    });

    ['2026-11-03', '2026-11-06', '2026-11-09'].forEach(date => {
        it(`adds a Processing trade with stored settlement date ${date}`, () => {
            const applyTransaction = jasmine.createSpy();
            component.gridApi = { getRowNode: () => undefined, applyTransaction } as any;
            component.isPending = false;
            const trade = { ...trades[0], state: State.Processing, settlementDate: date };
            (component as any).updateTrades(trade);
            expect(applyTransaction.calls.mostRecent().args[0].add[0]).toEqual(jasmine.objectContaining({
                id: trade.id, state: State.Processing, settlementDate: date
            }));
        });
    });

    it('updates the same row to Settled without losing its settlement date', () => {
        const original = { ...trades[0], state: State.Processing, settlementDate: '2026-11-09' };
        const getRowNode = jasmine.createSpy().and.returnValue({ data: original });
        const applyTransaction = jasmine.createSpy();
        component.gridApi = { getRowNode, applyTransaction } as any;
        component.isPending = false;
        (component as any).updateTrades({ ...original, state: State.Settled });
        expect(getRowNode).toHaveBeenCalledWith(`Trade-${original.id}`);
        const transaction = applyTransaction.calls.mostRecent().args[0];
        expect(transaction.add).toBeUndefined();
        expect(transaction.update[0]).toEqual(jasmine.objectContaining({ state: State.Settled, settlementDate: '2026-11-09' }));
    });

    it('buffers the settlement date and state while loading history', () => {
        const trade = { ...trades[0], state: State.Processing, settlementDate: '2026-11-09' };
        (component as any).updateTrades(trade);
        expect(component.pendingTrades).toEqual([trade]);
    });

    it('renders the stored date next to state from historical trades', fakeAsync(() => {
        component.trades = [{ ...trades[0], state: State.Processing, settlementDate: '2026-11-09' }];
        fixture.detectChanges();
        tick(100);
        const text = fixture.nativeElement.textContent;
        expect(text).toContain('SETTLEMENT DATE');
        expect(text).toContain('2026-11-09');
        expect(text).toContain('Processing');
        const fields = component.columnDefs.map(column => column.field);
        expect(fields.indexOf('settlementDate')).toEqual(fields.indexOf('state') + 1);
        flush();
    }));

});
