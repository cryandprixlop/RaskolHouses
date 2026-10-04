package ru.raskol.houses.model;

import org.bukkit.Location;

/** Кубоид-регион с нормализованными границами. */
public final class Region {

    public String world;
    public int x1, y1, z1, x2, y2, z2;

    public Region() {
    }

    public Region(String world, int ax, int ay, int az, int bx, int by, int bz) {
        this.world = world;
        set(ax, ay, az, bx, by, bz);
    }

    private void set(int ax, int ay, int az, int bx, int by, int bz) {
        x1 = Math.min(ax, bx); y1 = Math.min(ay, by); z1 = Math.min(az, bz);
        x2 = Math.max(ax, bx); y2 = Math.max(ay, by); z2 = Math.max(az, bz);
    }

    public boolean contains(Location loc) {
        if (loc.getWorld() == null || !world.equals(loc.getWorld().getName())) return false;
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
        return x >= x1 && x <= x2 && y >= y1 && y <= y2 && z >= z1 && z <= z2;
    }

    public boolean contains(Region other) {
        if (other == null || !world.equals(other.world)) return false;
        return other.x1 >= x1 && other.x2 <= x2
                && other.y1 >= y1 && other.y2 <= y2
                && other.z1 >= z1 && other.z2 <= z2;
    }
}
