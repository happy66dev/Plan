package com.djrapitops.plan.storage.database.transactions.events;

import com.djrapitops.plan.identification.ServerUUID;
import com.djrapitops.plan.storage.database.queries.DataStoreQueries;
import com.djrapitops.plan.storage.database.transactions.Transaction;

import java.util.UUID;

/** Stores one block event row. */
public class StoreBlockEventTransaction extends Transaction {
    private final UUID playerUUID;
    private final ServerUUID serverUUID;
    private final long date;
    private final String type;

    public StoreBlockEventTransaction(UUID playerUUID, ServerUUID serverUUID, long date, String type) {
        this.playerUUID = playerUUID;
        this.serverUUID = serverUUID;
        this.date = date;
        this.type = type;
    }

    @Override protected void performOperations() {
        execute(DataStoreQueries.storeBlockEvent(playerUUID, serverUUID, date, type));
    }
}
