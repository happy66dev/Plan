package com.djrapitops.plan.storage.database.transactions.patches;

import com.djrapitops.plan.storage.database.sql.tables.BlockEventsTable;

/** Creates the block events table for existing databases. */
public class BlockEventsTablePatch extends Patch {
    @Override public boolean hasBeenApplied() { return hasTable(BlockEventsTable.TABLE_NAME); }
    @Override protected void applyPatch() { execute(BlockEventsTable.createTableSQL(dbType)); }
}
