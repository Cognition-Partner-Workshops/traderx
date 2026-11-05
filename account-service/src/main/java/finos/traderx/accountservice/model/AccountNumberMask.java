package finos.traderx.accountservice.model;

/**
 * Customer-facing account numbers show only their last four digits.
 */
public final class AccountNumberMask {

    private AccountNumberMask() {
    }

    public static String mask(Integer accountId) {
        if (accountId == null) {
            return null;
        }
        String digits = String.valueOf(accountId);
        return "****" + digits.substring(Math.max(0, digits.length() - 4));
    }
}
