package finos.traderx.tradeprocessor.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import finos.traderx.records.AccountReferenceLookup;

import finos.traderx.messaging.PubSubException;
import finos.traderx.messaging.Publisher;
import finos.traderx.tradeprocessor.model.*;
import finos.traderx.tradeprocessor.repository.*;

@Service
public class TradeService {
	@Autowired(required = false)
	private AccountReferenceLookup accountReferences;
	Logger log= LoggerFactory.getLogger(TradeService.class);
	@Autowired
	TradeRepository tradeRepository;

	@Autowired
	PositionRepository positionRepository;

	@Autowired
	TradeStatusHistoryRepository tradeStatusHistoryRepository;

	
    @Autowired 
    private Publisher<Trade> tradePublisher;
    
    @Autowired
    private Publisher<Position> positionPublisher;
    
	@Transactional
	public TradeBookingResult processTrade(TradeOrder order) {
		log.info("Trade order received : "+order);
        Trade t=new Trade();
        t.setAccountId(order.getAccountId());

		log.info("Setting a random TradeID");
		t.setId(UUID.randomUUID().toString());


        t.setCreated(new Date());
        t.setUpdated(new Date());
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
		saveWithStatusHistory(t);
		positionRepository.save(position);
		// Simulate the handling of this trade...
		// Now mark as processing
		changeState(t, TradeState.Processing);
		// Now mark as settled
		changeState(t, TradeState.Settled);
		

		TradeBookingResult result=new TradeBookingResult(t, position);
		log.info("Trade Processing complete : "+result);
		String reference = accountReferences == null ? order.getAccountId().toString() : accountReferences.reference(order.getAccountId());
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				try {
					log.info("Publishing : "+result);
					tradePublisher.publish("/accounts/"+reference+"/trades", result.getTrade());
					positionPublisher.publish("/accounts/"+reference+"/positions", result.getPosition());
				} catch (PubSubException exc) {
					log.error("Error publishing trade "+order,exc);
				}
			}
		});
		
		return result;	
	}

	private void changeState(Trade t, TradeState state) {
		t.setUpdated(new Date());
		t.setState(state);
		saveWithStatusHistory(t);
	}

	private void saveWithStatusHistory(Trade t) {
		tradeRepository.save(t);
		tradeStatusHistoryRepository.save(new TradeStatusHistory(t.getId(), t.getState(), t.getUpdated().toInstant()));
	}

}
