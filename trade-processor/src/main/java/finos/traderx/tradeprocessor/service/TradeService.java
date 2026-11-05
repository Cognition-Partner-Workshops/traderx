package finos.traderx.tradeprocessor.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import finos.traderx.messaging.PubSubException;
import finos.traderx.messaging.Publisher;
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
	TradeStateChangeRepository tradeStateChangeRepository;

	private Clock clock = Clock.systemUTC();

	void setClock(Clock clock) {
		this.clock = clock;
	}

	/** Every state change is appended to the trade's history with the UTC instant it happened. */
	private void transition(Trade t, TradeState state) {
		Instant now = clock.instant();
		t.setState(state);
		t.setUpdated(Date.from(now));
		tradeStateChangeRepository.save(new TradeStateChange(t.getId(), state, now));
	}

	
    @Autowired 
    private Publisher<Trade> tradePublisher;
    
    @Autowired
    private Publisher<Position> positionPublisher;
    
	public TradeBookingResult processTrade(TradeOrder order) {
		log.info("Trade order received : "+order);
        Trade t=new Trade();
        t.setAccountId(order.getAccountId());

		log.info("Setting a random TradeID");
		t.setId(UUID.randomUUID().toString());


        t.setCreated(Date.from(clock.instant()));
        t.setUpdated(t.getCreated());
        t.setSecurity(order.getSecurity());
        t.setSide(order.getSide());
        t.setQuantity(order.getQuantity());
		t.setState(TradeState.New);
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
		tradeRepository.save(t);
		positionRepository.save(position);
		tradeStateChangeRepository.save(new TradeStateChange(t.getId(), TradeState.New, t.getCreated().toInstant()));
		// Simulate the handling of this trade...
		// Now mark as processing
		transition(t, TradeState.Processing);
		// Now mark as settled
		transition(t, TradeState.Settled);
		tradeRepository.save(t);
		

		TradeBookingResult result=new TradeBookingResult(t, position);
		log.info("Trade Processing complete : "+result);
		try{
			log.info("Publishing : "+result);
			tradePublisher.publish("/accounts/"+order.getAccountId()+"/trades", result.getTrade());
			positionPublisher.publish("/accounts/"+order.getAccountId()+"/positions", result.getPosition());
		} catch (PubSubException exc){
			log.error("Error publishing trade "+order,exc);
		}
		
		return result;	
	}

}
