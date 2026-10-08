package me.liwk.karhu.check.api;

import lombok.Getter;
import lombok.Setter;
import me.liwk.karhu.Karhu;
import me.liwk.karhu.api.check.Category;
import me.liwk.karhu.api.check.CheckInfo;
import me.liwk.karhu.api.check.SubCategory;
import me.liwk.karhu.check.setback.Setbacks;
import me.liwk.karhu.data.KarhuPlayer;
import me.liwk.karhu.handler.SimulationHandler;
import me.liwk.karhu.manager.ConfigManager;
import me.liwk.karhu.manager.alert.MiscellaneousAlertPoster;
import me.liwk.karhu.util.APICaller;
import me.liwk.karhu.util.MathUtil;
import me.liwk.karhu.util.bungee.BungeeAPI;
import me.liwk.karhu.util.discord.Webhook;
import me.liwk.karhu.util.location.CustomLocation;
import me.liwk.karhu.util.player.BlockUtil;
import me.liwk.karhu.util.task.Tasker;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.permissions.ServerOperator;

import java.awt.*;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Getter
@Setter
public abstract class Check<T> {

    protected final KarhuPlayer data;
    protected final Karhu karhu;
    protected final ConfigManager cfg;
    private int maxvl = 25;
    private int setbacks;

    private String name, desc, credits;
    private Category category;
    private SubCategory subCategory;
    private boolean subCheck, silent, experimental;

    private boolean setback = true;

    private CheckInfo checkInfo;

    protected double violations, subVl;

    private boolean didFail;

    private long lastFlag;
    private long now, nowNano;

    private Location flagLocation = null;

    protected static final boolean[] BOOLEANS = new boolean[]{true, false};
    protected static final boolean[] BOOLEANS_REVERSED = new boolean[]{false, true};

    protected SimulationHandler simulation;

    public Check(final KarhuPlayer data, Karhu karhu) {
        this.data = data;
        this.karhu = karhu;
        this.cfg = karhu.getConfigManager();

        this.name = getCheckInfo().name();
        this.desc = getCheckInfo().desc();
        this.credits = getCheckInfo().credits();

        this.category = getCheckInfo().category();
        this.subCategory = getCheckInfo().subCategory();
        this.subCheck = getCheckInfo().subCheck();

        this.silent = getCheckInfo().silent();
        this.experimental = getCheckInfo().experimental();

        this.simulation = data.getSimulationHandler();
    }

    public final CheckInfo getCheckInfo() {
        if (checkInfo == null) return this.getClass().getAnnotation(CheckInfo.class);
        return checkInfo;
    }

    public final void fail(String debug, long time) {
        this.fail(debug, getBanVL(), time);
    }

    public final void fail(String debug, int maxvl, long time) {
        if(Karhu.getInstance().getConfigManager().isPullback()) {
            flagLocation = data.getLastLocation().toLocation(data.getWorld());
        }

        if (data.getPositionPackets() < Math.min(5, Karhu.getInstance().getConfigManager().getExemptTicksJoin())) return;

        //SMH Fucking stupid
        if (data.getPositionPackets() < 150
                && data.deltas.deltaXZ > 20
                && data.getTransactionPing() == 0) return;

        didFail = true;

        this.maxvl = maxvl;

        this.now = System.currentTimeMillis();
        this.nowNano = System.nanoTime();

        final Player player = this.data.getBukkitPlayer();

        if (karhu.getConfigManager().isBypass() && player.hasPermission("karhu.bypass")) return;
        if (karhu.isServerLagging(now) || karhu.hasRecentlyDropped(1000L)) {
            if ((now - karhu.lastPerformanceAnnounce) > 10000L) {

                karhu.lastPerformanceAnnounce = now;

                switch (karhu.getConfigManager().getLagWarnDisplay().toUpperCase()) {
                    case "CONSOLE": {
                        String msg = karhu.getConfigManager().getLagWarnMsg()
                                .replaceAll("%prefix%", karhu.getConfigManager().getPrefix())
                                .replaceAll("%player%", player.getName());
                        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
                        break;
                    }
                    case "CHAT": {
                        Bukkit.getOnlinePlayers().stream().filter(ServerOperator::isOp)
                                .forEach(staff -> {
                                    String msg = karhu.getConfigManager().getLagWarnMsg()
                                            .replaceAll("%prefix%", karhu.getConfigManager().getPrefix())
                                            .replaceAll("%player%", player.getName());
                                    staff.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
                                });
                    }
                    case "NONE": {
                        break;
                    }
                    default:
                        break;
                }

            }
            return;
        }
        if (data.isBanned()) return;
        if (this.subCheck) return;

        if ((this.subCategory == SubCategory.AUTOCLICKER && !this.name.equals("AutoClicker (A)"))
                || this.subCategory == SubCategory.BADPACKETS
                || this.subCategory == SubCategory.KILLAURA) {

            if (data.isNewerThan8()) {
                if (((nowNano - data.lastFlying) / 1E6) > 55L)
                    return;
            }
        }

        if (category == Category.MOVEMENT) {
            data.invalidMovementTicks = 0;
        }

        final String locationParsed = format(2, data.getLocation().getX()) + "," +
                format(2, data.getLocation().getY()) + "," +
                format(2, data.getLocation().getZ());

        final String worldParsed = data.getWorld().getName();
        final int tempviolations = this.data.getViolations(this, time * 1000) + 1;

        final boolean autoban = Karhu.getInstance().getCheckState().isAutoban(this.name);
        final boolean banwave = Karhu.getInstance().getCheckState().isBanwave(this.name);

        String cmd = karhu.getConfigManager().getAlertClickCommand();

        String checkNameFormatted = this.experimental
                ? this.name + cfg.getExpIcon()
                : this.name;

        checkNameFormatted = !autoban
                ? checkNameFormatted + cfg.getNoPunishIcon()
                : checkNameFormatted;

        BaseComponent hover = new TextComponent(karhu.getConfigManager().getPrefix() + karhu.getConfigManager().getAlertMessage()
                .replace("%player%", data.getName())
                .replace("%version%", data.getClientVersion().toString().replaceAll("_", ".").replaceAll("v.", ""))
                .replace("%brand%", data.getBrand())
                .replace("%ping%", String.valueOf(data.getTransactionPing()))
                .replace("%tps%", String.valueOf(karhu.getTPS()))
                .replace("%check%", checkNameFormatted)
                .replace("%experimental%", MathUtil.booleanToString(experimental))
                .replace("%vl%", String.valueOf(tempviolations)));
        if (cmd != null) {
            hover.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd.replace("%player%", player.getName())));
        }

        String finalDebug = ChatColor.translateAlternateColorCodes('&', karhu.getConfigManager().getAlertHoverMessage()
                        .replace("%info%", debug.replaceAll("§b", Karhu.getInstance().getConfigManager().getAlertHoverMessageHighlight()))
                        .replace("%player%", data.getName())
                        .replace("%ping%", String.valueOf(data.getTransactionPing()))
                        .replace("%world%", player.getWorld().getName())
                        .replace("%ticks%", String.valueOf(data.getTotalTicks()))
                        .replace("%loc%", locationParsed)
                        .replace("%client%", data.getCleanBrand())
                        .replace("%check%", checkNameFormatted)
                        .replace("%experimental%", MathUtil.booleanToString(experimental))
                        .replace("%version%", data.getClientVersion().toString().replaceAll("_", ".").replaceAll("v.", ""))
                        .replace("%time%", String.valueOf(now - lastFlag))
                        .replace("%tps%", String.valueOf(karhu.getTPS())));

        hover.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(finalDebug).create()));

        String alert = karhu.getConfigManager().getPrefix() + karhu.getConfigManager().getAlertMessage()
                .replace("%player%", player.getName())
                .replace("%version%", data.getClientVersion().toString().replaceAll("_", ".").replaceAll("v.", ""))
                .replace("%brand%", data.getCleanBrand())
                .replace("%client%", data.getCleanBrand())
                .replace("%ping%", String.valueOf(data.getTransactionPing()))
                .replace("%tps%", String.valueOf(karhu.getTPS()))
                .replace("%check%", checkNameFormatted)
                .replace("%vl%", String.valueOf(tempviolations))
                .replace("%maxvl%", String.valueOf(maxvl));

        String databaseUser = Karhu.getInstance().getConfigManager().isCrackedServer()
                ? player.getName()
                : player.getUniqueId().toString();

        boolean autobanDisable = false;

        if (Karhu.isAPIAvailable()) {

            if (APICaller.callAlert(data.getBukkitPlayer(), this.getCheckInfo(), this, debug, hover, tempviolations, maxvl, data.getTransactionPing())) {

                this.data.addViolation(this);
                final int violations = this.data.getViolations(this, time * 1000);
                this.data.setCheckVl(violations, this);

                this.handleAlert(player, debug, alert, hover, violations);

                if (karhu.getConfigManager().isPullback() && (this.category == Category.MOVEMENT || this.category == Category.PACKET || this.category == Category.WORLD)) {
                    failSilent();
                }

                if (violations == getBanwaveVL() && banwave) {
                    Tasker.taskAsync(() -> {
                        Karhu.getInstance().getWaveManager().addToWave(databaseUser, name);
                    });
                }


                if (violations >= maxvl && autoban
                        && karhu.getConfigManager().isAutoban() && !data.isBanned()
                        && !player.hasPermission("karhu.bypass.ban")) {


                    if(!autobanDisable) {
                        Karhu.storage.addAlert(new ViolationX(databaseUser,
                                checkNameFormatted,
                                violations,
                                now,
                                debug + " [PUNISHED]",
                                locationParsed, worldParsed, data.getTransactionPing(), karhu.getTPS()));
                        Karhu.storage.addBan(new BanX(databaseUser,
                                checkNameFormatted,
                                now, debug, data.getTransactionPing(), karhu.getTPS()));
                    }


                    handlePunishment(player);

                    if (karhu.getConfigManager().isDiscordAlert() && !autobanDisable && karhu.getConfigManager().isSendBans()) {
                        this.karhu.getDiscordThread().execute(() -> this.handleDiscord(player, this.name, debug, violations, true));
                    }

                } else {

                    Karhu.storage.addAlert(new ViolationX(databaseUser,
                            checkNameFormatted,
                            violations, now,
                            debug, locationParsed, worldParsed, data.getTransactionPing(), karhu.getTPS()));

                }
            }

        } else {

            this.data.addViolation(this);
            final int violations = this.data.getViolations(this, time * 1000);
            this.data.setCheckVl(violations, this);

            this.handleAlert(player, debug, alert, hover, violations);

            if (karhu.getConfigManager().isPullback() && (this.category == Category.MOVEMENT
                    || this.category == Category.WORLD)) {

                failSilent();

                if(this.category == Category.MOVEMENT || this.name.equals("Timer (A)")) {
                    data.getLocation().setCheats(true);
                }
            }

            if (violations == getBanwaveVL() && banwave) {
                Tasker.taskAsync(() -> {
                    Karhu.getInstance().getWaveManager().addToWave(databaseUser, name);
                });
            }

            if (violations >= maxvl
                    && autoban
                    && karhu.getConfigManager().isAutoban() && !data.isBanned()
                    && !player.hasPermission("karhu.bypass.ban")) {

                if(!autobanDisable) {

                    Karhu.storage.addAlert(new ViolationX(databaseUser,
                            checkNameFormatted,
                            violations, now,
                            debug + " [PUNISHED]",
                            locationParsed, worldParsed, data.getTransactionPing(), karhu.getTPS()));
                    Karhu.storage.addBan(new BanX(databaseUser, checkNameFormatted,
                            now, debug, data.getTransactionPing(), karhu.getTPS()));

                }


                handlePunishment(player);

                if (karhu.getConfigManager().isDiscordAlert() && !autobanDisable && karhu.getConfigManager().isSendBans()) {
                    this.karhu.getDiscordThread().execute(() -> this.handleDiscord(player, this.name, debug, violations, true));
                }

            } else {

                Karhu.storage.addAlert(new ViolationX(databaseUser,
                        checkNameFormatted,
                        violations, now,
                        debug, locationParsed, worldParsed,
                        data.getTransactionPing(), karhu.getTPS()));

            }
        }

        if (category == Category.MOVEMENT) {
            data.setDidFlagMovement(true);
            data.setLastMovementFlag(data.getTotalTicks());
        }

    }

    public abstract void handle(final T obj);

    protected final String format(int places, Object obj) {
        return String.format("%." + places + "f", obj);
    }

    private void handleAlert(Player player, String debug, String text, BaseComponent hover, int violations) {

        String cmd = karhu.getConfigManager().getAlertClickCommand();

        if (cmd == null) return;

        if (now - lastFlag > Karhu.getInstance().getConfigManager().getAlertDelay()) {
            if (!karhu.getConfigManager().isHoverlessAlert()) {
                if (karhu.getConfigManager().isSpigotApiAlert()) {
                    for (UUID uuid : this.karhu.getAlertsManager().getAlertsToggled()) {
                        Player staff = Bukkit.getPlayer(uuid);
                        if (karhu.getConfigManager().isSpigotApiAlert() && staff != null) {
                            if(staff.hasPermission("karhu.hover-debug")) {
                                if (karhu.getConfigManager().isSpigotApiAlert()) {
                                    staff.spigot().sendMessage(hover);
                                } else {
                                    /*PlayerUtil.sendPacket(Bukkit.getPlayer(uuid), new WrappedPacketOutChat(hover,
                                            WrappedPacketOutChat.ChatPosition.CHAT, uuid));*/
                                    staff.spigot().sendMessage(hover);
                                }
                            } else staff.sendMessage(text);
                        }
                    }
                }
            } else {
                this.karhu.getAlertsManager().getAlertsToggled().stream().map(Bukkit::getPlayer)
                        .filter(Objects::nonNull)
                        .forEach(staff -> staff.sendMessage(text));
            }

            lastFlag = now;
        }

        if (karhu.getConfigManager().isDiscordAlert() && karhu.getConfigManager().isSendAlerts()) {
            int modulo = karhu.getConfigManager().getConfig().getInt("discord.post-vl-rate");

            if (violations % modulo == 0) {
                this.karhu.getDiscordThread().execute(() -> this.handleDiscord(player, this.name, debug, violations, false));
            }
        }

        if (karhu.getConfigManager().isBungeeAlert()) {
            if (violations % karhu.getConfigManager().getBungeePostRate() == 0) {
                BungeeAPI.sendAlert(this.experimental
                        ? this.name + cfg.getExpIcon() : this.name + "#" + violations + "#" + player.getName());
            }
        }

    }

    public void handleDiscord(Player player, String check, String data, int violations, boolean punish) {

        String hookURL = karhu.getConfigManager().getConfig().getString("discord.alert-webhook-url");

        final Webhook discord = new Webhook(hookURL);

        final boolean showWorld = karhu.getConfigManager().getConfig().getBoolean("discord.show-world");
        final boolean showStats = karhu.getConfigManager().getConfig().getBoolean("discord.show-statistics");
        final boolean showIcon = karhu.getConfigManager().getConfig().getBoolean("discord.show-icon-thumbnail");

        final boolean sendPunish = karhu.getConfigManager().isSendBans();
        final boolean sendAlerts = karhu.getConfigManager().isSendAlerts();


        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();

        discord.setUsername(karhu.getConfigManager().getName());
        discord.setTts(false);

        final String serverName = karhu.getConfigManager().getServerName().equalsIgnoreCase("Karhu")
                ? "Karhu (edit by changing server-name in config.yml)"
                : karhu.getConfigManager().getServerName();

        if (!punish) {

            if (sendAlerts) {

                if (showIcon) {
                    discord.addEmbed(new Webhook.EmbedObject()
                            .setTitle("```" + player.getName() + " " + player.getUniqueId() + "``` | " + check + " (x" + violations + ")")
                            .setThumbnail("https://minotar.net/avatar/" + player.getName() + "/50.png")
                            .setDescription(ChatColor.stripColor(data.replaceAll("\n", " ")))
                            .setColor(Color.CYAN)
                            .addField("Server: ", serverName, true)
                            .addField("Info",
                                    (showWorld ? "W: " + player.getWorld().getName() + " | C: " + format(1, player.getLocation().getX()) + "/" + format(1, player.getLocation().getY()) + "/" + format(1, player.getLocation().getZ())
                                            : " C: " + format(1, player.getLocation().getX()) + "/" + format(1, player.getLocation().getY()) + "/" + format(1, player.getLocation().getZ()))
                                            + (showStats ? " | TPS: " + Karhu.getInstance().getTPS() + " | Ping: "
                                            + this.data.getTransactionPing() + "ms" + " | CL: " + this.data.getCleanBrand()
                                            + " | V: " + MathUtil.parseVersion(this.data.getClientVersion()) : ""
                                            + " | T: " + this.data.getTotalTicks()), false)
                            .addField("Date", dtf.format(now), false));
                } else {
                    discord.addEmbed(new Webhook.EmbedObject()
                            .setTitle("```" + player.getName() + " " + player.getUniqueId() + "``` | " + check + " (x" + violations + ")")
                            .setDescription(ChatColor.stripColor(data.replaceAll("\n", " ")))
                            .setColor(Color.CYAN)
                            .addField("Server: ", serverName, true)
                            .addField("Info",
                                    (showWorld ? "W: " + player.getWorld().getName() + " | C: " + format(1, player.getLocation().getX()) + "/" + format(1, player.getLocation().getY()) + "/" + format(1, player.getLocation().getZ())
                                            : " C: " + format(1, player.getLocation().getX()) + "/" + format(1, player.getLocation().getY()) + "/" + format(1, player.getLocation().getZ()))
                                            + (showStats ? " | TPS: " + Karhu.getInstance().getTPS() + " | Ping: "
                                            + this.data.getTransactionPing() + "ms" + " | CL: " + this.data.getCleanBrand()
                                            + " | V: " + MathUtil.parseVersion(this.data.getClientVersion()) : ""
                                            + " | T: " + this.data.getTotalTicks()), false)
                            .addField("Date", dtf.format(now), false));
                }
                try {
                    discord.execute();
                } catch (IOException ex) {
                    if (ex.toString().contains("429")) {
                        karhu.getLogger().warning("Unable to post discord webhook: 429 Too many requests");
                    } else if (!ex.getMessage().contains("no protocol")) {
                        karhu.getLogger().warning("Unable to post discord webhook: " + ex.getMessage());
                    }
                }
            }
        } else {

            if (sendPunish) {
                if (showIcon) {
                    discord.addEmbed(new Webhook.EmbedObject()
                            .setTitle("```" + player.getName() + " " + player.getUniqueId() + "``` | " + check + " (x" + violations + ")")
                            .setThumbnail("https://minotar.net/avatar/" + player.getName() + "/50.png")
                            .setDescription(ChatColor.stripColor(data.replaceAll("\n", " ")))
                            .setColor(Color.RED)
                            .addField("Server: ", serverName, true)
                            .addField("Info",
                                    (showWorld ? "W: " + player.getWorld().getName() + " | C: " + format(1, player.getLocation().getX()) + "/" + format(1, player.getLocation().getY()) + "/" + format(1, player.getLocation().getZ())
                                            : " C: " + format(1, player.getLocation().getX()) + "/" + format(1, player.getLocation().getY()) + "/" + format(1, player.getLocation().getZ()))
                                            + (showStats ? " | TPS: " + Karhu.getInstance().getTPS() + " | Ping: "
                                            + this.data.getTransactionPing() + "ms" + " | CL: " + this.data.getCleanBrand()
                                            + " | V: " + MathUtil.parseVersion(this.data.getClientVersion()) : ""
                                            + " | T: " + this.data.getTotalTicks()), false)
                            .addField("Date", dtf.format(now), false));
                } else {
                    discord.addEmbed(new Webhook.EmbedObject()
                            .setTitle("```" + player.getName() + " " + player.getUniqueId() + "``` | " + check + " (x" + violations + ")")
                            .setDescription(ChatColor.stripColor(data.replaceAll("\n", " ")))
                            .setColor(Color.RED)
                            .addField("Server: ", serverName, true)
                            .addField("Info",
                                    (showWorld ? "W: " + player.getWorld().getName() + " | C: " + format(1, player.getLocation().getX()) + "/" + format(1, player.getLocation().getY()) + "/" + format(1, player.getLocation().getZ())
                                            : " C: " + format(1, player.getLocation().getX()) + "/" + format(1, player.getLocation().getY()) + "/" + format(1, player.getLocation().getZ()))
                                            + (showStats ? " | TPS: " + Karhu.getInstance().getTPS() + " | Ping: "
                                            + this.data.getTransactionPing() + "ms" + " | CL: " + this.data.getCleanBrand()
                                            + " | V: " + MathUtil.parseVersion(this.data.getClientVersion()) : ""
                                            + " | T: " + this.data.getTotalTicks()), false)
                            .addField("Date", dtf.format(now), false));
                }


                try {
                    discord.execute();
                } catch (IOException ex) {
                    if (ex.toString().contains("429")) {
                        karhu.getLogger().warning("Unable to post discord webhook: 429 Too many requests");
                    } else if (!ex.getMessage().contains("no protocol")) {
                        karhu.getLogger().warning("Unable to post discord webhook: " + ex.getMessage());
                    }
                }
            }
        }
    }


    private void handlePunishment(Player player) {
        long delayTime = karhu.getConfigManager().getCommandDelay();

        //player.kickPlayer("xxx");

        /*Karhu.getInstance().getAlertsManager().getAlertsToggled().stream().map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .forEach(staff -> staff.sendMessage("§7[§c!§7] §c" + data.getBukkitPlayer().getName() + " §7can't be banned due to B206 bug testing phase"));*/

        if (!Karhu.isAPIAvailable()) {
            if (Karhu.getInstance().getConfigManager().isDisallowFlagsAfterPunish()) {
                data.setBanned(true);
            }
            if (karhu.getConfigManager().isPunishBroadcast()) {
                Bukkit.broadcastMessage(
                        ChatColor.translateAlternateColorCodes('&',
                                Karhu.getInstance().getConfigManager().getConfig()
                                        .getString("Punishments.message")
                                        .replaceAll("%check%", this.name)
                                        .replaceAll("%player%", data.getName())));
            }
            boolean firstLoop = true;

            java.util.List<String> banCMD = Karhu.getInstance().getCheckState().isBanning(this)
                    ? Karhu.getInstance().getConfigManager().getPunishmentsBan()
                    : Karhu.getInstance().getConfigManager().getPunishmentsKick();


            if (!Karhu.getInstance().getConfigManager().isBungeeCommand()) {
                for (String ban : banCMD) {
                    if(firstLoop) {
                        Tasker.run(() -> {

                            Bukkit.getServer().dispatchCommand(
                                    Bukkit.getConsoleSender(),
                                    ban.replaceAll("%player%", player.getName())
                                            .replaceAll("%check%", this.name));
                        });
                        firstLoop = false;
                    } else {
                        Tasker.runTaskLater(() -> {
                            Bukkit.getServer().dispatchCommand(
                                    Bukkit.getConsoleSender(),
                                    ban.replaceAll("%player%", player.getName())
                                            .replaceAll("%check%", this.name));
                        }, 20 * delayTime);
                    }
                }
            } else {
                for (String ban : banCMD) {
                    if(firstLoop) {
                        Tasker.run(() -> {
                            BungeeAPI.sendCommand(ban.replaceAll("%player%", player.getName()).replaceAll("%check%", this.name));
                        });
                        firstLoop = false;
                    } else {
                        Tasker.runTaskLater(() -> {
                            BungeeAPI.sendCommand(ban.replaceAll("%player%", player.getName()).replaceAll("%check%", this.name));
                        }, 20 * delayTime);
                    }
                }
            }
        } else {
            if (APICaller.callBan(player, this.getCheckInfo(), this)) {
                if (Karhu.getInstance().getConfigManager().isDisallowFlagsAfterPunish()) {
                    data.setBanned(true);
                }
                if (karhu.getConfigManager().isPunishBroadcast()) {
                    Bukkit.broadcastMessage(
                            ChatColor.translateAlternateColorCodes('&',
                                    Karhu.getInstance().getConfigManager().getConfig()
                                            .getString("Punishments.message")
                                            .replaceAll("%check%", this.name)
                                            .replaceAll("%player%", data.getName())));
                }
                boolean firstLoop = true;

                List<String> banCMD = Karhu.getInstance().getCheckState().isBanning(this)
                        ? Karhu.getInstance().getConfigManager().getPunishmentsBan()
                        : Karhu.getInstance().getConfigManager().getPunishmentsKick();

                if (!Karhu.getInstance().getConfigManager().isBungeeCommand()) {
                    for (String ban : banCMD) {
                        if(firstLoop) {
                            Tasker.run(() -> {
                                Bukkit.getServer().dispatchCommand(
                                        Bukkit.getConsoleSender(),
                                        ban.replaceAll("%player%", player.getName())
                                                .replaceAll("%check%", this.name));
                            });
                            firstLoop = false;
                        } else {
                            Tasker.runTaskLater(() -> {
                                Bukkit.getServer().dispatchCommand(
                                        Bukkit.getConsoleSender(),
                                        ban.replaceAll("%player%", player.getName())
                                                .replaceAll("%check%", this.name));
                            }, 20 * delayTime);
                        }
                    }
                } else {
                    for (String ban : banCMD) {
                        if(firstLoop) {
                            Tasker.run(() -> {
                                BungeeAPI.sendCommand(ban.replaceAll("%player%", player.getName()).replaceAll("%check%", this.name));
                            });
                            firstLoop = false;
                        } else {
                            Tasker.runTaskLater(() -> {
                                BungeeAPI.sendCommand(ban.replaceAll("%player%", player.getName()).replaceAll("%check%", this.name));
                            }, 20 * delayTime);
                        }
                    }
                }
            }
        }
    }

    public int getBanVL() {
        return Karhu.getInstance().getCheckState().getCheckVl(this.name);
    }

    public int getBanwaveVL() {
        return Karhu.getInstance().getCheckState().getBanwaveVl(this.name);
    }

    protected void failSilent() {
        if(data.isInitialized()) pullback();
    }

    public void disallowMove(boolean glide) {
        if (data.isInitialized() && canSetbackStrict()) {
            cancel(glide);
        }
    }

    public void cancel(boolean glide) {
        if(!glide && data.elapsed(data.getLastMovementFlag()) <= 1 && !data.isPossiblyTeleporting()) return;

        final Player player = this.data.getBukkitPlayer();
        final Location teleport = Setbacks.forgeToRotatedLocation(data.getSafeSetback().toLocation(data.getWorld()), data);

        MiscellaneousAlertPoster.postSetback(data.getName() + ChatColor.RED + " "
                + (glide ? " chunk lag " : this.name + " limit exceeded ") + teleport.toVector()
                + " tp: " + data.getTeleportManager().teleportTicks);

        if (canSetbackStrict2(teleport)) {
            if (Karhu.isAPIAvailable()) {

                if (APICaller.callPullback(player, this.getCheckInfo(), this, teleport)) {

                    Tasker.run(() -> {
                        data.teleport(teleport);
                    });
                }
            } else {
                Tasker.run(() -> {
                    data.teleport(teleport);
                });
            }

            data.setDidFlagMovement(true);
            data.setLastMovementFlag(data.getTotalTicks());
        }
    }

    public void pullback() {

        if(!data.isDidFlagMovement()) {

            final Player player = this.data.getBukkitPlayer();

            final CustomLocation setback = Setbacks.forgeToRotatedLocation(data.getSafeSetback(), data);

            setback.setY(setback.getY() + 0.1);

            if (canSetback()) {
                if (Karhu.isAPIAvailable()) {

                    if (APICaller.callPullback(player, this.getCheckInfo(), this, setback.toLocation(data.getWorld()))) {
                        Tasker.run(() -> data.teleport(setback));
                    }

                } else {
                    Tasker.run(() -> data.teleport(setback));
                }
            }

        }
    }

    public void pullback(Location location) {

        if(!data.isDidFlagMovement()) {

            final Player player = this.data.getBukkitPlayer();

            if(location != null) {
                final Location setback = Setbacks.forgeToRotatedLocation(location, data);

                if (canSetback()) {

                    MiscellaneousAlertPoster.postSetback(data.getName() + " | §c" + this.name + " flying desynced");

                    data.setDidFlagMovement(true);
                    data.setLastMovementFlag(data.getTotalTicks());

                    if (Karhu.isAPIAvailable()) {
                        if (APICaller.callPullback(player, this.getCheckInfo(), this, setback)) {
                            Tasker.run(() -> data.teleport(setback));
                        }
                    } else {
                        Tasker.run(() -> data.teleport(setback));
                    }
                }
            }
        }
    }

    public void pullback(CustomLocation location) {

        if(!data.isDidFlagMovement()) {

            final Player player = this.data.getBukkitPlayer();

            if(location != null) {
                final CustomLocation setback = Setbacks.forgeToRotatedLocation(location, data);

                if (canSetback()) {

                    MiscellaneousAlertPoster.postSetback(data.getName() + " | §c" + this.name + " flying desynced");

                    data.setDidFlagMovement(true);
                    data.setLastMovementFlag(data.getTotalTicks());

                    if (Karhu.isAPIAvailable()) {
                        if (APICaller.callPullback(player, this.getCheckInfo(), this, setback.toLocation(data.getWorld()))) {
                            Tasker.run(() -> data.teleport(setback));
                        }
                    } else {
                        Tasker.run(() -> data.teleport(setback));
                    }
                }
            }
        }
    }

    public boolean canSetback() {
        return BlockUtil.chunkLoaded(this.data.getLastLocation().toLocation(data.getWorld()))
                && BlockUtil.chunkLoaded(this.data.getLastLastLocation().toLocation(data.getWorld()))
                && BlockUtil.chunkLoaded(this.data.getLocation().toLocation(data.getWorld()))
                && !data.isPossiblyTeleporting()
                && !data.getBukkitPlayer().isDead()
                && !data.isDidFlagMovement()
                && data.getTotalTicks() > 40;
    }

    public boolean canSetbackStrict() {
        return BlockUtil.chunkLoaded(this.data.getLastLocation().toLocation(data.getWorld()))
                && BlockUtil.chunkLoaded(this.data.getLastLastLocation().toLocation(data.getWorld()))
                && BlockUtil.chunkLoaded(this.data.getLocation().toLocation(data.getWorld()))
                && !data.isPossiblyTeleporting()
                && !data.getBukkitPlayer().isDead()
                && data.getTotalTicks() > 40;
    }

    public boolean canSetbackStrict2(Location location) {
        if (BlockUtil.chunkLoaded(location)) {
            if (location.getBlock().getType().isSolid()) {
                location = fixSetback(location.clone());
            }
        }

        if (location.getWorld() != data.getWorld()) {
            location = fixSetback(data.getLocation().toLocation(data.getWorld()));
        }

        return BlockUtil.chunkLoaded(location)
                && !location.getBlock().getType().isSolid()
                && !data.isWasWasInUnloadedChunk()
                && !data.isWasInUnloadedChunk()
                && !data.isInUnloadedChunk()
                && !data.isPossiblyTeleporting()
                && !data.getBukkitPlayer().isDead()
                && data.getTotalTicks() > 40;
    }

    public Location fixSetback(Location location) {

        World world = data.getWorld();

        Location pushedOut = Setbacks.moveOutOfBlockSafely(location.getX(), location.getZ(), data);

        if (pushedOut == null) {
            if (data.getLocation().distance(data.getLastLastLocation()) <= 5) {
                location = data.getLastLastLocation().toLocation(world);
            } else {
                location = data.getLastLocation().toLocation(world);
            }
            return Setbacks.forgeToRotatedLocation(location, data);
        }

        return Setbacks.forgeToRotatedLocation(pushedOut, data);
    }

    public void debug(String formatted) {
        String debugForm = "§7[§b" + this.name + "§7] §9" + data.getName() + " §f" + formatted;

        this.karhu.getAlertsManager()
                .getDebugToggled()
                .stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .forEach(admin -> admin.sendMessage(debugForm));
    }

    public void debugMisc(String formatted) {
        String debugForm = "§7[§b" + this.name + "§7] §9" + data.getName() + " §f" + formatted;

        this.karhu.getAlertsManager()
                .getMiscDebugToggled()
                .stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .forEach(admin -> admin.sendMessage(debugForm));
    }


    protected double increase(double increase) {
        violations += increase;
        return violations;
    }
    protected void decrease(double decrease) {
        violations = Math.max(0, violations - decrease);
    }

    protected double increaseSub(double increase) {
        subVl += increase;
        return subVl;
    }
    protected void decreaseSub(double decrease) {
        subVl = Math.max(0, subVl - decrease);
    }

    protected boolean canClick() {
        return !data.isHasDig() && !data.isPlacing() && !data.isUsingItem() && !data.isSpectating();
    }
}

