package finos.traderx.tradeservice;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import finos.traderx.messaging.Publisher;
import finos.traderx.messaging.socketio.SocketIOJSONPublisher;
import finos.traderx.tradeservice.model.TradeOrder;
import finos.traderx.tradeservice.security.ServiceTokenIssuer;

@Configuration
public class PubSubConfig {
    @Value("${trade.feed.address}")
    private String tradeFeedAddress;

    @Bean 
    public Publisher<TradeOrder> tradePublisher(ServiceTokenIssuer serviceTokenIssuer) {
        SocketIOJSONPublisher<TradeOrder> publisher = new SocketIOJSONPublisher<TradeOrder>(){};
        publisher.setTokenSupplier(serviceTokenIssuer::issueToken);
        publisher.setTopic("/trades");
        publisher.setSocketAddress(tradeFeedAddress);
        return publisher;
    }

}
