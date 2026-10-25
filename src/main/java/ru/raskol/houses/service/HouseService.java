package ru.raskol.houses.service;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import ru.raskol.houses.RaskolHouses;
import ru.raskol.houses.hook.VaultHook;
import ru.raskol.houses.model.House;
import ru.raskol.houses.store.HouseStore;

/** Покупка у города / продажа игроку / возврат / налог / таблички. */
public final class HouseService {

    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private final RaskolHouses plugin;
    private final HouseStore store;
    private final VaultHook vault;

    public HouseService(RaskolHouses plugin, HouseStore store, VaultHook vault) {
        this.plugin = plugin;
        this.store = store;
        this.vault = vault;
    }

    /* ================= Покупка (у города ИЛИ у игрока) ================= */

    public boolean tryBuy(Player p, House house) {
        if (house.isOwned()) {
            if (!house.forSale) {
                p.sendMessage("§c" + house.title() + " уже продан владельцу " + house.ownerName + ".");
                return false;
            }
            return tryBuyFromPlayer(p, house);
        }

        int limit = plugin.getConfig().getInt("houses-per-player", 1);
        if (limit > 0 && store.ownedCount(p.getUniqueId().toString()) >= limit) {
            p.sendMessage("§cЛимит: у тебя уже " + limit + " дом(ов). Сначала верни или продай текущий.");
            return false;
        }
        if (!vault.isAvailable()) { p.sendMessage("§cЭкономика недоступна."); return false; }
        if (!vault.has(p, house.price)) {
            p.sendMessage("§cНедостаточно средств: нужно §b" + vault.format(house.price)
                    + "§c, у тебя §b" + vault.format(vault.balance(p)) + "§c.");
            return false;
        }
        if (!vault.withdraw(p, house.price)) {
            p.sendMessage("§cНе удалось списать деньги.");
            return false;
        }

        String via = depositToTown(house, house.price,
                "RaskolHouses: покупка дома №" + house.number);
        if (via == null) {
            vault.depositPlayer(p, house.price);
            p.sendMessage("§cКазна города недоступна — операция отменена, деньги возвращены.");
            return false;
        }

        house.owner = p.getUniqueId().toString();
        house.ownerName = p.getName();
        house.paidDays = plugin.getConfig().getInt("tax.initial-paid-days", 7);
        store.save();
        updateSign(house);

        p.sendMessage("§a✔ Куплен " + house.title() + " за §b" + vault.format(house.price)
                + "§a. Деньги ушли: §e" + via + "§a.");
        p.sendMessage("§aВключено дней налога: §e" + house.paidDays
                + "§a. Продлить: §e/rhouse pay <дни>");
        plugin.getLogger().info("[Houses] bought: " + house.id() + " by " + p.getName()
                + " for " + house.price + " via " + via);
        return true;
    }

    /* ================= Продажа игрок -> игрок ================= */

    private boolean tryBuyFromPlayer(Player p, House house) {
        if (house.owner.equals(p.getUniqueId().toString())) {
            p.sendMessage("§cЭто твой собственный дом — сними его с продажи: /rhouse sell cancel");
            return false;
        }
        int limit = plugin.getConfig().getInt("houses-per-player", 1);
        if (limit > 0 && store.ownedCount(p.getUniqueId().toString()) >= limit) {
            p.sendMessage("§cЛимит: у тебя уже " + limit + " дом(ов). Сначала верни или продай текущий.");
            return false;
        }
        if (!vault.isAvailable()) { p.sendMessage("§cЭкономика недоступна."); return false; }
        double price = house.salePrice;
        if (!vault.has(p, price)) {
            p.sendMessage("§cНедостаточно средств: нужно §b" + vault.format(price)
                    + "§c, у тебя §b" + vault.format(vault.balance(p)) + "§c.");
            return false;
        }
        if (!vault.withdraw(p, price)) {
            p.sendMessage("§cНе удалось списать деньги.");
            return false;
        }

        // Комиссия королевства
        String nation = plugin.getTownyHook().nationOfTown(house.town);
        double defPct = plugin.getConfig().getDouble("sale.commission-percent", 5.0);
        double percent = (nation != null) ? store.getCommission(nation, defPct) : defPct;
        double commission = price * percent / 100.0;
        String viaNation = null;

        if (nation != null && commission > 0) {
            if (plugin.getTownyHook().depositNationBank(nation, commission,
                    "RaskolHouses: комиссия с продажи дома №" + house.number)) {
                viaNation = "казна королевства " + nation;
            } else {
                String acc = plugin.getConfig().getString("nation-account-prefix", "nation-")
                        + nation.toLowerCase();
                vault.createAccount(acc);
                if (vault.depositAccount(acc, commission)) viaNation = "аккаунт " + acc;
            }
            if (viaNation == null) {
                vault.depositPlayer(p, price);
                p.sendMessage("§cКазна королевства недоступна — операция отменена, деньги возвращены.");
                return false;
            }
        }

        double ownerShare = price - commission;
        if (ownerShare > 0) {
            boolean paid = vault.depositAccount(house.ownerName, ownerShare);
            if (!paid) {
                // откат: вернуть комиссию и деньги покупателя
                if (nation != null && commission > 0) {
                    plugin.getTownyHook().withdrawNationBank(nation, commission,
                            "RaskolHouses: откат комиссии продажи №" + house.number);
                }
                vault.depositPlayer(p, price);
                p.sendMessage("§cНе удалось выплатить владельцу — операция отменена, деньги возвращены.");
                return false;
            }
        }

        String oldOwner = house.ownerName;
        house.owner = p.getUniqueId().toString();
        house.ownerName = p.getName();
        house.forSale = false;
        house.salePrice = 0.0;
        store.save();
        updateSign(house);

        p.sendMessage("§a✔ Куплен " + house.title() + " у §e" + oldOwner + " §aза §b"
                + vault.format(price) + "§a.");
        if (nation != null && commission > 0) {
            p.sendMessage("§7Комиссия королевства " + percent + "% (§b"
                    + vault.format(commission) + "§7) ушла: " + viaNation + ".");
        }
        Player seller = Bukkit.getPlayerExact(oldOwner);
        if (seller != null) {
            seller.sendMessage("§a✔ Твой " + house.title() + " продан игроку §e" + p.getName()
                    + " §aза §b" + vault.format(price) + "§a. На счёт поступило §b"
                    + vault.format(ownerShare) + " §7(минус комиссия " + percent + "%).");
        }
        plugin.getLogger().info("[Houses] player-sale: " + house.id() + " " + oldOwner
                + " -> " + p.getName() + " for " + price + " (commission " + percent
                + "% -> " + (viaNation == null ? "n/a" : viaNation) + ")");
        return true;
    }

    /* ================= Выставить / снять с продажи ================= */

    public void listForSale(Player p, House house, double price) {
        if (!house.isOwned() || !house.owner.equals(p.getUniqueId().toString())) {
            p.sendMessage("§cТы не владелец этого дома.");
            return;
        }
        if (price <= 0) { p.sendMessage("§cЦена должна быть больше нуля."); return; }
        house.forSale = true;
        house.salePrice = price;
        store.save();
        updateSign(house);

        String nation = plugin.getTownyHook().nationOfTown(house.town);
        double defPct = plugin.getConfig().getDouble("sale.commission-percent", 5.0);
        double percent = (nation != null) ? store.getCommission(nation, defPct) : defPct;
        p.sendMessage("§a" + house.title() + " выставлен на продажу за §b" + vault.format(price) + "§a.");
        p.sendMessage("§7Комиссия королевства при продаже: " + percent + "% (ты получишь §b"
                + vault.format(price * (100.0 - percent) / 100.0) + "§7).");
        p.sendMessage("§7Снять с продажи: §e/rhouse sell cancel");
    }

    public void cancelSale(Player p, House house) {
        if (!house.isOwned() || !house.owner.equals(p.getUniqueId().toString())) {
            p.sendMessage("§cТы не владелец этого дома.");
            return;
        }
        house.forSale = false;
        house.salePrice = 0.0;
        store.save();
        updateSign(house);
        p.sendMessage("§a" + house.title() + " снят с продажи.");
    }

    /* ================= Продажа государству ================= */

    public void release(Player p, House house) {
        double refundPct = plugin.getConfig().getDouble("refund-percent", 0.0);
        double refund = house.price * refundPct / 100.0;

        if (refund > 0) {
            boolean taken = withdrawFromTown(house.town, house.structure.world, refund,
                    "RaskolHouses: возврат за дом №" + house.number);
            if (taken && vault.depositPlayer(p, refund)) {
                p.sendMessage("§aВозврат §b" + vault.format(refund) + " (" + (int) refundPct
                        + "%) выплачен из казны города §e" + house.town + "§a.");
            } else {
                p.sendMessage("§eКазна города пуста или недоступна — возврат не выплачен.");
                plugin.getLogger().warning("[Houses] возврат не выплачен: town=" + house.town
                        + " refund=" + refund);
            }
        }

        house.owner = "";
        house.ownerName = "";
        house.paidDays = 0;
        house.forSale = false;
        house.salePrice = 0.0;
        store.save();
        updateSign(house);
        p.sendMessage("§c" + house.title() + " §7возвращён городу.");
        plugin.getLogger().info("[Houses] released: " + house.id() + " by " + p.getName());
    }

    /* ================= Налог: продление ================= */

    public void payTax(Player p, House house, int days) {
        if (!house.isOwned() || !house.owner.equals(p.getUniqueId().toString())) {
            p.sendMessage("§cТы не владелец этого дома.");
            return;
        }
        if (days <= 0) { p.sendMessage("§cКоличество дней должно быть > 0."); return; }

        double perDay = plugin.getConfig().getDouble("tax.per-day", 200.0);
        int max = plugin.getConfig().getInt("tax.max-prepaid-days", 20);

        int target = Math.min(max, house.paidDays + days);
        int added = target - house.paidDays;
        if (added <= 0) {
            p.sendMessage("§cПредоплата уже на максимуме (" + max + " дн.).");
            return;
        }
        double cost = added * perDay;
        if (!vault.has(p, cost)) {
            p.sendMessage("§cНедостаточно средств: нужно §b" + vault.format(cost) + "§c.");
            return;
        }
        if (!vault.withdraw(p, cost)) {
            p.sendMessage("§cНе удалось списать деньги.");
            return;
        }
        String via = depositToTown(house, cost,
                "RaskolHouses: налог " + added + " дн., дом №" + house.number);
        if (via == null) {
            vault.depositPlayer(p, cost);
            p.sendMessage("§cКазна недоступна — операция отменена, деньги возвращены.");
            return;
        }

        house.paidDays = target;
        store.save();
        updateSign(house);
        p.sendMessage("§a✔ Продлено на §e" + added + " дн.§a (оплачено §b" + vault.format(cost)
                + "§a → " + via + "). Оплачено всего: §e" + house.paidDays + "/" + max + " дн.");
        if (added < days) {
            p.sendMessage("§eПотолок " + max + " дн.: из запрошенных " + days
                    + " добавлено только " + added + ".");
        }
    }

    /* ================= Казна ================= */

    private String depositToTown(House house, double amount, String reason) {
        if (plugin.getConfig().getBoolean("use-towny-bank", true)
                && plugin.getTownyHook().depositTownBank(house.town, amount, reason)) {
            return "банк Towny города " + house.town;
        }
        String account = plugin.getConfig().getString("town-account-prefix", "town-")
                + house.town.toLowerCase();
        vault.createAccount(account);
        if (vault.depositAccount(account, house.structure.world, amount)
                || vault.depositAccount(account, amount)) {
            return "аккаунт " + account;
        }
        return null;
    }

    private boolean withdrawFromTown(String town, String world, double amount, String reason) {
        if (plugin.getConfig().getBoolean("use-towny-bank", true)
                && plugin.getTownyHook().withdrawTownBank(town, amount, reason)) {
            return true;
        }
        String account = plugin.getConfig().getString("town-account-prefix", "town-")
                + town.toLowerCase();
        return vault.withdrawAccount(account, amount);
    }

    /* ================= Таблички (настенные) ================= */

    public void placeSign(House h, Player creator) {
        try {
            org.bukkit.World w = Bukkit.getWorld(h.structure.world);
            if (w == null) return;
            int cx = (h.structure.x1 + h.structure.x2) / 2;
            int cz = (h.structure.z1 + h.structure.z2) / 2;
            int maxX = (h.structure.x2 - h.structure.x1) / 2 + 1;
            int maxZ = (h.structure.z2 - h.structure.z1) / 2 + 1;

            for (int dy : new int[]{2, 1, 3}) {
                int y = h.structure.y1 + dy;
                if (y > h.structure.y2) continue;
                for (int[] dir : DIRS) {
                    int limit = (dir[0] != 0) ? maxX : maxZ;
                    for (int step = 1; step <= limit; step++) {
                        int bx = cx + dir[0] * step;
                        int bz = cz + dir[1] * step;
                        Block wall = w.getBlockAt(bx, y, bz);
                        if (wall.getType().isSolid()) {
                            Block front = w.getBlockAt(bx - dir[0], y, bz - dir[1]);
                            if (front.getType() == Material.AIR) {
                                BlockFace facing = faceOf(-dir[0], -dir[1]);
                                if (attachSign(h, front.getLocation(), facing)) {
                                    creator.sendMessage("§aТабличка установлена на стене внутри дома.");
                                }
                                return;
                            }
                            break;
                        }
                    }
                }
            }
            creator.sendMessage("§eНе нашёл стену для таблички — поставь вручную: /rhouse setsign");
        } catch (Throwable t) {
            creator.sendMessage("§eТабличка не установлена: " + t.getMessage());
        }
    }

    public boolean attachSign(House h, Location loc, org.bukkit.block.BlockFace facing) {
        Block b = loc.getBlock();
        if (!(b.getState() instanceof Sign)) {
            if (b.getType() != Material.AIR) return false;
            org.bukkit.block.BlockFace f = (facing != null && isHorizontal(facing)) ? facing : detectWall(b);
            if (f != null) {
                b.setType(Material.OAK_WALL_SIGN);
                if (b.getBlockData() instanceof WallSign ws) {
                    ws.setFacing(f);
                    b.setBlockData(ws);
                }
            } else if (b.getRelative(org.bukkit.block.BlockFace.DOWN).getType().isSolid()) {
                b.setType(Material.OAK_SIGN);
            } else {
                return false;
            }
        }
        if (b.getState() instanceof Sign sign) {
            writeSign(sign, h);
            sign.update();
        }
        h.signWorld = loc.getWorld().getName();
        h.signX = loc.getBlockX();
        h.signY = loc.getBlockY();
        h.signZ = loc.getBlockZ();
        store.save();
        return true;
    }

    public boolean attachSign(House h, Location loc) {
        return attachSign(h, loc, null);
    }

    public void updateSign(House house) {
        if (house.signWorld == null || house.signWorld.isEmpty()) return;
        org.bukkit.World w = Bukkit.getWorld(house.signWorld);
        if (w == null) return;
        Block block = w.getBlockAt(house.signX, house.signY, house.signZ);
        if (block.getState() instanceof Sign sign) {
            writeSign(sign, house);
            sign.update();
        }
    }

    private void writeSign(Sign sign, House h) {
        sign.getSide(Side.FRONT).setLine(0, "§l§6Дом №" + h.number);
        sign.getSide(Side.FRONT).setLine(1, h.isOwned()
                ? "§a" + h.ownerName
                : "Цена: §b" + (int) h.price);
        if (h.forSale) {
            sign.getSide(Side.FRONT).setLine(2, "§dПродаётся: §b" + (int) h.salePrice);
            sign.getSide(Side.FRONT).setLine(3, "§eПКМ: купить у игрока");
        } else if (h.isOwned()) {
            sign.getSide(Side.FRONT).setLine(2, "§eНалог: §7" + h.paidDays + " дн.");
            sign.getSide(Side.FRONT).setLine(3, "");
        } else {
            sign.getSide(Side.FRONT).setLine(2, "§7[свободен]");
            sign.getSide(Side.FRONT).setLine(3, "§eПКМ: купить");
        }
    }

    private org.bukkit.block.BlockFace detectWall(Block b) {
        for (org.bukkit.block.BlockFace f : new org.bukkit.block.BlockFace[]{
                org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.SOUTH,
                org.bukkit.block.BlockFace.EAST, org.bukkit.block.BlockFace.WEST}) {
            if (b.getRelative(f).getType().isSolid()) return f.getOppositeFace();
        }
        return null;
    }

    private boolean isHorizontal(org.bukkit.block.BlockFace f) {
        return f == org.bukkit.block.BlockFace.NORTH || f == org.bukkit.block.BlockFace.SOUTH
                || f == org.bukkit.block.BlockFace.EAST || f == org.bukkit.block.BlockFace.WEST;
    }

    private org.bukkit.block.BlockFace faceOf(int dx, int dz) {
        if (dx == 1) return org.bukkit.block.BlockFace.EAST;
        if (dx == -1) return org.bukkit.block.BlockFace.WEST;
        if (dz == 1) return org.bukkit.block.BlockFace.SOUTH;
        return org.bukkit.block.BlockFace.NORTH;
    }
}
