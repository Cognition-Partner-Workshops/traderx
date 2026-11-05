package finos.traderx.records;

public class AccountReferenceLookup {
    public String reference(Integer id) {
        return AccountReferences.reference(id);
    }
}
