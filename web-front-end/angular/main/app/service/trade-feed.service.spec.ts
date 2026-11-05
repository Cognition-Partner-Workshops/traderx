import { TradeFeedService } from './trade-feed.service';

describe('Trade feed routing', () => {
  it('subscribes, filters system and other-topic messages and detaches the exact listener', () => {
    const socket = jasmine.createSpyObj('socket', ['on', 'off', 'emit']);
    const service = Object.create(TradeFeedService.prototype) as TradeFeedService;
    (service as any).socket = socket;
    const receive = jasmine.createSpy('receive');
    const unsubscribe = service.subscribe('/accounts/7/trades', receive);
    expect(socket.emit).toHaveBeenCalledOnceWith('subscribe', '/accounts/7/trades');
    const listener = socket.on.calls.mostRecent().args[1];
    listener({ from: 'System', topic: '/accounts/7/trades', payload: { id: 1 } });
    listener({ from: 'Trader', topic: '/accounts/8/trades', payload: { id: 2 } });
    expect(receive).not.toHaveBeenCalled();
    listener({ from: 'Trader', topic: '/accounts/7/trades', payload: { id: 3 } });
    expect(receive).toHaveBeenCalledOnceWith({ id: 3 });
    unsubscribe();
    expect(socket.emit).toHaveBeenCalledWith('unsubscribe', '/accounts/7/trades');
    expect(socket.off).toHaveBeenCalledOnceWith('publish', listener);
  });
});
