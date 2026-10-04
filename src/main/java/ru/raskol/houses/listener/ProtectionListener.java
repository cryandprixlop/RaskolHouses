package ru.raskol.houses.listener;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import ru.raskol.houses.RaskolHouses;
import ru.raskol.houses.model.House;
import ru.raskol.houses.store.HouseStore;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Защита домов:
 * - structure (стены/фасад/двор): никто, кроме админа;
 * - interior/yard: только владелец;
 * - взрывы и огонь в домах гасятся.
 */
public final class ProtectionListener implements Listener {

    private final RaskolHouses plugin;
    private final HouseStore store;
    private final Map<UUID, Long> lastMsg = new HashMap<>();

    public ProtectionListener(RaskolHouses plugin, HouseStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (deny(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (deny(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (store.houseAt(event.getBlock().getLocation()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(b -> store.houseAt(b.getLocation()) != null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(b -> store.houseAt(b.getLocation()) != null);
    }

    /** true = действие запрещено. */
    private boolean deny(Player player, Location loc) {
        House house = store.houseAt(loc);
        if (house == null) return false;
        if (player.hasPermission("rhouse.admin")) return false;

        boolean inBuildZone = (house.interior != null && house.interior.contains(loc))
                || (house.yard != null && house.yard.contains(loc));

        if (!inBuildZone) {
            warn(player, "§cЭто конструкция дома — стены и фасад ломать нельзя!");
            return true;
        }
        if (!house.isOwned() || !house.owner.equals(player.getUniqueId().toString())) {
            warn(player, "§c" + house.title() + ": это чужая собственность!");
            return true;
        }
        return false;
    }

    private void warn(Player player, String message) {
        long now = System.currentTimeMillis();
        Long prev = lastMsg.get(player.getUniqueId());
        if (prev != null && now - prev < 3000) return;
        lastMsg.put(player.getUniqueId(), now);
        player.sendMessage(message);
    }
}
