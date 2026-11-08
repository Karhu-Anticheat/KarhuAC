package me.liwk.karhu.handler.global.bukkit;

import com.github.retrooper.packetevents.manager.server.ServerVersion;
import me.liwk.karhu.Karhu;
import me.liwk.karhu.antivpn.VPNCheck;
import me.liwk.karhu.data.KarhuPlayer;
import me.liwk.karhu.manager.alert.MiscellaneousAlertPoster;
import me.liwk.karhu.util.APICaller;
import me.liwk.karhu.util.player.PlayerUtil;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.*;

import java.util.UUID;

public final class BukkitHandler implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoinMonitor(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if(Karhu.getInstance().getConfigManager().isGeyserSupport()) {
            if (PlayerUtil.isGeyserPlayer(player)) {
                Karhu.getInstance().printCool(ChatColor.RED + player.getName() + " joined using geyser");
                Karhu.getInstance().getDataManager().remove(player.getUniqueId());
            }
        }
    }


    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        if (Karhu.getInstance().getAlertsManager().hasAlertsToggled(uuid)) {
            Karhu.getInstance().getAlertsManager().removeFromList(uuid);
        }

        if (Karhu.getInstance().getAlertsManager().hasMitigationToggled(event.getPlayer())) {
            Karhu.getInstance().getAlertsManager().getMitigationToggled().remove(uuid);
        }

        Karhu.getInstance().getDataManager().remove(uuid);

        if(Karhu.isAPIAvailable()) {
            APICaller.callUnregister(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {

        if (Karhu.SERVER_VERSION.isNewerThanOrEquals(ServerVersion.V_1_13)) {

            Location to = e.getTo();
            Location from = e.getFrom();

            final Player p = e.getPlayer();
            final KarhuPlayer data = Karhu.getInstance().getDataManager().getPlayerData(p);


            if (data != null) {
                if (to == null || to.getWorld() != from.getWorld()) return;
                if (!data.getCollisionHandler().hasCached() || data.isForceRunCollisions()) {
                    data.getCollisionHandler().cacheBlocks();
                } else if (to.distanceSquared(from) >= 0.002) {
                    data.getCollisionHandler().cacheBlocks();
                }
            }
        }
    }


    /*@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {

        if (Karhu.SERVER_VERSION.isNewerThanOrEquals(ServerVersion.V_1_13)) {

            Location to = e.getTo();
            Location from = e.getFrom();

            final Player p = e.getPlayer();
            final KarhuPlayer data = Karhu.getInstance().getDataManager().getPlayerData(p);

            if (data != null) {
                if (to == null || to.getWorld() != from.getWorld()) return;

                Tasker.run(() -> {
                    data.getCollisionHandler().cacheBlocks();
                });
            }
        }
    }*/

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageByEntityEvent e) {

        if(e.isCancelled()) return;

        if(e.getDamager() instanceof Player) {
            final Player player = (Player) e.getDamager();

            final KarhuPlayer data = Karhu.getInstance().getDataManager().getPlayerData(player);

            if (data != null) {

                boolean hitbox = Karhu.getInstance().getConfigManager().isHitboxCancel(),
                        reach = Karhu.getInstance().getConfigManager().isReachCancel(),
                        tripleBlock = Karhu.getInstance().getConfigManager().isTriplehitBlock();

                if (data.isCancelNextHitR() && reach) {
                    e.setDamage(0.0D);
                    MiscellaneousAlertPoster.postMitigation(data, data.suspiciousActionsVl,
                            "Reach",
                            "* Hit cancelled after reach flag"
                    );
                } else if(data.isCancelNextHitH() && hitbox) {
                    e.setDamage(0.0D);
                    e.setCancelled(true);
                    ++data.suspiciousActionsVl;
                    MiscellaneousAlertPoster.postMitigation(data, data.suspiciousActionsVl,
                            "Reach",
                            "* Hit cancelled after reach flag (2)"
                    );
                } else {
                    data.suspiciousActionsVl = Math.max(0, data.suspiciousActionsVl - 0.05);
                }

                if (data.elapsed(data.getCancelHitsTick()) < 30) {
                    e.setDamage(0.0D);
                    e.setCancelled(true);
                    ++data.suspiciousActionsVl;
                    MiscellaneousAlertPoster.postMitigation(data, data.suspiciousActionsVl,
                            "SuspiciousActions",
                            "* Hit cancelled for suspicious actions (1)"
                    );
                } else {
                    data.suspiciousActionsVl = Math.max(0, data.suspiciousActionsVl - 0.05);
                }

                if (data.isForceCancelReach()) {
                    if(e.getEntity().getEntityId() == data.getEntityIdCancel()) {
                        e.setDamage(0.0D);
                        e.setCancelled(true);
                        data.setForceCancelReach(false);
                        ++data.suspiciousActionsVl;
                        MiscellaneousAlertPoster.postMitigation(data, data.suspiciousActionsVl,
                                "Reach",
                                "* Hit cancelled, karhu doesn't track opponent locations"
                        );
                    }
                } else {
                    data.suspiciousActionsVl = Math.max(0, data.suspiciousActionsVl - 0.05);
                }

                if (tripleBlock && data.isCancelTripleHit()) {
                    e.setDamage(0.0D);
                    e.setCancelled(true);

                    ++data.suspiciousActionsVl;

                    data.setForceCancelReach(false);
                    MiscellaneousAlertPoster.postMitigation(data, data.suspiciousActionsVl,
                            "Reach",
                            "* Hit cancelled, triple hit"
                    );
                } else {
                    data.suspiciousActionsVl = Math.max(0, data.suspiciousActionsVl - 0.05);
                }

                if (data.isReduceNextDamage()) {
                    e.setDamage(e.getDamage() * 0.75D);
                    data.setReduceNextDamage(false);
                    ++data.suspiciousActionsVl;

                    MiscellaneousAlertPoster.postMitigation(data, data.suspiciousActionsVl,
                            "SuspiciousActions",
                            "* Damage reduced for suspicious actions (2)"
                    );
                } else {
                    data.suspiciousActionsVl = Math.max(0, data.suspiciousActionsVl - 0.05);
                }

                if (data.getHitsToCancel() > 0) {
                    if (data.isCancelLagAbuseHits()) {
                        e.setDamage(0.0D);
                        e.setCancelled(true);
                    } else {
                        e.setDamage(e.getDamage() * 0.5D);
                    }

                    data.setHitsToCancel(data.getHitsToCancel() - 1);
                }

                if (data.isAbusingVelocity()) {
                    e.setDamage(e.getDamage() * 0.5D);
                    data.setAbusingVelocity(false);
                    ++data.suspiciousActionsVl;
                    MiscellaneousAlertPoster.postMitigation(data, data.suspiciousActionsVl,
                            "SuspiciousActions",
                            "* Damage reduced for suspicious actions (3)"
                    );
                } else {
                    data.suspiciousActionsVl = Math.max(0, data.suspiciousActionsVl - 0.05);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent e) {

        if(e.isCancelled()) return;

        final Player player = e.getPlayer();

        final KarhuPlayer data = Karhu.getInstance().getDataManager().getPlayerData(player);

        if (data != null) {

            if(data.isCancelBreak()) {
                e.setCancelled(true);
                data.setCancelBreak(false);
            }
        }

    }

    // AsyncPlayerPreLoginEvent runs off the main thread and waits for its listeners,
    // so the blocking lookup can be done inline and disallow() is still taken into account.
    @EventHandler(priority = EventPriority.HIGH)
    public void onPreJoin(AsyncPlayerPreLoginEvent e) {
        if (e.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;

        if(!Karhu.getInstance().getConfigManager().isAntivpn()
            || (!Karhu.getInstance().getConfigManager().isProxycheck() && !Karhu.getInstance().getConfigManager().isMaliciouscheck())
            || Karhu.getInstance().getConfigManager().getAntiVpnBypass().contains(e.getUniqueId().toString())) {
            return;
        }

        if (VPNCheck.checkAddress(e.getAddress())) {
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, Karhu.getInstance().getConfigManager().getAntivpnKickMsg());
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {

        final Player p = event.getPlayer();
        final KarhuPlayer data = Karhu.getInstance().getDataManager().getPlayerData(p);

        if (data != null) {
            this.recorrectPlayerStates(data);
        }

    }

    @EventHandler
    public void onHunger(FoodLevelChangeEvent e) {
        if (e.getEntity().getType() == EntityType.PLAYER) {
            final Player p = (Player) e.getEntity();
            final KarhuPlayer data = Karhu.getInstance().getDataManager().getPlayerData(p);
            if (data != null) {
                if (data.isRecorrectingSprint()) {
                    e.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEvent(BlockPistonExtendEvent event) {
        final World blockWorld = event.getBlock().getWorld();
        final Location blockLocation = event.getBlock().getLocation();
        for (Player player : event.getBlock().getWorld().getPlayers()) {
            if(!blockWorld.equals(player.getWorld())) {
                return;
            }
            if (blockLocation.distance(player.getLocation()) <= 10) {
                KarhuPlayer data = Karhu.getInstance().getDataManager().getPlayerData(player);
                data.queueToPrePing(task -> {
                    data.setLastPistonPush(data.getTotalTicks());
                    data.setLastSlimePistonPush(data.getTotalTicks());
                });
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEvent(BlockPistonRetractEvent event) {
        final Block block = event.getBlock();
        final Location blockLocation = block.getLocation();
        for (Player player : block.getWorld().getPlayers()) {
            if(!block.getWorld().equals(player.getWorld())) {
                return;
            }
            if (blockLocation.distance(player.getLocation()) <= 10) {
                KarhuPlayer data = Karhu.getInstance().getDataManager().getPlayerData(player);
                data.queueToPrePing(task -> {
                    data.setLastPistonPush(data.getTotalTicks());
                    data.setLastSlimePistonPush(data.getTotalTicks());
                });
            }
        }
    }

    private void recorrectPlayerStates(KarhuPlayer data) {

        //this.Karhu.getInstance().getGhostBlockProcessor().onWorldChange(data.getBukkitPlayer());

        data.setRecorrectingSprint(true);
        data.setDesyncSprint(true);
        data.setLastWorldChange(data.getTotalTicks());

        //Resync sprinting
        data.getBukkitPlayer().setSprinting(true);
        data.getBukkitPlayer().setSprinting(false);

        /*data.getCheckVlMap().clear();
        data.getCheckViolationTimes().clear();*/

        /*
         * This is just an attempt at fixing the sprint de-sync when changing worlds, should work fine though.
         */
        /*final int oldHunger = data.getBukkitPlayer().getFoodLevel();

        Bukkit.getScheduler().runTaskLater(Karhu.getInstance(), () -> {
            data.getBukkitPlayer().setFoodLevel(1);
        }, 10L);


        Bukkit.getScheduler().runTaskLater(Karhu.getInstance(), () -> {
            data.getBukkitPlayer().setFoodLevel(oldHunger);
            data.setRecorrectingSprint(false);
        }, 13L);*/

    }

}
