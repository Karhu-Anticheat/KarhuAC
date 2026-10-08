package me.liwk.karhu.database.mysql;

import me.liwk.karhu.Karhu;
import me.liwk.karhu.check.api.BanWaveX;
import me.liwk.karhu.check.api.BanX;
import me.liwk.karhu.check.api.Check;
import me.liwk.karhu.check.api.ViolationX;
import me.liwk.karhu.data.KarhuPlayer;
import me.liwk.karhu.database.Query;
import me.liwk.karhu.database.Storage;
import me.liwk.karhu.util.MathUtil;
import me.liwk.karhu.util.NetUtil;
import me.liwk.karhu.util.task.Tasker;
import org.bukkit.Bukkit;

import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class MySQLStorage implements Storage {
    private ConcurrentLinkedQueue<ViolationX> violations = new ConcurrentLinkedQueue<>();
    private ConcurrentLinkedQueue<BanX> bans = new ConcurrentLinkedQueue<>();
    private ConcurrentLinkedQueue<BanWaveX> banWaveQueue = new ConcurrentLinkedQueue<>();

    @Override
    public void init() {
        MySQL.init();
        try {
            // LONG is an alias of MEDIUMTEXT in MySQL/MariaDB, numeric columns must be BIGINT
            Query.prepare("CREATE TABLE IF NOT EXISTS `ALERTS` (" +
                    "`UUID` TEXT NOT NULL," +
                    "`MODULE` TEXT NOT NULL," +
                    "`VL` SMALLINT NOT NULL," +
                    "`TIME` BIGINT NOT NULL," +
                    "`EXTRA` TEXT," +
                    "`COORDS` TEXT," +
                    "`WORLD` TEXT," +
                    "`PING` BIGINT NOT NULL," +
                    "`TPS` DOUBLE NOT NULL)").execute();
            Query.prepare("CREATE TABLE IF NOT EXISTS `ALERTSTATUS` (" +
                    "`UUID` VARCHAR(36) NOT NULL," +
                    "`STATUS` TINYINT(1) NOT NULL," +
                    "PRIMARY KEY (UUID))")
                    .execute();
            Query.prepare("CREATE TABLE IF NOT EXISTS `BANS` (" +
                    "`UUID` TEXT NOT NULL," +
                    "`MODULE` TEXT NOT NULL," +
                    "`TIME` BIGINT NOT NULL," +
                    "`EXTRA` TEXT," +
                    "`PING` BIGINT NOT NULL," +
                    "`TPS` DOUBLE NOT NULL)").execute();
            Query.prepare("CREATE TABLE IF NOT EXISTS `BANWAVE` (" +
                    "`UUID` TEXT NOT NULL," +
                    "`MODULE` TEXT NOT NULL," +
                    "`TIME` BIGINT NOT NULL," +
                    "`TOTALLOGS` SMALLINT NOT NULL)")
                    .execute();

            // Migrate tables created by older versions. ADD COLUMN IF NOT EXISTS only exists on MariaDB.
            addColumnIfMissing("ALERTS", "COORDS", "TEXT");
            addColumnIfMissing("ALERTS", "WORLD", "TEXT");
            migrateToBigint("ALERTS", "TIME");
            migrateToBigint("ALERTS", "PING");
            migrateToBigint("BANS", "TIME");
            migrateToBigint("BANS", "PING");
            migrateToBigint("BANWAVE", "TIME");
        } catch (Exception e) {
            Karhu.getInstance().printCool("&b> &cFailed to create MySQL tables: " + e.getMessage());
            e.printStackTrace();
        }
        new Thread(() -> {
            while (Karhu.getInstance() != null && Karhu.getInstance().isEnabled()) {
                try {
                    NetUtil.sleep(5000);
                    if (violations.isEmpty() && bans.isEmpty() && banWaveQueue.isEmpty()) continue;
                    if(!violations.isEmpty()) {
                        for (ViolationX violation : violations) {
                            try {
                                MySQL.use();
                                Query.prepare("INSERT INTO `ALERTS` (`UUID`, `MODULE`, `VL`, `TIME`, `EXTRA`, `COORDS`, `WORLD`, `PING`, `TPS`) VALUES (?,?,?,?,?,?,?,?,?)")
                                        .append(violation.player).append(violation.type).append(violation.vl)
                                        .append(violation.time).append(violation.data).append(violation.location)
                                        .append(violation.world)
                                        .append(violation.ping).append(violation.TPS).execute();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        violations.clear();
                    }

                    if(!bans.isEmpty()) {
                        for (BanX ban : bans) {
                            try {
                                MySQL.use();
                                Query.prepare("INSERT INTO `BANS` (`UUID`, `MODULE`, `TIME`, `EXTRA`, `PING`, `TPS`) VALUES (?,?,?,?,?,?)")
                                        .append(ban.player).append(ban.type).append(ban.time)
                                        .append(ban.data).append(ban.ping).append(ban.TPS)
                                        .execute();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        bans.clear();
                    }

                    if(!banWaveQueue.isEmpty()) {
                        for (BanWaveX wave : banWaveQueue) {
                            try {
                                MySQL.use();
                                Query.prepare("INSERT INTO `BANWAVE` (`UUID`, `MODULE`, `TIME`, `TOTALLOGS`) VALUES (?,?,?,?)")
                                        .append(wave.player).append(wave.type).append(wave.time).append(wave.totalLogs)
                                        .execute();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        banWaveQueue.clear();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, "KarhuMySQLCommitter").start();

    }

    private static String getColumnType(String table, String column) {
        AtomicReference<String> type = new AtomicReference<>();
        Query.prepare("SELECT `DATA_TYPE` FROM `information_schema`.`COLUMNS` " +
                        "WHERE `TABLE_SCHEMA` = DATABASE() AND `TABLE_NAME` = ? AND `COLUMN_NAME` = ?")
                .append(table).append(column).executeSingle(rs -> {
                    if (rs != null) type.set(rs.getString(1).toLowerCase(Locale.ROOT));
                });
        return type.get();
    }

    private static void addColumnIfMissing(String table, String column, String definition) {
        if (getColumnType(table, column) == null) {
            Query.prepare("ALTER TABLE `" + table + "` ADD COLUMN `" + column + "` " + definition).execute();
        }
    }

    private static void migrateToBigint(String table, String column) {
        String type = getColumnType(table, column);
        if (type == null || type.equals("bigint")) return;

        try {
            Query.prepare("ALTER TABLE `" + table + "` MODIFY `" + column + "` BIGINT NOT NULL").execute();
            Karhu.getInstance().printCool("&b> &fMigrated MySQL column " + table + "." + column + " from " + type + " to BIGINT");
        } catch (Exception e) {
            Karhu.getInstance().printCool("&b> &cCouldn't migrate MySQL column " + table + "." + column + " to BIGINT: " + e.getMessage());
        }
    }

    @Override
    public void addAlert(ViolationX violation) {
        violations.add(violation);
    }

    @Override
    public void addBan(BanX ban) {
        bans.add(ban);
    }

    @Override
    public void setAlerts(String uuid, int status) {
        MySQL.use();
        Query.prepare("INSERT INTO `ALERTSTATUS` (`UUID`, `STATUS`) VALUES (?,?) ON DUPLICATE KEY UPDATE STATUS=" + status)
                .append(uuid).append(status)
                .execute();
    }

    @Override
    public boolean getAlerts(String uuid) {
        try {
            MySQL.use();
            List<Integer> alert = new ArrayList<>();
            Query.prepare("SELECT * FROM `ALERTSTATUS` WHERE `UUID` = ? limit 1")
                    .append(uuid).execute(rs -> {
                alert.add(rs.getInt(2));
            });

            if (alert.isEmpty() || alert.get(0) == null) {
                setAlerts(uuid, 1);
                return true;
            }

            return MathUtil.getIntAsBoolean(alert.get(0));
        } catch (Exception exception) {
            setAlerts(uuid, 1);
            return true;
        }
    }

    @Override
    public void loadActiveViolations(String uuid, KarhuPlayer data) {
        MySQL.use();
        List<ViolationX> violations = new ArrayList<>();
        Map<String, Integer> validVls = new HashMap<>();
        Query.prepare("SELECT `MODULE`, `VL`, `TIME`, `EXTRA`, `COORDS`, `WORLD`, `PING`, `TPS` FROM `ALERTS` WHERE `UUID` = ? ORDER BY `TIME`")
                .append(uuid).execute(rs -> {
            violations.add(new ViolationX(uuid, rs.getString(1), rs.getInt(2), rs.getLong(3),
                    rs.getString(4), rs.getString(5), rs.getString(6), rs.getLong(7), rs.getDouble(8)));
        });

        for(ViolationX v : violations) {
            if(System.currentTimeMillis() - v.time < 200L * 1000L) {
                if(!validVls.containsKey(v.type)) {
                    validVls.put(v.type, v.vl);
                } else {
                    if(v.vl > validVls.get(v.type)) {
                        validVls.replace(v.type, v.vl);
                    }
                }
            }
        }

        for(Check c : data.getCheckManager().getChecks()) {
            if(validVls.containsKey(c.getName())) {
                data.addViolations(c, validVls.get(c.getName()));
                final int vl = data.getViolations(c, (100L * 1000));
                data.setCheckVl(vl, c);
            }
        }
    }

    @Override
    public List<ViolationX> getViolations(String uuid, Check type, int page, int limit, long from, long to) {
        MySQL.use();
        List<ViolationX> violations = new ArrayList<>();
        Query.prepare("SELECT `MODULE`, `VL`, `TIME`, `EXTRA`, `COORDS`, `WORLD`, `PING`, `TPS` FROM `ALERTS` WHERE `UUID` = ? ORDER BY `TIME` DESC LIMIT ?,?")
                .append(uuid).append(page * limit).append(limit).execute(rs -> {
            violations.add(new ViolationX(uuid, rs.getString(1), rs.getInt(2),
                    rs.getLong(3), rs.getString(4), rs.getString(5),
                    rs.getString(6), rs.getLong(7), rs.getDouble(8)));
        });
        return violations;
    }

    @Override
    public int getViolationAmount(String uuid) {
        MySQL.use();
        AtomicInteger violations = new AtomicInteger();
        Query.prepare("SELECT `MODULE`, `VL`, `TIME`, `EXTRA`, `COORDS`, `WORLD`, `PING`, `TPS` FROM `ALERTS` WHERE `UUID` = ? ORDER BY `TIME`")
                .append(uuid).execute(rs -> violations.incrementAndGet());
        return violations.get();
    }

    @Override
    public List<ViolationX> getAllViolations(String uuid) {
        MySQL.use();
        List<ViolationX> violations = new ArrayList<>();
        Query.prepare("SELECT `MODULE`, `VL`, `TIME`, `EXTRA`, `COORDS`, `WORLD`, `PING`, `TPS` FROM `ALERTS` WHERE `UUID` = ? ORDER BY `TIME`")
                .append(uuid).execute(rs -> {
            violations.add(new ViolationX(uuid, rs.getString(1), rs.getInt(2), rs.getLong(3),
                    rs.getString(4), rs.getString(5), rs.getString(6), rs.getLong(7), rs.getDouble(8)));
        });
        return violations;
    }

    @Override
    public int getAllViolationsInStorage() {
        List<ViolationX> violations = new ArrayList<>();
        Query.prepare("SELECT `MODULE`, `VL`, `TIME`, `EXTRA`, `COORDS`, `WORLD`, `PING`, `TPS` FROM `ALERTS` ORDER BY `TIME`")
                .execute(rs -> {
                    violations.add(new ViolationX("s", rs.getString(1), rs.getInt(2), rs.getLong(3),
                            rs.getString(4), rs.getString(5), rs.getString(6),  rs.getLong(7), rs.getDouble(8)));
                });
        return violations.size();
    }

    @Override
    public List<BanX> getRecentBans() {
        List<BanX> bans = new ArrayList<>();
        MySQL.use();
        Query.prepare("SELECT `UUID`, `MODULE`, `TIME`, `EXTRA`, `PING`, `TPS` FROM `BANS` ORDER BY `TIME`").execute(rs -> {
            bans.add(new BanX(rs.getString(1), rs.getString(2), rs.getLong(3),
                    rs.getString(4), rs.getLong(5), rs.getDouble(6)));
        });
        Collections.reverse(bans);
        return bans.subList(0, Math.min(bans.size(), 10));
    }

    @Override
    public void purge(String uuid, boolean all) {
        Query.prepare("DELETE FROM `ALERTS` WHERE UUID = ?").append(uuid).execute();
        Query.prepare("DELETE FROM `BANS` WHERE UUID = ?").append(uuid).execute();
    }

    @Override
    public List<String> getBanwaveList() {
        MySQL.use();
        List<String> players = new ArrayList<>();
        Query.prepare("SELECT `UUID` FROM `BANWAVE` ORDER BY `TIME`")
                .execute(rs -> {
                    players.add(rs.getString(1));
                });
        return players;
    }

    @Override
    public boolean isInBanwave(String uuid) {
        try {
            ResultSet rs = Query.prepare("SELECT `MODULE`, `TIME`, `TOTALLOGS` FROM `BANWAVE` WHERE `UUID` = ? ORDER BY `TIME` DESC LIMIT ?,?")
                    .append(uuid).executeQuery();
            return rs.first();
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override
    public void addToBanWave(BanWaveX bwRequest) {
        if(!this.isInBanwave(bwRequest.player)) {
            banWaveQueue.add(bwRequest);
        }
    }

    @Override
    public void removeFromBanWave(String uuid) {
        Optional<BanWaveX> bwx = banWaveQueue.stream().filter(bw -> bw.player.equals(uuid)).findFirst();
        bwx.ifPresent(banWaveX -> banWaveQueue.remove(banWaveX));
        Query.prepare("DELETE FROM `BANWAVE` WHERE UUID = ?").append(uuid).execute();
    }
}
