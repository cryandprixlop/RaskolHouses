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

/** Покупка / возврат / налог / таблички (настенные). */
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

    /* ================= Покупка ================= */

    public boolean tryBuy(Player p, House house) {
        if (house.isOwned()) {
            p.sendMessage("§c" + house.title() + " уже продан владельцу " + house.ownerName + ".");
            return false;
        }
        int limit = plugin.getConfig().getInt("houses-per-player", 1);
        if (limit > 0 && store.ownedCount(p.getUniqueId().toString()) >= limit) {
            p.sendMessage("§cЛимит: у тебя уже " + limit + " дом(ов). Сначала верни текущий: /rhouse release");
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
        return vault.withdrawAccount(account, world, amount)
                || vault.withdrawAccount(account, amount);
    }

    /* ================= Таблички (настенные) ================= */

    /** Авто-поиск стены внутри дома: табличка вешается на неё лицом в комнату. */
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

    /**
     * Привязать табличку к координатам.
     * facing = куда смотрит лицевая сторона (null = авто: ищем стену рядом).
     * Если блока-таблички нет — создаёт OAK_WALL_SIGN (или OAK_SIGN, если стены нет).
     */
    public boolean attachSign(House h, Location loc, BlockFace facing) {
        Block b = loc.getBlock();
        if (!(b.getState() instanceof Sign)) {
            if (b.getType() != Material.AIR) return false;
            BlockFace f = (facing != null && isHorizontal(facing)) ? facing : detectWall(b);
            if (f != null) {
                b.setType(Material.OAK_WALL_SIGN);
                if (b.getBlockData() instanceof WallSign ws) {
                    ws.setFacing(f);
                    b.setBlockData(ws);
                }
            } else if (b.getRelative(BlockFace.DOWN).getType().isSolid()) {
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
        sign.getSide(Side.FRONT).setLine(2, h.isOwned()
                ? "§eНалог: §7" + h.paidDays + " дн."
                : "§7[свободен]");
        sign.getSide(Side.FRONT).setLine(3, h.isOwned() ? "" : "§eПКМ: купить");
    }

    /** Направление стены рядом с блоком -> facing таблички (от стены). */
    private BlockFace detectWall(Block b) {
        for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            if (b.getRelative(f).getType().isSolid()) return f.getOppositeFace();
        }
        return null;
    }

    private boolean isHorizontal(BlockFace f) {
        return f == BlockFace.NORTH || f == BlockFace.SOUTH || f == BlockFace.EAST || f == BlockFace.WEST;
    }

    private BlockFace faceOf(int dx, int dz) {
        if (dx == 1) return BlockFace.EAST;
        if (dx == -1) return BlockFace.WEST;
        if (dz == 1) return BlockFace.SOUTH;
        return BlockFace.NORTH;
    }
}
