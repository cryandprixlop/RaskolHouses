package ru.raskol.houses.listener;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import ru.raskol.houses.RaskolHouses;
import ru.raskol.houses.model.House;
import ru.raskol.houses.service.HouseService;
import ru.raskol.houses.store.HouseStore;

import java.util.HashMap;
import java.util.Map;

/**
 * ПКМ по табличке дома:
 * - дом свободен → подтверждение покупки (повторный ПКМ в течение 5 сек);
 * - дом продан → карточка владельца.
 */
public final class SignListener implements Listener {

    private static final long CONFIRM_MS = 5000;

    private final RaskolHouses plugin;
    private final HouseStore store;
    private final HouseService service;
    private final Map<String, Long> confirm = new HashMap<>();

    public SignListener(RaskolHouses plugin, HouseStore store, HouseService service) {
        this.plugin = plugin;
        this.store = store;
        this.service = service;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSignClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || !(block.getState() instanceof Sign)) return;

        House house = houseBySign(block.getLocation());
        if (house == null) return;

        Player p = event.getPlayer();
        event.setCancelled(true); // не даём редактировать табличку

        if (house.isOwned()) {
            p.sendMessage("§6" + house.title() + " §7— владелец: §a" + house.ownerName
                    + "§7, цена была: §b" + (int) house.price);
            return;
        }

        String key = p.getUniqueId() + ":" + house.id();
        long now = System.currentTimeMillis();
        Long prev = confirm.get(key);
        if (prev == null || now - prev > CONFIRM_MS) {
            confirm.put(key, now);
            p.sendMessage("§e" + house.title() + " стоит §b" + (int) house.price
                    + "§e. ПКМ по табличке ещё раз в течение 5 сек — подтвердить покупку.");
            return;
        }
        confirm.remove(key);
        service.tryBuy(p, house);
    }

    private House houseBySign(Location loc) {
        if (loc.getWorld() == null) return null;
        String world = loc.getWorld().getName();
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
        for (House h : store.all()) {
            if (world.equals(h.signWorld) && h.signX == x && h.signY == y && h.signZ == z) {
                return h;
            }
        }
        return null;
    }
}
