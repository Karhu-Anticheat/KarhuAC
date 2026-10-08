package me.liwk.karhu.manager.alert;

import me.liwk.karhu.Karhu;
import me.liwk.karhu.util.task.Tasker;
import org.bukkit.entity.Player;

import java.util.*;

public final class AlertsManager {

    private final Set<UUID> debugToggled = Collections.synchronizedSet(new HashSet<>());
    private final Set<UUID> miscDebugToggled = Collections.synchronizedSet(new HashSet<>());
    private final Set<UUID> alertsToggled = Collections.synchronizedSet(new HashSet<>());
    private final Set<UUID> setbackToggled = Collections.synchronizedSet(new HashSet<>());

    private final Set<UUID> mitigationToggled = Collections.synchronizedSet(new HashSet<>());

    public boolean hasAlertsToggled(UUID uuid) {
        return this.alertsToggled.contains(uuid);
    }

    public boolean hasDebugToggled(Player player) {
        return this.debugToggled.contains(player.getUniqueId());
    }

    public boolean hasMiscDebugToggled(Player player) {
        return this.miscDebugToggled.contains(player.getUniqueId());
    }

    public boolean hasSetbackToggled(Player player) {
        return this.setbackToggled.contains(player.getUniqueId());
    }

    public boolean hasMitigationToggled(Player player) {
        return this.mitigationToggled.contains(player.getUniqueId());
    }

    public void toggleAlerts(Player player) {
        if (!this.alertsToggled.remove(player.getUniqueId())) {
            this.alertsToggled.add(player.getUniqueId());

            if (Karhu.getInstance().getConfigManager().isCrackedServer()) {
                Tasker.taskAsync(() -> Karhu.getStorage().setAlerts(player.getName(), 1));
            } else {
                Tasker.taskAsync(() -> Karhu.getStorage().setAlerts(player.getUniqueId().toString(), 1));
            }

        } else {

            if (Karhu.getInstance().getConfigManager().isCrackedServer()) {
                Tasker.taskAsync(() -> Karhu.getStorage().setAlerts(player.getName(), 0));
            } else {
                Tasker.taskAsync(() -> Karhu.getStorage().setAlerts(player.getUniqueId().toString(), 0));
            }

        }
    }

    public void removeFromList(UUID uuid) {
        this.alertsToggled.remove(uuid);
    }

    public void toggleDebug(Player player) {
        if (!this.debugToggled.remove(player.getUniqueId())) {
            this.debugToggled.add(player.getUniqueId());
        }
    }

    public void toggleMiscDebug(Player player) {
        if (!this.miscDebugToggled.remove(player.getUniqueId())) {
            this.miscDebugToggled.add(player.getUniqueId());
        }
    }

    public void toggleSetback(Player player) {
        if (!this.setbackToggled.remove(player.getUniqueId())) {
            this.setbackToggled.add(player.getUniqueId());
        }
    }

    public void toggleMitigation(Player player) {
        if (!this.mitigationToggled.remove(player.getUniqueId())) {
            this.mitigationToggled.add(player.getUniqueId());
        }
    }

    public void setMitigations(Player player, boolean state) {
        if (state) {
            if (!hasMitigationToggled(player)) {
                this.mitigationToggled.add(player.getUniqueId());
            }
        } else {
            this.mitigationToggled.remove(player.getUniqueId());
        }
    }

    public void setReceiveAlerts(Player player, boolean state) {
        if (!state) {
            this.alertsToggled.remove(player.getUniqueId());

            if(Karhu.getInstance().getConfigManager().isCrackedServer()) {
                Tasker.taskAsync(() -> Karhu.getStorage().setAlerts(player.getName(), 0));
            } else {
                Tasker.taskAsync(() -> Karhu.getStorage().setAlerts(player.getUniqueId().toString(), 0));
            }

        } else {
            this.alertsToggled.add(player.getUniqueId());

            if(Karhu.getInstance().getConfigManager().isCrackedServer()) {
                Tasker.taskAsync(() -> Karhu.getStorage().setAlerts(player.getName(), 1));
            } else {
                Tasker.taskAsync(() -> Karhu.getStorage().setAlerts(player.getUniqueId().toString(), 1));
            }


        }
    }

    public Set<UUID> getAlertsToggled() {
        return this.alertsToggled;
    }

    public Set<UUID> getDebugToggled() {
        return debugToggled;
    }

    public Set<UUID> getMiscDebugToggled() {
        return miscDebugToggled;
    }

    public Set<UUID> getSetbackToggled() {
        return setbackToggled;
    }

    public Set<UUID> getMitigationToggled() {
        return mitigationToggled;
    }

}

