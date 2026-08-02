/*
 *  This file is part of Player Analytics (Plan).
 *
 *  Plan is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Plan is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with Plan. If not, see <https://www.gnu.org/licenses/>.
 */
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
