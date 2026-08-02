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
