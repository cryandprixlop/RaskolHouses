package ru.raskol.houses.model;

/** Дом: город + номер, цена, владелец, регионы. */
public final class House {

    public String town;
    public int number;
    public double price;
    public String owner = "";
    public String ownerName = "";
    public Region structure;   // весь дом + фасад + двор: никто не ломает
    public Region interior;    // внутри: владелец может строить
    public Region yard;        // прилегающая территория: владелец может строить

    public String id() {
        return town + "-" + number;
    }

    public boolean isOwned() {
        return owner != null && !owner.isEmpty();
    }

    public String title() {
        return "Дом №" + number + " (" + town + ")";
    }
}
