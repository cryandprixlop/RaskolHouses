package ru.raskol.houses.service;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import ru.raskol.houses.RaskolHouses;
import ru.raskol.houses.hook.VaultHook;
import ru.raskol.houses.model.House;
import ru.raskol.houses.store.HouseStore;

/** Вся логика покупки/возврата/табличек — для команд и кликов по табличке. */
public final class HouseService {

    private final RaskolHouses plugin;
    private final HouseStore store;
    private final VaultHook vault;

    public HouseService(RaskolHouses plugin, HouseStore store, VaultHook vault) {
        this.plugin = plugin;
        this.store = store;
        this.vault = vault;
    }

    /** Покупка. true = успешно. */
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
        if (!vault.isAvailable()) {
            p.sendMessage("§cЭкономика недоступна.");
            return false;
        }
        if (!vault.has(p, house.price)) {
            p.sendMessage("§cНедостаточно средств: нужно §b" + vault.format(house.price)
                    + "§c, у тебя §b" + vault.format(vault.balance(p)) + "§c.");
            return false;
        }
        if (!vault.withdraw(p, house.price)) {
            p.sendMessage("§cНе удалось списать деньги.");
            return false;
        }

        String account = plugin.getConfig().getString("town-account-prefix", "town-")
                + house.town.toLowerCase();
        boolean deposited = vault.depositAccount(account, house.structure.world, house.price)
                || vault.depositAccount(account, house.price);
        if (!deposited) {
            vault.depositPlayer(p, house.price);
            p.sendMessage("§cКазна города недоступна — операция отменена, деньги возвращены.");
            plugin.getLogger().warning("[Houses] не удалось пополнить казну " + account);
            return false;
        }

        house.owner = p.getUniqueId().toString();
        house.ownerName = p.getName();
        store.save();
        updateSign(house);

        p.sendMessage("§a✔ Куплен " + house.title() + " за §b" + vault.format(house.price)
                + "§a. Деньги ушли в казну города §e" + house.town + "§a.");
        p.sendMessage("§aТелепорт: §e/rhouse home");
        plugin.getLogger().info("[Houses] bought: " + house.id() + " by " + p.getName()
                + " for " + house.price + " -> " + account);
        return true;
    }

    /** Возврат дома городу. */
    public void release(Player p, House house) {
        double refundPct = plugin.getConfig().getDouble("refund-percent", 0.0);
        double refund = house.price * refundPct / 100.0;
        if (refund > 0 && vault.isAvailable()) {
            vault.depositPlayer(p, refund);
            p.sendMessage("§aВозврат: §b" + vault.format(refund) + "§a.");
        }
        house.owner = "";
        house.ownerName = "";
        store.save();
        updateSign(house);
        p.sendMessage("§c" + house.title() + " §7возвращён городу.");
        plugin.getLogger().info("[Houses] released: " + house.id() + " by " + p.getName());
    }

    /** Табличка при создании дома. */
    public void placeSign(House h, Player creator) {
        try {
            org.bukkit.World w = Bukkit.getWorld(h.structure.world);
            if (w == null) return;
            int x = (h.structure.x1 + h.structure.x2) / 2;
            int z = (h.structure.z1 + h.structure.z2) / 2;
            int y = w.getHighestBlockYAt(x, z);
            Block base = w.getBlockAt(x, y, z);
            Block signBlock = w.getBlockAt(x, y + 1, z);
            if (!base.getType().isSolid() || signBlock.getType() != Material.AIR) return;

            signBlock.setType(Material.OAK_SIGN);
            if (signBlock.getState() instanceof Sign sign) {
                writeSign(sign, h);
                sign.update();
            }
            h.signWorld = w.getName();
            h.signX = x;
            h.signY = y + 1;
            h.signZ = z;
            store.save();
            creator.sendMessage("§aТабличка установлена на входе.");
        } catch (Throwable t) {
            creator.sendMessage("§eТабличка не установлена: " + t.getMessage());
        }
    }

    /** Обновление существующей таблички. */
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
        sign.getSide(Side.FRONT).setLine(1, "Цена: §b" + (int) h.price);
        sign.getSide(Side.FRONT).setLine(2, h.isOwned() ? "§a" + h.ownerName : "§7[свободен]");
        sign.getSide(Side.FRONT).setLine(3, h.isOwned() ? "" : "§eПКМ: купить");
    }
}
