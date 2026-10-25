package ru.raskol.houses.model;

public final class House {

    public String town;
    public int number;
    public double price;
    public String owner = "";
    public String ownerName = "";
    public Region structure;
    public Region interior;
    public Region yard;

    // Табличка
    public String signWorld = "";
    public int signX, signY, signZ;

    // Налог: оплачено дней вперёд
    public int paidDays;

    // Продажа игроку
    public boolean forSale = false;
    public double salePrice = 0.0;

    public String id() { return town + "-" + number; }

    public boolean isOwned() { return owner != null && !owner.isEmpty(); }

    public String title() { return "Дом №" + number + " (" + town + ")"; }

    public org.bukkit.Location center() {
        return new org.bukkit.Location(
                org.bukkit.Bukkit.getWorld(structure.world),
                (structure.x1 + structure.x2) / 2.0,
                (structure.y1 + structure.y2) / 2.0,
                (structure.z1 + structure.z2) / 2.0);
    }
}
