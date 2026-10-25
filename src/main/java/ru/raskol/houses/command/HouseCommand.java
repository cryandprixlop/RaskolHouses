package ru.raskol.houses.command;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import ru.raskol.houses.RaskolHouses;
import ru.raskol.houses.hook.SelectionHook;
import ru.raskol.houses.hook.TownyHook;
import ru.raskol.houses.model.House;
import ru.raskol.houses.model.Region;
import ru.raskol.houses.service.HouseService;
import ru.raskol.houses.store.HouseStore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class HouseCommand implements CommandExecutor, TabCompleter {

    private final RaskolHouses plugin;
    private final HouseStore store;
    private final SelectionHook selection;
    private final TownyHook towny;
    private final HouseService service;

    public HouseCommand(RaskolHouses plugin, HouseStore store,
                        SelectionHook selection, TownyHook towny, HouseService service) {
        this.plugin = plugin;
        this.store = store;
        this.selection = selection;
        this.towny = towny;
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) { sendHelp(sender); return true; }
        switch (args[0].toLowerCase()) {
            case "create"     -> handleCreate(sender, args);
            case "interior"   -> handleZone(sender, "interior");
            case "yard"       -> handleZone(sender, "yard");
            case "setprice"   -> handleSetPrice(sender, args);
            case "delete"     -> handleDelete(sender, args);
            case "buy"        -> handleBuy(sender);
            case "sell"       -> handleSell(sender, args);
            case "commission" -> handleCommission(sender, args);
            case "release"    -> handleRelease(sender);
            case "home"       -> handleHome(sender);
            case "pay"        -> handlePay(sender, args);
            case "taxinfo"    -> handleTaxInfo(sender);
            case "setsign"    -> handleSetSign(sender);
            case "info"       -> handleInfo(sender);
            case "list"       -> handleList(sender, args);
            default           -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6=== RaskolHouses ===");
        sender.sendMessage("§e/rhouse create <номер> <цена> §7— создать дом");
        sender.sendMessage("§e/rhouse interior §7— зона интерьера");
        sender.sendMessage("§e/rhouse yard §7— зона двора");
        sender.sendMessage("§e/rhouse setsign §7— настенная табличка на грань, куда смотришь");
        sender.sendMessage("§e/rhouse setprice <номер> <цена> §7— сменить цену города");
        sender.sendMessage("§e/rhouse delete <номер> §7— удалить дом");
        sender.sendMessage("§e/rhouse buy §7— купить дом (у города или у игрока)");
        sender.sendMessage("§e/rhouse sell <цена> §7— выставить свой дом на продажу");
        sender.sendMessage("§e/rhouse sell cancel §7— снять дом с продажи");
        sender.sendMessage("§e/rhouse commission <процент> §7— (король) комиссия нации с продаж");
        sender.sendMessage("§e/rhouse pay <дни> §7— продлить налог (до 20 дн.)");
        sender.sendMessage("§e/rhouse taxinfo §7— статус налога");
        sender.sendMessage("§e/rhouse release §7— продать дом государству");
        sender.sendMessage("§e/rhouse home §7— телепорт в свой дом");
        sender.sendMessage("§e/rhouse info §7— карточка дома");
        sender.sendMessage("§e/rhouse list [город|owned] §7— список домов");
    }

    private boolean admin(CommandSender sender) {
        if (sender.hasPermission("rhouse.admin")) return true;
        sender.sendMessage("§cНет прав.");
        return false;
    }

    private Player player(CommandSender sender) {
        if (sender instanceof Player p) return p;
        sender.sendMessage("§cТолько игроки.");
        return null;
    }

    /* ================= create / zones / setsign / setprice / delete ================= */

    private void handleCreate(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        Player p = player(sender);
        if (p == null) return;
        if (args.length < 3) { p.sendMessage("§c/rhouse create <номер> <цена>"); return; }
        int number;
        double price;
        try {
            number = Integer.parseInt(args[1]);
            price = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            p.sendMessage("§cНомер — целое число, цена — число.");
            return;
        }

        Region sel = selection.getSelection(p);
        if (sel == null) { p.sendMessage("§cСначала сделай выделение WorldEdit."); return; }

        Location center = new Location(p.getWorld(),
                (sel.x1 + sel.x2) / 2.0, (sel.y1 + sel.y2) / 2.0, (sel.z1 + sel.z2) / 2.0);
        String town = towny.townAt(center);
        if (town == null) { p.sendMessage("§cДома можно создавать только в городах."); return; }
        if (store.byTownNumber(town, number) != null) {
            p.sendMessage("§cВ городе " + town + " уже есть дом №" + number + ".");
            return;
        }

        House h = new House();
        h.town = town;
        h.number = number;
        h.price = price;
        h.structure = sel;
        store.add(h);

        if (plugin.getConfig().getBoolean("sign-on-create", true)) {
            service.placeSign(h, p);
        }

        p.sendMessage("§aСоздан " + h.title() + " §aза " + price + ".");
        p.sendMessage("§7Выдели комнаты: /rhouse interior");
        plugin.getLogger().info("[Houses] created: " + h.id() + " price=" + price + " by " + p.getName());
    }

    private void handleZone(CommandSender sender, String zone) {
        if (!admin(sender)) return;
        Player p = player(sender);
        if (p == null) return;
        House house = store.houseAt(p.getLocation());
        if (house == null) { p.sendMessage("§cВстань внутрь дома."); return; }
        Region sel = selection.getSelection(p);
        if (sel == null) { p.sendMessage("§cСначала сделай выделение."); return; }
        if (!house.structure.contains(sel)) { p.sendMessage("§cВыделение должно быть внутри дома."); return; }
        if (zone.equals("interior")) house.interior = sel;
        else house.yard = sel;
        store.save();
        p.sendMessage("§aЗона " + (zone.equals("interior") ? "интерьера" : "двора")
                + " установлена для " + house.title() + ".");
    }

    private void handleSetSign(CommandSender sender) {
        if (!admin(sender)) return;
        Player p = player(sender);
        if (p == null) return;

        RayTraceResult rt = p.rayTraceBlocks(5.0);
        if (rt == null || rt.getHitBlock() == null) {
            p.sendMessage("§cПосмотри на стену (до 5 блоков).");
            return;
        }
        Block target = rt.getHitBlock();
        BlockFace face = rt.getHitBlockFace();

        House house = store.houseAt(target.getLocation());
        if (house == null) house = store.houseAt(p.getLocation());
        if (house == null) { p.sendMessage("§cРядом нет дома из реестра."); return; }

        if (target.getState() instanceof Sign) {
            if (service.attachSign(house, target.getLocation(), null)) {
                p.sendMessage("§aТабличка привязана к " + house.title() + ".");
            } else {
                p.sendMessage("§cНе удалось привязать табличку.");
            }
            return;
        }

        if (face == null || face == BlockFace.UP || face == BlockFace.DOWN) {
            p.sendMessage("§cПосмотри на вертикальную стену, не на пол/потолок.");
            return;
        }

        Location signLoc = target.getRelative(face).getLocation();
        if (service.attachSign(house, signLoc, face)) {
            p.sendMessage("§aНастенная табличка установлена и привязана к " + house.title() + ".");
        } else {
            p.sendMessage("§cНе удалось поставить табличку: место занято.");
        }
    }

    private void handleSetPrice(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        Player p = player(sender);
        if (p == null) return;
        if (args.length < 3) { p.sendMessage("§c/rhouse setprice <номер> <цена>"); return; }
        String town = towny.townAt(p.getLocation());
        if (town == null) { p.sendMessage("§cТы вне города."); return; }
        House house = store.byTownNumber(town, Integer.parseInt(args[1]));
        if (house == null) { p.sendMessage("§cНет такого дома."); return; }
        house.price = Double.parseDouble(args[2]);
        store.save();
        service.updateSign(house);
        p.sendMessage("§aЦена " + house.title() + " теперь " + house.price + ".");
    }

    private void handleDelete(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        Player p = player(sender);
        if (p == null) return;
        if (args.length < 2) { p.sendMessage("§c/rhouse delete <номер>"); return; }
        String town = towny.townAt(p.getLocation());
        if (town == null) { p.sendMessage("§cТы вне города."); return; }
        House house = store.byTownNumber(town, Integer.parseInt(args[1]));
        if (house == null) { p.sendMessage("§cНет такого дома."); return; }
        store.remove(house.id());
        p.sendMessage("§cДом " + house.title() + " удалён из реестра.");
    }

    /* ================= buy / sell / commission ================= */

    private void handleBuy(CommandSender sender) {
        Player p = player(sender);
        if (p == null) return;
        House house = store.houseAt(p.getLocation());
        if (house == null) { p.sendMessage("§7Ты не внутри дома."); return; }
        service.tryBuy(p, house);
    }

    private void handleSell(CommandSender sender, String[] args) {
        Player p = player(sender);
        if (p == null) return;
        House house = store.houseAt(p.getLocation());
        if (house == null) { p.sendMessage("§7Ты не внутри дома."); return; }
        if (args.length >= 2 && args[1].equalsIgnoreCase("cancel")) {
            service.cancelSale(p, house);
            return;
        }
        if (args.length < 2) { p.sendMessage("§c/rhouse sell <цена>  или  /rhouse sell cancel"); return; }
        double price;
        try { price = Double.parseDouble(args[1]); }
        catch (NumberFormatException e) { p.sendMessage("§cЦена — число."); return; }
        service.listForSale(p, house, price);
    }

    private void handleCommission(CommandSender sender, String[] args) {
        Player p = player(sender);
        if (p == null) return;
        if (args.length < 2) { p.sendMessage("§c/rhouse commission <процент>"); return; }
        String nation = towny.residentNationName(p);
        if (nation == null && !p.hasPermission("rhouse.admin")) {
            p.sendMessage("§cТы не состоишь в королевстве.");
            return;
        }
        if (!p.hasPermission("rhouse.admin") && !towny.isKing(p)) {
            p.sendMessage("§cТолько король может менять комиссию королевства.");
            return;
        }
        double percent;
        try { percent = Double.parseDouble(args[1]); }
        catch (NumberFormatException e) { p.sendMessage("§cПроцент — число."); return; }
        if (percent < 0 || percent > 100) { p.sendMessage("§cПроцент должен быть 0..100."); return; }
        String target = (nation != null) ? nation : "DEFAULT";
        if (nation != null) store.setCommission(nation, percent);
        p.sendMessage("§aКомиссия с продаж домов для королевства " + target
                + " теперь §e" + percent + "%§a.");
        plugin.getLogger().info("[Houses] commission set: " + target + " = " + percent
                + "% by " + p.getName());
    }

    /* ================= release / home / pay / taxinfo ================= */

    private void handleRelease(CommandSender sender) {
        Player p = player(sender);
        if (p == null) return;
        House house = store.houseAt(p.getLocation());
        if (house == null) { p.sendMessage("§7Ты не внутри дома."); return; }
        if (!house.isOwned() || !house.owner.equals(p.getUniqueId().toString())) {
            p.sendMessage("§cТы не владелец этого дома.");
            return;
        }
        service.release(p, house);
    }

    private void handleHome(CommandSender sender) {
        Player p = player(sender);
        if (p == null) return;
        List<House> owned = store.ownedBy(p.getUniqueId().toString());
        if (owned.isEmpty()) { p.sendMessage("§cУ тебя нет дома. Купи: /rhouse buy"); return; }
        House h = owned.get(0);
        Location safe = new Location(
                org.bukkit.Bukkit.getWorld(h.structure.world),
                (h.structure.x1 + h.structure.x2) / 2.0,
                h.structure.y1 + 1,
                (h.structure.z1 + h.structure.z2) / 2.0);
        if (safe.getWorld() == null) { p.sendMessage("§cМир дома не найден."); return; }
        p.teleport(safe);
        p.sendMessage("§aТелепорт в " + h.title() + ".");
    }

    private void handlePay(CommandSender sender, String[] args) {
        Player p = player(sender);
        if (p == null) return;
        House house = store.houseAt(p.getLocation());
        if (house == null) { p.sendMessage("§7Ты не внутри дома."); return; }
        int days;
        try {
            days = Integer.parseInt(args.length >= 2 ? args[1] : "1");
        } catch (NumberFormatException e) {
            p.sendMessage("§c/rhouse pay <дни>");
            return;
        }
        service.payTax(p, house, days);
    }

    private void handleTaxInfo(CommandSender sender) {
        Player p = player(sender);
        if (p == null) return;
        House house = store.houseAt(p.getLocation());
        if (house == null) {
            List<House> owned = store.ownedBy(p.getUniqueId().toString());
            if (owned.isEmpty()) { p.sendMessage("§cУ тебя нет дома."); return; }
            house = owned.get(0);
        }
        double perDay = plugin.getConfig().getDouble("tax.per-day", 200.0);
        int max = plugin.getConfig().getInt("tax.max-prepaid-days", 20);
        int grace = plugin.getConfig().getInt("tax.grace-days", 3);
        p.sendMessage("§6=== Налог: " + house.title() + " ===");
        p.sendMessage("§eСтавка: §b" + (int) perDay + " §eмонет/день");
        p.sendMessage("§eОплачено: §e" + house.paidDays + "/" + max + " дн."
                + (house.paidDays < 0 ? " §c(просрочка " + (-house.paidDays) + "/" + grace + ")" : ""));
        if (house.forSale) {
            p.sendMessage("§dДом выставлен на продажу за §b" + (int) house.salePrice);
        }
        p.sendMessage("§eПродлить: §7/rhouse pay <дни> §8(цена = дни × " + (int) perDay + ")");
    }

    /* ================= info / list ================= */

    private void handleInfo(CommandSender sender) {
        Player p = player(sender);
        if (p == null) return;
        House house = store.houseAt(p.getLocation());
        if (house == null) { p.sendMessage("§7Ты не внутри дома."); return; }
        p.sendMessage("§6=== " + house.title() + " ===");
        p.sendMessage("§eЦена города: §7" + house.price);
        p.sendMessage("§eСтатус: " + (house.isOwned()
                ? "§aвладелец: " + house.ownerName + " §7(налог: " + house.paidDays + " дн.)"
                : "§bсвободен"));
        if (house.forSale) {
            p.sendMessage("§dПродаётся за §b" + house.salePrice
                    + " §7— купить: /rhouse buy или ПКМ по табличке");
        }
    }

    private void handleList(CommandSender sender, String[] args) {
        String filter = args.length >= 2
                ? String.join(" ", Arrays.asList(args).subList(1, args.length)).toLowerCase() : null;
        List<House> list = new ArrayList<>();
        for (House h : store.all()) {
            if (filter == null) list.add(h);
            else if (filter.equals("owned") && sender instanceof Player p
                    && h.owner.equals(p.getUniqueId().toString())) list.add(h);
            else if (filter.equals("sale") && h.forSale) list.add(h);
            else if (h.town.toLowerCase().equals(filter)) list.add(h);
        }
        if (list.isEmpty()) { sender.sendMessage("§7Домов не найдено."); return; }
        sender.sendMessage("§6=== Домов: " + list.size() + " ===");
        for (House h : list) {
            sender.sendMessage("§e" + h.title() + " §7— " + h.price
                    + (h.isOwned() ? " §aвладелец: " + h.ownerName : " §bсвободен")
                    + (h.forSale ? " §dПРОДАЁТСЯ: " + h.salePrice : ""));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("create", "interior", "yard", "setsign", "setprice",
                    "delete", "buy", "sell", "commission", "release", "home", "pay",
                    "taxinfo", "info", "list"), args[0]);
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("sell")) return filter(List.of("cancel"), args[1]);
            if (args[0].equalsIgnoreCase("list")) return filter(Arrays.asList("owned", "sale"), args[1]);
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> list, String prefix) {
        String lower = prefix.toLowerCase();
        List<String> out = new ArrayList<>();
        for (String s : list) {
            if (s.toLowerCase().startsWith(lower)) out.add(s);
        }
        return out;
    }
}
