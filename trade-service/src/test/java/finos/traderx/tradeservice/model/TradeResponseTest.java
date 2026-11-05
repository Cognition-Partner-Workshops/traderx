package finos.traderx.tradeservice.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TradeResponseTest {

    @Test
    void successFactoryMarksAcceptedOrderWithItsIdAndNoError() {
        TradeResponse response = TradeResponse.success("4e7d4734");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getId()).isEqualTo("4e7d4734");
        assertThat(response.getErrorMessage()).isNull();
    }

    @Test
    void errorFactoryMarksRejectedOrderWithReasonAndNoId() {
        TradeResponse response = TradeResponse.error("Unknown account 99");

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).isEqualTo("Unknown account 99");
        assertThat(response.getId()).isNull();
    }
}
