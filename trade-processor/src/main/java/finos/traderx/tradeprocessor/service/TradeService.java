package finos.traderx.tradeprocessor.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Date;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import finos.traderx.tradeprocessor.model.*;
import finos.traderx.tradeprocessor.repository.*;

@Service
public class TradeService {
	Logger log= LoggerFactory.getLogger(TradeService.class);
	@Autowired
	TradeRepository tradeRepository;

	@Autowired
	PositionRepository positionRepository;

	
    @Autowired
    private Clock settlementClock;

    @Autowired
    private ApplicationEventPublisher events;
    
	@Transactional
	public TradeBookingResult processTrade(TradeOrder order) {
		log.info("Trade order received : "+order);
        Trade t=new Trade();
        t.setAccountId(order.getAccountId());

		log.info("Setting a random TradeID");
		t.setId(UUID.randomUUID().toString());


        Date now = Date.from(settlementClock.instant());
        t.setCreated(now);
        t.setUpdated(now);
        t.setSettlementDate(SettlementCalendar.settlementDate(now.toInstant().atZone(settlementClock.getZone()).toLocalDate()));
        t.setSecurity(order.getSecurity());
        t.setSide(order.getSide());
        t.setQuantity(order.getQuantity());
		t.setState(TradeState.Processing);
		Position position=positionRepository.findByAccountIdAndSecurity(order.getAccountId(), order.getSecurity());
		log.info("Position for "+order.getAccountId()+" "+order.getSecurity()+" is "+position);
		if(position==null) {
			log.info("Creating new position for "+order.getAccountId()+" "+order.getSecurity());
			position=new Position();
			position.setAccountId(order.getAccountId());
			position.setSecurity(order.getSecurity());
			position.setQuantity(0);
		}
		int newQuantity=((order.getSide()==TradeSide.Buy)?1:-1)*t.getQuantity();
		position.setQuantity(position.getQuantity()+newQuantity);
		log.info("Trade {}",t);
		position.setUpdated(now);
		tradeRepository.save(t);
		positionRepository.save(position);
		

		TradeBookingResult result=new TradeBookingResult(t, position);
		log.info("Trade Processing complete : "+result);
		events.publishEvent(result);
		
		return result;	
	}

    @Transactional
    public void settleDueTrades() {
        LocalDate today = LocalDate.now(settlementClock);
        for (Trade trade : tradeRepository.findByStateAndSettlementDateLessThanEqual(TradeState.Processing, today)) {
            trade.setState(TradeState.Settled);
            trade.setUpdated(Date.from(settlementClock.instant()));
            tradeRepository.save(trade);
            events.publishEvent(new TradeBookingResult(trade, null));
        }
    }

}
