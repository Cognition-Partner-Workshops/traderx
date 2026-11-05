ALTER TABLE Accounts ADD COLUMN IF NOT EXISTS PublicReference VARCHAR(36) DEFAULT RANDOM_UUID() NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS AccountPublicReference ON Accounts(PublicReference);

CREATE TABLE IF NOT EXISTS TradeStatusHistory (
    ID BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    TradeID VARCHAR(100) NOT NULL REFERENCES Trades(ID),
    State VARCHAR(20) NOT NULL CHECK (State IN ('New', 'Processing', 'Settled', 'Cancelled')),
    ChangedAt TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TRIGGER IF NOT EXISTS TradeStatusHistoryImmutable BEFORE UPDATE, DELETE ON TradeStatusHistory FOR EACH ROW AS '
org.h2.api.Trigger create() {
    return new org.h2.api.Trigger() {
        public void fire(java.sql.Connection connection, Object[] before, Object[] after) throws java.sql.SQLException {
            throw new java.sql.SQLException("Trade status history is append-only");
        }
    };
}
';
