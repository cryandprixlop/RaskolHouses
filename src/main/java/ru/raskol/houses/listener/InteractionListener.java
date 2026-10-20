package ru.raskol.houses.listener;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import ru.raskol.houses.RaskolHouses;
import ru.raskol.houses.model.House;
import ru.raskol.houses.store.HouseStore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Чужим запрещено взаимодействовать с контейнерами/станциями внутри дома.
 * Двери, калитки, люки — НЕ защищены (вход свободный).
 */
public final class InteractionListener implements Listener {

    private final RaskolHouses plugin;
    private final HouseStore store;
    private final Map<UUID, Long> lastMsg = new HashMap<>();

    public InteractionListener(RaskolHouses plugin, HouseStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        if (!plugin.getConfig().getBoolean("protect-interactions", true)) return;

        House house = store.houseAt(block.getLocation());
        if (house == null) return;

        Player p = event.getPlayer();
        if (p.hasPermission("rhouse.admin")) return;
        if (house.isOwned() && house.owner.equals(p.getUniqueId().toString())) return;

        if (!isProtected(block.getType())) return;

        event.setCancelled(true);
        warn(p, "§c" + house.title() + ": чужое имущество трогать нельзя!");
    }

    private boolean isProtected(Material material) {
        String name = material.name();
        if (name.endsWith("_BED") || name.endsWith("_SHULKER_BOX")) return true;
        List<String> list = plugin.getConfig().getStringList("protected-materials");
        return list.contains(name);
    }

    private void warn(Player player, String message) {
        long now = System.currentTimeMillis();
        Long prev = lastMsg.get(player.getUniqueId());
        if (prev != null && now - prev < 3000) return;
        lastMsg.put(player.getUniqueId(), now);
        player.sendMessage(message);
    }
}
