package com.djrapitops.plan.storage.database.sql.tables;

import com.djrapitops.plan.storage.database.DBType;
import com.djrapitops.plan.storage.database.sql.building.CreateTableBuilder;
import com.djrapitops.plan.storage.database.sql.building.Sql;

import static com.djrapitops.plan.storage.database.sql.building.Sql.INSERT_INTO;

/** Stores one row for every Bukkit block event. */
public final class BlockEventsTable {
    public static final String TABLE_NAME = "plan_block_events";
    public static final String PLAYER_UUID = "player_uuid";
    public static final String SERVER_UUID = "server_uuid";
    public static final String DATE = "date";
    public static final String TYPE = "type";
    public static final String INSERT_STATEMENT = INSERT_INTO + TABLE_NAME + " (" + PLAYER_UUID + "," + SERVER_UUID + "," + DATE + "," + TYPE + ") VALUES (?,?,?,?)";

    private BlockEventsTable() { }

    public static String createTableSQL(DBType dbType) {
        return CreateTableBuilder.create(TABLE_NAME, dbType)
                .column(PLAYER_UUID, Sql.varchar(36)).notNull()
                .column(SERVER_UUID, Sql.varchar(36)).notNull()
                .column(DATE, Sql.LONG).notNull()
                .column(TYPE, Sql.varchar(20)).notNull()
                .toString();
    }
}
