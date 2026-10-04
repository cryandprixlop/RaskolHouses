package ru.raskol.houses.hook;

import org.bukkit.entity.Player;
import ru.raskol.houses.RaskolHouses;
import ru.raskol.houses.model.Region;

/** Чтение выделения WorldEdit через рефлексию (без жёсткой зависимости). */
public final class SelectionHook {

    private final RaskolHouses plugin;

    public SelectionHook(RaskolHouses plugin) {
        this.plugin = plugin;
    }

    public boolean isAvailable() {
        return plugin.getServer().getPluginManager().getPlugin("WorldEdit") != null;
    }

    /** Выделение игрока; null если WorldEdit нет или выделения нет. */
    public Region getSelection(Player player) {
        try {
            Object wep = plugin.getServer().getPluginManager().getPlugin("WorldEdit");
            if (wep == null) return null;

            Object session = wep.getClass().getMethod("getSession", Player.class).invoke(wep, player);
            if (session == null) return null;

            Object weWorld = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter")
                    .getMethod("adapt", org.bukkit.World.class)
                    .invoke(null, player.getWorld());

            Object region = session.getClass()
                    .getMethod("getSelection", Class.forName("com.sk89q.worldedit.world.World"))
                    .invoke(session, weWorld);
            if (region == null) return null;

            Object min = region.getClass().getMethod("getMinimumPoint").invoke(region);
            Object max = region.getClass().getMethod("getMaximumPoint").invoke(region);

            int x1 = (Integer) min.getClass().getMethod("getX").invoke(min);
            int y1 = (Integer) min.getClass().getMethod("getY").invoke(min);
            int z1 = (Integer) min.getClass().getMethod("getZ").invoke(min);
            int x2 = (Integer) max.getClass().getMethod("getX").invoke(max);
            int y2 = (Integer) max.getClass().getMethod("getY").invoke(max);
            int z2 = (Integer) max.getClass().getMethod("getZ").invoke(max);

            return new Region(player.getWorld().getName(), x1, y1, z1, x2, y2, z2);
        } catch (Throwable t) {
            return null;
        }
    }
}
