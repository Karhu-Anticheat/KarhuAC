package me.liwk.karhu.command.sub;

import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import me.liwk.karhu.Karhu;
import me.liwk.karhu.check.api.BanX;
import me.liwk.karhu.check.api.Check;
import me.liwk.karhu.check.api.ViolationX;
import me.liwk.karhu.command.CommandAPI;
import me.liwk.karhu.data.KarhuPlayer;
import me.liwk.karhu.manager.ConfigManager;
import me.liwk.karhu.menu.KarhuMenu;
import me.liwk.karhu.menu.PlayerInfoMenu;
import me.liwk.karhu.util.APICaller;
import me.liwk.karhu.util.benchmark.Benchmark;
import me.liwk.karhu.util.benchmark.KarhuBenchmarker;
import me.liwk.karhu.util.bungee.BungeeAPI;
import me.liwk.karhu.util.framework.Command;
import me.liwk.karhu.util.framework.CommandArgs;
import me.liwk.karhu.util.framework.CommandFramework;
import me.liwk.karhu.util.haste.Hastebin;
import me.liwk.karhu.util.mojang.MojangPing;
import me.liwk.karhu.util.pair.AttackSwingPair;
import me.liwk.karhu.util.task.Tasker;
import me.liwk.karhu.util.text.TextUtils;
import net.md_5.bungee.api.chat.*;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import static org.bukkit.Bukkit.getLogger;

public class KarhuCommand extends CommandAPI {

    public KarhuCommand(CommandFramework k) {
        super(k);
    }

    @Command(name = "karhu", permission = "karhu.staff")
    public void onCommand(CommandArgs command) {
        Player player = command.getPlayer();
        CommandSender sender = command.getSender();

        String[] args = command.getArgs();
        String name2 = Karhu.getInstance().getConfigManager().getName();

        if (command.getLabel().equalsIgnoreCase("karhu") || command.getLabel().equalsIgnoreCase(name2)) {
            ConfigManager cfg = Karhu.getInstance().getConfigManager();
            if (args.length >= 1) {
                if (args[0].equalsIgnoreCase("alerts") && command.getSender() instanceof Player) {
                    if (player.hasPermission("karhu.alerts")) {
                        Karhu.getInstance().getAlertsManager().toggleAlerts(player);
                        player.sendMessage(
                                Karhu.getInstance().getAlertsManager().hasAlertsToggled(player.getUniqueId())
                                        ? (ChatColor
                                        .translateAlternateColorCodes('&',
                                                Karhu.getInstance().getConfigManager().getConfig().getString("commands.alerts.enabled")
                                        ))
                                        : (ChatColor
                                        .translateAlternateColorCodes('&', Karhu.getInstance().getConfigManager().getConfig().getString("commands.alerts.disabled")
                                        )));
                    }
                } else if (args[0].equalsIgnoreCase("mitigations") && command.getSender() instanceof Player) {
                        if (player.hasPermission("karhu.mitigations")) {
                            Karhu.getInstance().getAlertsManager().toggleMitigation(player);
                            player.sendMessage(Karhu.getInstance().getAlertsManager().hasMitigationToggled(player) ? "§aMitigation alerts on" : "§cMitigation alerts off");
                        }
                } else if (args[0].equalsIgnoreCase("debug") && command.getSender() instanceof Player && player.hasPermission("karhu.debug")) {
                    if (args.length == 1) {
                        Karhu.getInstance().getAlertsManager().toggleDebug(player);
                        player.sendMessage(Karhu.getInstance().getAlertsManager().hasDebugToggled(player) ? ("§4§lDEBUG-mode §aenabled") : ("§4§lDEBUG-mode §cdisabled"));
                    } else {
                        if(args[1].equalsIgnoreCase("misc")) {
                            Karhu.getInstance().getAlertsManager().toggleMiscDebug(player);
                            player.sendMessage(Karhu.getInstance().getAlertsManager().hasMiscDebugToggled(player) ? ("§4§lK -> MISC DEBUG-mode §aenabled") : ("§4§lK -> MISC DEBUG-mode §cdisabled"));
                        }
                    }
                } else if (args[0].equalsIgnoreCase("gui") && command.getSender() instanceof Player) {
                    if (player.hasPermission("karhu.gui")) {
                        KarhuMenu.openMenu(player);
                    } else {
                        player.sendMessage(ChatColor.RED + "You don't have enough permissions.");
                    }
                } else if (args[0].equalsIgnoreCase("reload")) {
                    if (!(command.getSender() instanceof Player) || player.hasPermission("karhu.reload")) {
                        command.getSender().sendMessage("§7Reloading configs....");

                        Karhu.getInstance().getConfigManager().loadConfig(Karhu.getInstance(), false);
                        Karhu.getInstance().getConfigManager().loadChecks(Karhu.getInstance(), false);

                        command.getSender().sendMessage("§aReload succesful!");
                    } else {
                        player.sendMessage(ChatColor.RED + "You don't have enough permissions.");
                    }
                } else if (args[0].equalsIgnoreCase("info") && command.getSender() instanceof Player) {
                    if (player.hasPermission("karhu.info")) {
                        if (args.length > 1) {
                            Player target = Bukkit.getPlayer(args[1]);

                            if (target == null) {
                                player.sendMessage("§cSorry, i couldn't find that player");
                                return;
                            }

                            PlayerInfoMenu.openMenu(player, target);
                        } else {
                            player.sendMessage("§cSorry, i couldn't find that player");
                        }
                    }
                } else if (args[0].equalsIgnoreCase("status") && command.getSender() instanceof Player) {
                    if (player.hasPermission("karhu.status")) {
                        if (args.length > 1) {
                            Player target = Bukkit.getPlayer(args[1]);

                            if (target == null) {
                                player.sendMessage("§cSorry, i couldn't find that player");
                                return;
                            }

                            KarhuPlayer data = Karhu.getInstance().getDataManager().getPlayerData(target.getUniqueId());

                            if(data == null) {
                                player.sendMessage("§cSorry, i couldn't find that player");
                                return;
                            }

                            boolean cracked = Karhu.getInstance().getConfigManager().isCrackedServer();
                            String highlight = Karhu.getInstance().getConfigManager().getGuiHighlightColor();

                            boolean inBanWave = Karhu.getInstance().getWaveManager()
                                    .getPlayersToBan()
                                    .contains(cracked ? target.getName() : target.getUniqueId().toString());

                            String color = inBanWave ? "§a" : "§c";

                            long time = System.currentTimeMillis() - data.getLastJoinTime();

                            player.sendMessage("");
                            player.sendMessage(highlight + target.getName() + "'s Status:");
                            player.sendMessage("§f- " + highlight + "Ban Wave: " + color + inBanWave);
                            player.sendMessage("§f- " + highlight + "Ping: §f" + data.getTransactionPing());
                            player.sendMessage("§f- " + highlight + "Version: §f" + data.getClientVersion().toString());
                            player.sendMessage("§f- " + highlight + "Client: §f" + data.getCleanBrand());
                            player.sendMessage("§f- " + highlight + "Highest CPS: §f" + data.getHighestCps());
                            player.sendMessage("§f- " + highlight + "Highest Reach: §f" + data.getHighestReach());
                            player.sendMessage("§f- " + highlight + "Session: §b" + (time / 3600000L) + "h" + " " + (time / 60000L) + "m");
                        } else {
                            player.sendMessage("§cSorry, i couldn't find that player");
                        }
                    }
                } else if (args[0].equalsIgnoreCase("users")) {

                    Map<String, Set<String>> playerBrands = new HashMap<>();

                    for (Player p : Bukkit.getOnlinePlayers()) {
                        KarhuPlayer data = Karhu.getInstance().getDataManager().getPlayerData(p.getUniqueId());
                        if (data == null) continue;
                        String brand = data.getBrand();
                        if (!playerBrands.containsKey(brand))
                            playerBrands.put(brand, new HashSet<>());
                        playerBrands.get(brand).add(p.getName());
                    }

                    for (Map.Entry<String, Set<String>> entry : playerBrands.entrySet()) {
                        command.getSender().sendMessage(String.format("§7[" + cfg.getLogsHighlight() + "%s§7] (" + cfg.getLogsHighlight() + "%d§7):" + cfg.getLogsHighlight() + " %s", entry.getKey(), entry.getValue().size(), entry.getValue()));
                    }

                    playerBrands.clear();

                } else if (args[0].equalsIgnoreCase("stats")) {


                    Tasker.taskAsync(() -> {
                        player.sendMessage("");
                        player.sendMessage("§9Timings - Last 20 Ticks");
                        player.sendMessage(cfg.getGuiHighlightColor() + "TPS: §f" + Karhu.getInstance().getTPS());
                        player.sendMessage(cfg.getGuiHighlightColor() + "Players handled: §f" + Karhu.getInstance().getDataManager().getPlayerDataMap().size());
                        player.sendMessage("");
                        player.sendMessage("§9Other");
                        player.sendMessage(cfg.getGuiHighlightColor() + "Online Players: §f" + Bukkit.getOnlinePlayers().size());

                        StringBuilder stringBuilder = new StringBuilder();

                        stringBuilder.append("\n§9Karhu Benchmark");

                        for (Benchmark sortedProfile : KarhuBenchmarker.sortedProfiles()) {
                            stringBuilder.append("\n")
                                    .append(cfg.getGuiHighlightColor())
                                    .append(sortedProfile.profileType())
                                    .append(": §f")
                                    .append(String.format("%.4f", sortedProfile.runningAverage()))
                                    .append("ms")
                                    .append(" (")
                                    .append(sortedProfile.results())
                                    .append(")");
                        }

                        player.sendMessage(stringBuilder.toString());

                        player.sendMessage("");
                    });



                } else if (args[0].equalsIgnoreCase("manualban")) {
                    if (!(command.getSender() instanceof Player) || player.hasPermission("karhu.manualban")) {
                        if (args.length > 1) {
                            Bukkit.getScheduler().runTaskAsynchronously(Karhu.getInstance(), () -> {
                                final UUID uuid = MojangPing.getUUID(args[1]);
                                final KarhuPlayer data = Karhu.getInstance().getDataManager().getPlayerData(uuid);
                                if (Karhu.getInstance().getConfigManager().isCrackedServer()) {
                                    if (data != null) {
                                        if(Karhu.getInstance().getConfigManager().isDisallowFlagsAfterPunish()) {
                                            data.setBanned(true);
                                        }
                                    }

                                    if (Karhu.getInstance().getConfigManager().isPunishBroadcast()) {
                                        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', Karhu.getInstance().getConfigManager().getConfig().getString("Punishments.message").replaceAll("%player%", args[1])));
                                    }
                                } else {
                                    if (uuid != null) {
                                        if (data != null) {
                                            if(Karhu.getInstance().getConfigManager().isDisallowFlagsAfterPunish()) {
                                                data.setBanned(true);
                                            }
                                        }

                                        if (Karhu.getInstance().getConfigManager().isPunishBroadcast()) {
                                            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', Karhu.getInstance().getConfigManager().getConfig().getString("Punishments.message").replaceAll("%player%", args[1])));
                                        }
                                    } else {
                                        command.getSender().sendMessage("§cSorry, i couldn't find that player");
                                    }
                                }

                                List<String> banCMD = Karhu.getInstance().getConfigManager().getBanCommand();

                                if(!Karhu.isAPIAvailable()) {
                                    if (!Karhu.getInstance().getConfigManager().isBungeeCommand()) {
                                        for (String ban : banCMD) {
                                            Tasker.run(() -> Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), ban.replaceAll("%player%", args[1])));
                                        }
                                    } else {
                                        for (String ban : banCMD) {
                                            BungeeAPI.sendCommand(ban.replaceAll("%player%", args[1]));
                                        }
                                    }
                                } else {
                                    if (APICaller.callBan(player, null, null)) {
                                        if (!Karhu.getInstance().getConfigManager().isBungeeCommand()) {
                                            for (String ban : banCMD) {
                                                Tasker.run(() -> Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), ban.replaceAll("%player%", args[1])));
                                            }
                                        } else {
                                            for (String ban : banCMD) {
                                                BungeeAPI.sendCommand(ban.replaceAll("%player%", args[1]));
                                            }
                                        }
                                    }
                                }

                            });
                        } else player.sendMessage("§c/karhu manualban <player>");
                    } else player.sendMessage("§cNo permissions");

                } else if (args[0].equalsIgnoreCase("antivpn")) {
                    if (!(command.getSender() instanceof Player) || player.hasPermission("karhu.antivpn")) {
                        if (args.length > 1) {
                            if (args[1].equalsIgnoreCase("whitelist")) {
                                if (args.length > 2) {

                                    String uuid = findUUID(args[3]);

                                    if (args[2].equalsIgnoreCase("add")) {
                                        if (uuid != null) {
                                            List<String> allowed = Karhu.getInstance().getConfigManager().getAntiVpnBypass();
                                            allowed.add(uuid);

                                            Karhu.getInstance().getConfigManager().getConfig().set("anti-vpn.bypass", allowed);
                                            Karhu.getInstance().getConfigManager().save();
                                            Karhu.getInstance().getConfigManager().loadConfig(Karhu.getInstance(), true);

                                            command.getSender().sendMessage("§aSuccesfully §7added §2" + args[3] + " §7to antivpn whitelist");
                                        } else {
                                            command.getSender().sendMessage("§cSorry, i couldn't find that player");
                                        }
                                    } else if (args[2].equalsIgnoreCase("remove")) {
                                        if (uuid != null) {
                                            List<String> allowed = Karhu.getInstance().getConfigManager().getAntiVpnBypass();
                                            if (allowed.contains(uuid)) {
                                                allowed.remove(uuid);

                                                Karhu.getInstance().getConfigManager().getConfig().set("anti-vpn.bypass", allowed);
                                                Karhu.getInstance().getConfigManager().save();
                                                Karhu.getInstance().getConfigManager().loadConfig(Karhu.getInstance(), true);

                                                command.getSender().sendMessage("§aSuccesfully §7removed §4" + args[3] + " §7from antivpn whitelist");
                                            }
                                        } else {
                                            command.getSender().sendMessage("§cSorry, i couldn't find that player");
                                        }
                                    } else {
                                        command.getSender().sendMessage("§cUsage: /" + Karhu.getInstance().getConfigManager().getName().toLowerCase() + " antivpn whitelist add/remove PLAYER");
                                    }
                                } else {
                                    command.getSender().sendMessage("§cUsage: /" + Karhu.getInstance().getConfigManager().getName().toLowerCase() + " antivpn whitelist add/remove PLAYER");
                                }
                            } else {
                                command.getSender().sendMessage("§cUsage: /" + Karhu.getInstance().getConfigManager().getName().toLowerCase() + " antivpn whitelist add/remove PLAYER");
                            }
                        } else {
                            command.getSender().sendMessage("§cUsage: /" + Karhu.getInstance().getConfigManager().getName().toLowerCase() + " antivpn whitelist add/remove PLAYER");
                        }

                    } else {
                        command.getSender().sendMessage("§cUsage: /" + Karhu.getInstance().getConfigManager().getName().toLowerCase() + " antivpn whitelist add/remove PLAYER");
                    }

                } else if (args[0].equalsIgnoreCase("sessionlogs") || args[0].equalsIgnoreCase("slogs")) {
                    if (args.length == 1) {
                        player.sendMessage(ChatColor.RED + "Use: /karhu sessionlogs <player>");
                        return;
                    }

                    double v = 0;

                    Player target = Bukkit.getPlayer(args[1]);
                    if (target != null) {
                        KarhuPlayer karhuPlayer = Karhu.getInstance().getDataManager().getPlayerData(target.getUniqueId());

                        player.sendMessage("§7§m--------------------------------------");
                        player.sendMessage("§7Violations of " + cfg.getLogsHighlight() + target.getName() + "§f:");
                        player.sendMessage(" ");

                        for (Check<?> check : karhuPlayer.getCheckManager().getChecks()) {
                            double vl = karhuPlayer.getCheckVl(check);
                            if (vl > 0) {
                                player.sendMessage(" §7* " + cfg.getLogsHighlight()
                                        + (check.isExperimental()
                                        ? check.getName() + cfg.getExpIcon()
                                        : check.getName())
                                        + " §7- " + "x" + cfg.getLogsHighlight() + vl);
                                v = vl;
                            }
                        }

                        if(v == 0) {
                            player.sendMessage("§cPlayer has no logs!");
                            player.sendMessage(" ");
                        }

                        player.sendMessage("§7§m--------------------------------------");
                    } else {
                        player.sendMessage(ChatColor.RED + "Couldn't find that player.");
                    }
                } else if (args[0].toLowerCase().equalsIgnoreCase("compactlogs") || args[0].toLowerCase().equalsIgnoreCase("clogs")) {
                    if (args.length == 1) {
                        player.sendMessage(ChatColor.RED + "Use: /karhu compactlogs <player>");
                        return;
                    }

                    Bukkit.getScheduler().runTaskAsynchronously(Karhu.getInstance(), () -> {

                        double v = 0;

                        String uuid = findUUID(args[1]);

                        List<ViolationX> vls = Karhu.storage.getAllViolations(uuid);

                        TreeMap<String, Integer> flagMap = new TreeMap<>();

                        for (ViolationX violationX : vls) {
                            flagMap.computeIfAbsent(violationX.type, k -> flagMap.put(k, 0));
                            flagMap.put(violationX.type, flagMap.get(violationX.type) + 1);
                            ++v;
                        }

                        player.sendMessage("§7§m--------------------------------------");
                        player.sendMessage("§7Violations of " + cfg.getLogsHighlight() + args[1] + " (" + flagMap.size() + ")" + "§f:");
                        player.sendMessage(" ");

                        for (Map.Entry<String, Integer> e : flagMap.entrySet()) {
                            player.sendMessage(" §7* " + cfg.getLogsHighlight()
                                    + e.getKey()
                                    + " §7- " + "x" + cfg.getLogsHighlight() + e.getValue());
                        }

                        if (v == 0) {
                            player.sendMessage("§cPlayer has no logs!");
                            player.sendMessage(" ");
                        }

                        player.sendMessage("§7§m--------------------------------------");
                    });
                } else if (args[0].equalsIgnoreCase("logs") && command.getSender() instanceof Player) {

                    if (!(command.getSender() instanceof Player) || player.hasPermission("karhu.logs")) {

                        Bukkit.getScheduler().runTaskAsynchronously(Karhu.getInstance(), () -> {
                            if (args.length >= 2) {

                                String uuid = findUUID(args[1]);

                                int page = args.length == 3 ? Integer.parseInt(args[2]) : 0;
                                List<ViolationX> vls = Karhu.storage.getViolations(uuid, null, page, 10, -1, -1);

                                if (vls.isEmpty()) {
                                    player.sendMessage("§cPlayer has no logs!");
                                    if (!Karhu.getInstance().getConfigManager().isCrackedServer()) return;

                                    uuid = Bukkit.getOfflinePlayer(args[1]).getName();
                                    vls = Karhu.storage.getViolations(uuid, null, page, 10, -1, -1);

                                    if (vls.isEmpty()) {
                                        player.sendMessage("§cPlayer has no logs!");
                                        return;
                                    }
                                }

                                int maxPages = Karhu.storage.getAllViolations(uuid).size() / 10;

                                player.sendMessage("§7Showing logs of " + cfg.getLogsHighlight() + args[1] + " §7(§a" + page + "§7/§2" + maxPages + "§7)");

                                for (ViolationX v : vls) {
                                    if (!v.data.contains("PUNISHED")) {
                                        String msgText = "§7* " + cfg.getLogsHighlight() + v.type + " §7VL: " + cfg.getLogsHighlight() + TextUtils.format(v.vl, 1) + " §7(§a" + TextUtils.formatMillis(System.currentTimeMillis() - v.time) + " ago§7)";
                                        BaseComponent msg = new TextComponent("§7* " + cfg.getLogsHighlight() + v.type + " §7VL: " + cfg.getLogsHighlight() + TextUtils.format(v.vl, 1) + " §7(§a" + TextUtils.formatMillis(System.currentTimeMillis() - v.time) + " ago§7)");
                                        msg.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(ChatColor.translateAlternateColorCodes('&', v.data.replaceAll("§b", Karhu.getInstance().getConfigManager().getAlertHoverMessageHighlight())) + "\n" + cfg.getLogsHighlight() + v.ping + "§7ms, " + cfg.getLogsHighlight() + v.TPS + "TPS").create()));
                                        msg.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/karhu teleport " + v.location + " " + v.world));

                                        if (!Karhu.getInstance().getConfigManager().getConfig().getBoolean("hoverless-alert")
                                                && (player.hasPermission("karhu.hover-debug"))) {
                                            if (Karhu.getInstance().getConfigManager().getConfig().getBoolean("spigot-api-alert")) {
                                                player.spigot().sendMessage(msg);
                                            } else {
                                                player.spigot().sendMessage(msg);
                                            }
                                        } else {
                                            player.sendMessage(msgText);
                                        }
                                    } else {
                                        String msgText = "§7* " + cfg.getLogsBan() + v.type + " §7VL: " + cfg.getLogsBan() + TextUtils.format(v.vl, 1) + " §7(" + cfg.getLogsBan() + TextUtils.formatMillis(System.currentTimeMillis() - v.time) + " ago§7)";
                                        BaseComponent msg = new TextComponent("§7* " + cfg.getLogsBan() + v.type + " §7VL: " + cfg.getLogsBan() + TextUtils.format(v.vl, 1) + " §7(" + cfg.getLogsBan() + TextUtils.formatMillis(System.currentTimeMillis() - v.time) + " ago§7)");
                                        msg.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(ChatColor.translateAlternateColorCodes('&', v.data.replaceAll("§b", Karhu.getInstance().getConfigManager().getAlertHoverMessageHighlight())) + "\n" + cfg.getLogsHighlight() + v.ping + "§7ms, " + cfg.getLogsHighlight() + v.TPS + "TPS").create()));
                                        msg.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/karhu teleport " + v.location + " " + v.world));

                                        if (!Karhu.getInstance().getConfigManager().getConfig().getBoolean("hoverless-alert")
                                                && (player.hasPermission("karhu.hover-debug"))) {
                                            if (Karhu.getInstance().getConfigManager().getConfig().getBoolean("spigot-api-alert")) {
                                                player.spigot().sendMessage(msg);
                                            } else {
                                                player.spigot().sendMessage(msg);
                                            }
                                        } else {
                                            player.sendMessage(msgText);
                                        }
                                    }
                                }

                                //vls.stream().map(v -> "§7* §b" + v.type + " §7VL: §b" + TextUtils.format(v.vl, 1) + " §7(§a" + TextUtils.formatMillis(System.currentTimeMillis() - v.time) + " ago§7)").forEach(player::sendMessage);

                            } else
                                command.getSender().sendMessage("§c/" + Karhu.getInstance().getConfigManager().getName().toLowerCase() + " logs <player> <page>");
                        });
                    }
                } else if (args[0].equalsIgnoreCase("top")) {
                    if (sender instanceof ConsoleCommandSender || sender.hasPermission("karhu.top")) {
                        Bukkit.getScheduler().runTaskAsynchronously(Karhu.getInstance(), () -> {
                            TreeMap<UUID, Check> highestVl = new TreeMap<>();

                            for (Player pl : Bukkit.getOnlinePlayers()) {

                                final KarhuPlayer data = Karhu.getInstance().getDataManager().getPlayerData(pl.getUniqueId());
                                double highest = 0;

                                for (Check check : data.getCheckManager().getChecks()) {
                                    double vl = data.getCheckVl(check);
                                    if (vl > highest) {
                                        highestVl.put(pl.getUniqueId(), check);

                                        highest = vl;
                                    }
                                }
                            }

                            if (!highestVl.isEmpty()) {

                                sender.sendMessage("§7§m--------------------------------------");

                                int looped = 1;
                                for (Map.Entry<UUID, Check> e : highestVl.entrySet()) {

                                    Player p = Bukkit.getPlayer(e.getKey());
                                    if (p != null) {
                                        final KarhuPlayer data = Karhu.getInstance().getDataManager().getPlayerData(p.getUniqueId());

                                        if (looped > 10) break;
                                        if (!Karhu.getInstance().getConfigManager().isCrackedServer()) {
                                            sender.sendMessage(cfg.getLogsHighlight() + "#" + looped + " §7" + p.getDisplayName()
                                                    + " - " + cfg.getLogsHighlight() + data.getCheckVl(e.getValue())
                                                    + " §7- (Most: " + cfg.getLogsHighlight() + e.getValue().getName() + "§7)"
                                                    + " §7- (Total: " + cfg.getLogsHighlight()
                                                    + Karhu.getStorage().getAllViolations(String.valueOf(e.getKey())).size() + "§7)");
                                        } else {
                                            sender.sendMessage(cfg.getLogsHighlight() + "#" + looped + " §7" + p.getDisplayName()
                                                    + " §7- " + cfg.getLogsHighlight() + data.getCheckVl(e.getValue())
                                                    + " §7- (Most: " + cfg.getLogsHighlight() + e.getValue().getName() + "§7)"
                                                    + " §7- (Total: " + cfg.getLogsHighlight() + Karhu.getStorage()
                                                    .getAllViolations(p.getName()).size() + "§7)");
                                        }
                                        looped += 1;
                                    }
                                }

                                sender.sendMessage("§7§m--------------------------------------");

                            } else {
                                sender.sendMessage("§c" + cfg.getName() + " is very sad to announce this, but your anticheat hasn't flagged anybody :(");
                            }
                        });
                    }
                } else if (args[0].equalsIgnoreCase("pastelogs")) {

                    if (!(command.getSender() instanceof Player) || player.hasPermission("karhu.pastelogs")) {

                        Bukkit.getScheduler().runTaskAsynchronously(Karhu.getInstance(), () -> {
                            if (args.length >= 2) {

                                String uuid = findUUID(args[1]);

                                List<ViolationX> vls = Karhu.storage.getAllViolations(uuid);

                                if (vls.isEmpty()) {
                                    sender.sendMessage("§cPlayer has no logs!");
                                    return;
                                }

                                StringBuilder end = new StringBuilder("Anticheat logs for player " + args[1] + " pasted with " + Karhu.getInstance().getConfigManager().getName() + " " + Karhu.getInstance().getVersion());

                                Bukkit.getScheduler().runTaskAsynchronously(Karhu.getInstance(), () -> {
                                    try {
                                        for (ViolationX v : vls) {
                                            String logline = TextUtils.formatMillis(System.currentTimeMillis() - v.time) + " ago | " + ChatColor.stripColor(v.type).replaceAll("\n", " ") + " [" + ChatColor.stripColor(v.data.replaceAll("\n", " ") + "]" + " [" + v.ping + "ms]" + "/[" + v.TPS + " TPS]" + " (x" + v.vl + ")");
                                            end.append("\n").append(logline);
                                        }
                                        String url = Hastebin.uploadPaste(end.toString());
                                        if (url == null) {
                                            sender.sendMessage("§cCouldn't paste logs, maybe the file is too big?");
                                            return;
                                        }
                                        sender.sendMessage("§aPasted logs to: §7" + url);
                                    } catch (Exception ex) {
                                        sender.sendMessage("§cCouldn't paste logs, maybe the file is too big?");
                                        ex.printStackTrace();
                                    }
                                });


                            } else
                                sender.sendMessage("§c/" + Karhu.getInstance().getConfigManager().getName().toLowerCase() + " pastelogs <player> <page>");
                        });
                    }
                } else if (args[0].equalsIgnoreCase("teleport")) {
                    if(sender instanceof Player && player.hasPermission("karhu.teleport")) {

                        if(args.length > 2) {
                            String[] coords = args[1].split(",");

                            World world = Bukkit.getWorld(args[2]);

                            Location location = null;

                            if(world != null && coords.length == 3) {
                                try {
                                    location = new Location(world,
                                            Double.parseDouble(coords[0]),
                                            Double.parseDouble(coords[1]),
                                            Double.parseDouble(coords[2])
                                    );
                                } catch (NumberFormatException ignored) {
                                }
                            }

                            if(location != null) {
                                player.teleport(location);
                                player.sendMessage("§aTeleporting to " + location.toVector().toString());
                            } else {
                                player.sendMessage("§cUse: /karhu teleport <x,y,z> <world>");
                            }
                        }
                    }

                } else if (args[0].equalsIgnoreCase("recentbans")) {

                    if (!(command.getSender() instanceof Player) || player.hasPermission("karhu.recentbans")) {

                        Bukkit.getScheduler().runTaskAsynchronously(Karhu.getInstance(), () -> {

                            List<BanX> bans = Karhu.storage.getRecentBans();

                            if (bans.isEmpty()) {
                                player.sendMessage("§cThere are no recent bans!");
                                return;
                            }

                            player.sendMessage("§7Showing " + cfg.getLogsHighlight() + " 10 §7 recent bans.");

                            for (BanX v : bans) {
                                String username = findName(v.player);
                                String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(v.time));

                                String msgText = "§7* §f" + username + " | " + cfg.getLogsHighlight() + v.type + " §7(§a" + TextUtils.formatMillis(System.currentTimeMillis() - v.time) + " ago | " + date + "§7)";
                                BaseComponent msg = new TextComponent("§7* §f" + username + " | " + cfg.getLogsHighlight() + v.type + " §7(§a" + TextUtils.formatMillis(System.currentTimeMillis() - v.time) + " ago | " + date + "§7)");

                                msg.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(ChatColor.translateAlternateColorCodes('&', v.data.replaceAll("§b", Karhu.getInstance().getConfigManager().getAlertHoverMessageHighlight())) + "\n" + cfg.getLogsHighlight() + v.ping + "§7ms, " + cfg.getLogsHighlight() + v.TPS + "TPS").create()));
                                msg.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/karhu logs " + username));

                                if (!Karhu.getInstance().getConfigManager().getConfig().getBoolean("hoverless-alert") && player.hasPermission("karhu.hover-debug")) {
                                    if (Karhu.getInstance().getConfigManager().getConfig().getBoolean("spigot-api-alert")) {
                                        player.spigot().sendMessage(msg);
                                    } else {
                                        /*WrappedPacketOutChat chat = new WrappedPacketOutChat(msg, WrappedPacketOutChat.ChatPosition.CHAT, player.getUniqueId());
                                        PlayerUtil.sendPacket(player, chat);*/
                                        player.spigot().sendMessage(msg);
                                    }
                                } else {
                                    player.sendMessage(msgText);
                                }
                            }
                            //vls.stream().map(v -> "§7* §b" + v.type + " §7VL: §b" + TextUtils.format(v.vl, 1) + " §7(§a" + TextUtils.formatMillis(System.currentTimeMillis() - v.time) + " ago§7)").forEach(player::sendMessage);
                        });
                    }
                } else if (args[0].equalsIgnoreCase("version")) {
                    command.getSender().sendMessage("§7Your build: §b" + Karhu.getInstance().getVersion() + " | " + Karhu.getInstance().getBuild());
                } else if(args[0].equalsIgnoreCase("banwave") && (!(sender instanceof Player) || player.hasPermission("karhu.banwave"))) {

                    String help = ChatColor.translateAlternateColorCodes('&',
                            Karhu.getInstance().getConfigManager().getConfig().getString("commands.banwave", "Check karhu discord customer announcements"));

                    if(args.length > 1) {
                        if (args[1].equalsIgnoreCase("list") || args[1].equalsIgnoreCase("players")) {
                            sender.sendMessage("Banwave list: (" + Karhu.getInstance().getWaveManager().getPlayersToBan().size() + ")");
                            for (String uuid : Karhu.getInstance().getWaveManager().getPlayersToBan()) {
                                String name = findName(uuid);
                                sender.sendMessage("- " + name);
                            }
                        } else if (args[1].equalsIgnoreCase("run") || args[1].equalsIgnoreCase("start")) {
                            if (!Karhu.getInstance().getWaveManager().isRunningBanwave() && !Karhu.getInstance().getWaveManager().getPlayersToBan().isEmpty()) {
                                sender.sendMessage("Starting banwave.");
                                Tasker.taskAsync(() -> {
                                    Karhu.getInstance().getWaveManager().startBanwave();
                                });
                            } else {
                                if (Karhu.getInstance().getWaveManager().isRunningBanwave()) {
                                    sender.sendMessage("Banwave is already running!");
                                } else {
                                    sender.sendMessage("Banwave has no players in it!");
                                }
                            }
                        } else if (args[1].equalsIgnoreCase("add")) {
                            if (Karhu.getInstance().getWaveManager().isRunningBanwave()) {
                                sender.sendMessage("Banwave is already running!");
                            } else {
                                if (args.length > 2) {
                                    String uuid = Karhu.getInstance().getConfigManager().isCrackedServer()
                                            ? args[2]
                                            : findUUID(args[2]);
                                    boolean added = Karhu.getInstance().getWaveManager().addToWave(uuid, "Manual");
                                    if (added) {
                                        sender.sendMessage("Added " + args[2] + " to banwave");
                                    } else {
                                        sender.sendMessage("Player " + args[2] + " is already in the banwave!");
                                    }
                                }
                            }
                        } else if (args[1].equalsIgnoreCase("remove")) {
                            if (args.length > 2) {
                                String uuid = Karhu.getInstance().getConfigManager().isCrackedServer()
                                        ? args[2]
                                        : findUUID(args[2]);
                                Karhu.getInstance().getWaveManager().removeFromWave(uuid);
                                sender.sendMessage("Removed " + args[2] + " from banwave");
                            }
                        } else if (args[1].equalsIgnoreCase(help)) {
                            sender.sendMessage(help);
                        } else {
                            sender.sendMessage(help);
                        }
                    } else {
                        sender.sendMessage(help);
                    }


                } else if(args[0].equalsIgnoreCase("setbacktracker")) {
                    Karhu.getInstance().getAlertsManager().toggleSetback(player);
                    player.sendMessage(Karhu.getInstance().getAlertsManager().hasSetbackToggled(player) ? "§aSetback tracker on" : "§cSetback tracker off");
                } else if (args[0].equalsIgnoreCase("worldtrack")) {
                    Map<World, Long2ObjectMap<Chunk>> chunksWorlds = Karhu.getInstance().getChunkManager().getLoadedChunks();

                    for (World world : chunksWorlds.keySet()) {
                        sender.sendMessage("§bWorld name: §f" + world.getName() + " §bchunk amount: §f" + Karhu.getInstance().getChunkManager().getCacheSize(world));
                    }
                } else if (args[0].equalsIgnoreCase("record")) {
                    if (sender.hasPermission("karhu.replay") && args.length > 2 && isSafeRecordingName(args[2])) {

                        Player target = Bukkit.getPlayer(args[1]);
                        KarhuPlayer targetData = target == null ? null : Karhu.getInstance().getDataManager().getPlayerData(target);

                        if (targetData == null) {
                            sender.sendMessage("§cSorry, i couldn't find that player");
                            return;
                        }

                        targetData.setRecording(!targetData.isRecording());
                        targetData.setRecordingName(args[2]);

                        String status = targetData.isRecording() ? ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled";
                        sender.sendMessage(String.format("Record status of %s is %s.", target.getName(), status + ChatColor.RESET));
                    }

                } else if (args[0].equalsIgnoreCase("replay")) {

                    if (sender.hasPermission("karhu.replay") && args.length > 1 && isSafeRecordingName(args[1])) {
                        String recordingName = args[1];
                        OfflinePlayer replayingPlayer = Bukkit.getOfflinePlayer(UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5"));
                        KarhuPlayer playerData = Karhu.getInstance().getDataManager().add(new User(null, null, ClientVersion.V_1_21_7, new UserProfile(replayingPlayer.getUniqueId(), "Notch")), System.nanoTime());
                        playerData.setReplaying(true);
                        playerData.setRecordingName(recordingName);

                        File dataFolder = Karhu.getInstance().getDataFolder();
                        File replaysFolder = new File(dataFolder, "replays");
                        if (!replaysFolder.isDirectory()) {
                            replaysFolder.mkdirs();
                        }

                        File file = new File(replaysFolder, recordingName + ".txt");
                        if (!file.exists()) {
                            sender.sendMessage("File not found: " + recordingName);
                            return;
                        }

                        try {
                            sender.sendMessage(String.format("Replaying §e%s §rclicks...", recordingName));

                            List<AttackSwingPair> clicks = Files.lines(file.toPath())
                                    .map(line -> {
                                        String[] parts = line.split(",");
                                        long swingTime = Long.parseLong(parts[0].trim());
                                        Long attackTime = parts[1].trim().equals("null") ? null : Long.parseLong(parts[1].trim());
                                        return new AttackSwingPair(swingTime, attackTime);
                                    })
                                    .collect(Collectors.toList());

                            clicks.forEach(playerData::handleClick);

                            sender.sendMessage(String.format("Replay of §e%d §rclicks is done!", clicks.size()));
                            clicks.clear();
                        } catch (IOException | NumberFormatException e) {
                            getLogger().severe("Error processing file: " + e.getMessage());
                        }
                    }

                } else {
                    command.getSender().sendMessage(ChatColor.translateAlternateColorCodes('&', Karhu.getInstance().getConfigManager().getConfig().getString("commands.help")));
                }

            } else {
                command.getSender().sendMessage(ChatColor.translateAlternateColorCodes('&', Karhu.getInstance().getConfigManager().getConfig().getString("commands.help")));
            }
        }

    }

    private static boolean isSafeRecordingName(String name) {
        return name.matches("[A-Za-z0-9_-]{1,64}");
    }

    private String findUUID(String arg) {
        if (Karhu.getInstance().getConfigManager().isCrackedServer()) {
            Player target = Bukkit.getPlayer(arg);

            if (target != null) {
                return arg;
            } else {
                return arg;
            }
        } else {
            Player target = Bukkit.getPlayer(arg);

            if (target != null) {
                return target.getUniqueId().toString();
            } else {
                return Bukkit.getOfflinePlayer(arg).getUniqueId().toString();
            }
        }
    }

    private String findName(String arg) {
        if(!arg.contains("-")) return arg;

        Player target = Bukkit.getPlayer(UUID.fromString(arg));

        if (target != null) {
            return target.getName();
        } else {
            return Bukkit.getOfflinePlayer(UUID.fromString(arg)).getName();
        }
    }
}



