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
package com.djrapitops.plan.gathering.listeners.bukkit;

import com.djrapitops.plan.identification.ServerInfo;
import com.djrapitops.plan.storage.database.DBSystem;
import com.djrapitops.plan.storage.database.transactions.events.StoreBlockEventTransaction;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import javax.inject.Inject;
import java.util.UUID;

/** Records successful Bukkit block break and place events. */
public class BlockEventListener implements Listener {
    private final ServerInfo serverInfo;
    private final DBSystem dbSystem;

    @Inject
    public BlockEventListener(ServerInfo serverInfo, DBSystem dbSystem) {
        this.serverInfo = serverInfo;
        this.dbSystem = dbSystem;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        store(event.getPlayer().getUniqueId(), "block_break");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        store(event.getPlayer().getUniqueId(), "block_place");
    }

    private void store(UUID playerUUID, String type) {
        dbSystem.getDatabase().executeTransaction(new StoreBlockEventTransaction(playerUUID, serverInfo.getServerUUID(), System.currentTimeMillis(), type));
    }
}
