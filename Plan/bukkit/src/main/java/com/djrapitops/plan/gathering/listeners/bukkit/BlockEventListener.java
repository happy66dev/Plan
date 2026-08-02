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

    private void store(java.util.UUID playerUUID, String type) {
        dbSystem.getDatabase().executeTransaction(new StoreBlockEventTransaction(playerUUID, serverInfo.getServerUUID(), System.currentTimeMillis(), type));
    }
}
