package finos.traderx.tradeservice;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.module.SimpleModule;

import finos.traderx.tradeservice.model.AccountNumberMask;
import finos.traderx.tradeservice.model.TradeOrder;

/**
 * Masks the account number in HTTP responses only; the order published to the
 * trade feed for the trade-processor keeps the full account number.
 */
@Component
public class AccountMaskingModule extends SimpleModule {

    public AccountMaskingModule() {
        setMixInAnnotation(TradeOrder.class, TradeOrderResponseMixin.class);
    }

    abstract static class TradeOrderResponseMixin {
        @JsonSerialize(using = AccountNumberMask.Serializer.class)
        abstract Integer getAccountId();
    }
}
