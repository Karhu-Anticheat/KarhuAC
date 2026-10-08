package me.liwk.karhu;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import dev.thomazz.pledge.Pledge;
import dev.thomazz.pledge.pinger.ClientPinger;
import dev.thomazz.pledge.pinger.ClientPingerOptions;
import io.github.retrooper.packetevents.adventure.serializer.legacy.LegacyComponentSerializer;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import lombok.Getter;
import me.liwk.karhu.api.check.CheckState;
import me.liwk.karhu.command.CommandAPI;
import me.liwk.karhu.command.sub.AlertsCommand;
import me.liwk.karhu.command.sub.KarhuCommand;
import me.liwk.karhu.command.sub.LogsCommand;
import me.liwk.karhu.database.Storage;
import me.liwk.karhu.database.mongo.MongoStorage;
import me.liwk.karhu.database.mysql.MySQL;
import me.liwk.karhu.database.mysql.MySQLStorage;
import me.liwk.karhu.database.sqlite.LocalStorage;
import me.liwk.karhu.handler.global.PacketProcessor;
import me.liwk.karhu.handler.global.TransactionHandler;
import me.liwk.karhu.handler.global.bukkit.*;
import me.liwk.karhu.manager.ConfigManager;
import me.liwk.karhu.manager.PlayerDataManager;
import me.liwk.karhu.manager.WaveManager;
import me.liwk.karhu.manager.alert.AlertsManager;
import me.liwk.karhu.replay.view.ReplayManager;
import me.liwk.karhu.replay.view.ReplayViewerManager;
import me.liwk.karhu.util.APICaller;
import me.liwk.karhu.util.MathUtil;
import me.liwk.karhu.util.Metrics;
import me.liwk.karhu.util.benchmark.KarhuBenchmarker;
import me.liwk.karhu.util.framework.CommandFramework;
import me.liwk.karhu.util.framework.CommandManager1_19;
import me.liwk.karhu.util.task.Tasker;
import me.liwk.karhu.util.thread.KarhuThreadManager;
import me.liwk.karhu.util.thread.ThreadManager;
import me.liwk.karhu.world.chunk.ChunkListeners;
import me.liwk.karhu.world.chunk.IChunkManager;
import me.liwk.karhu.world.chunk.WorldChunkManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

public final class Karhu extends JavaPlugin {

    @Getter
    private static Karhu instance;

    @Getter
    private IChunkManager chunkManager;

    @Getter
    private final String version = "2.7.15";

    @Getter
    private final String build = "268";

    @Getter
    private boolean isViaRewind, isViaVersion, isProtocolSupport, isFloodgate;

    @Getter
    private ExecutorService alertsThread, discordThread, packetThread, statsThread;

    @Getter
    public static Storage storage;

    @Getter
    private ConfigManager configManager;

    @Getter
    private CheckState checkState;

    @Getter
    private PlayerDataManager dataManager;

    @Getter
    private AlertsManager alertsManager;

    @Getter
    private CommandFramework framework;

    @Getter
    private CommandManager1_19 commandManager;

    @Getter
    private ReplayManager replayManager;

    @Getter
    private ReplayViewerManager viewerManager;

    @Getter
    public static ServerVersion SERVER_VERSION;

    @Getter
    public static boolean PING_PONG_MODE;

    private double tps;

    @Getter
    public long tpsMilliseconds, ticks, lastTimeStamp, lastTick,
            lastPerformanceDrop, lastPerformanceAnnounce;

    @Getter
    private long serverTick;

    private static Boolean apiAvailability = null;

    @Getter
    public static boolean crackedServer = false;

    @Getter
    public ThreadManager threadManager;

    @Getter
    private Pledge pledge;

    public static double DIVISOR = 32.0D;

    @Getter
    private TransactionHandler transactionHandler;

    @Getter
    private WaveManager waveManager;

    @Getter
    private String bungeeChannel = "karhu:proxy";

    @Getter
    private Metrics metrics = null;

    @Getter
    private final LegacyComponentSerializer componentSerializer =
            LegacyComponentSerializer.builder()
                    .character(LegacyComponentSerializer.AMPERSAND_CHAR)
                    .hexCharacter(LegacyComponentSerializer.HEX_CHAR)
                    .build();

    @Override
    public void onEnable() {
        instance = this;

        final List<String> no = new ArrayList<>();

        no.add(" _  __          _              ");
        no.add("| |/ /         | |             ");
        no.add("| ' / __ _ _ __| |__  _   _    ");
        no.add("|  < / _` | '__| '_ \\| | | |       Version: "
                + this.getVersion() + " | " + this.getBuild());
        no.add("| . \\ (_| | |  | | | | |_| |  ");
        no.add("|_|\\_\\__,_|_|  |_| |_|\\__,_|");

        no.forEach(msg -> printCool(ChatColor.BLUE + msg));
        no.clear();

        final long start = System.currentTimeMillis();

        File libs = new File(
                getDataFolder().getAbsolutePath()
                        + File.separator
                        + "libs"
                        + File.separator
        );

        if (libs.mkdir()) {
            printCool(ChatColor.GREEN + "Folder "
                    + libs.getAbsolutePath() + " created!");
        } else {
            printCool(ChatColor.RED + "Folder "
                    + libs.getAbsolutePath()
                    + " failed to create, maybe it's already there!");
        }

        this.threadManager = new ThreadManager();
        this.chunkManager = new WorldChunkManager();
        this.waveManager = new WaveManager();
        this.transactionHandler = new TransactionHandler();
        this.replayManager = new ReplayManager(this);
        this.viewerManager = new ReplayViewerManager(this);

        KarhuBenchmarker.registerProfiles();

        Tasker.load(this);

        this.packetThread =
                KarhuThreadManager.createNewNormalExecutor("karhu-packet-thread");

        this.alertsThread =
                KarhuThreadManager.createNewNormalExecutor("karhu-alert-thread");

        this.discordThread =
                KarhuThreadManager.createNewExecutor("karhu-discord-thread");

        this.statsThread =
                KarhuThreadManager.createNewExecutor(2, "karhu-stats-thread");

        printCool("&b> &fThreads initialized");

        initPockets();

        this.checkState = new CheckState();
        this.checkState.loadOrGetClasses();

        this.dataManager = new PlayerDataManager(this);
        this.configManager = new ConfigManager(this);

        printCool("&b> &fPacketEvents settings setup");

        Bukkit.getMessenger().registerOutgoingPluginChannel(
                this,
                bungeeChannel
        );

        PacketEvents.getAPI()
                .getEventManager()
                .registerListener(new PacketProcessor(this));

        printCool("&b> &fPacketEvents loaded " + SERVER_VERSION);

        registerBukkitListeners();

        printCool("&b> &fEvents initialized");

        this.framework = new CommandFramework(this);
        this.commandManager = new CommandManager1_19(this);
        this.alertsManager = new AlertsManager();

        printCool("&b> &fManagers initialized");

        registerCommands();

        printCool("&b> &fCommands initialized");

        PING_PONG_MODE =
                SERVER_VERSION.isNewerThanOrEquals(ServerVersion.V_1_17);

        DIVISOR =
                SERVER_VERSION.getProtocolVersion() <= 47
                        ? 32.0D
                        : 4098.0D;

        /*
         * Pledge
         */
        this.pledge = Pledge.getOrCreate(this);

        ClientPingerOptions.ClientPingerOptionsBuilder options =
                ClientPingerOptions.builder();

        options.startId(-3000);
        options.endId(-20000);
        options.consolidatePackets(
                configManager.isPledgeConsolidatePackets()
        );

        ClientPinger pinger =
                this.pledge.createPinger(options.build());

        pinger.attach(new PledgeListener());

        /*
         * Storage
         */
        switch (configManager.getConfig()
                .getString("database")
                .toLowerCase()) {

            case "mongodb":
            case "mongo":
                storage = new MongoStorage();
                printCool("&b> &fMongo initialized");
                break;

            case "mysql":
                MySQL.init();
                storage = new MySQLStorage();
                printCool("&b> &fMySQL initialized");
                break;

            default:
                storage = new LocalStorage();
                printCool("&b> &fSQLite initialized");
                break;
        }

        storage.init();

        /*
         * Chunk loading
         */
        Tasker.run(() -> {
            printCool("&b> &fStarting world chunk load...");

            long startTime = System.currentTimeMillis();

            AtomicInteger chunkAmountServer = new AtomicInteger();
            AtomicInteger chunkAmountCache = new AtomicInteger();

            Bukkit.getWorlds().forEach(world -> {

                this.chunkManager.addWorld(world);

                Chunk[] array = world.getLoadedChunks();

                printCool("&b> &fChunkManager is going to cache "
                        + array.length
                        + " chunks from world "
                        + world.getName());

                if (SERVER_VERSION.getProtocolVersion() >= 47) {

                    for (Chunk chunk : array) {
                        this.chunkManager.onChunkLoad(chunk);
                    }

                } else {

                    int size = Math.min(array.length, 32);

                    for (int i = 0; i < size; i++) {
                        this.chunkManager.onChunkLoad(array[i]);
                    }
                }

                printCool("&b> &fChunkManager cached "
                        + array.length
                        + " chunks from world "
                        + world.getName());

                chunkAmountServer.addAndGet(array.length);
                chunkAmountCache.addAndGet(
                        this.chunkManager.getCacheSize(world)
                );
            });

            long finishedAt = System.currentTimeMillis();

            printCool("&b> &fFinished chunk load in "
                    + (finishedAt - startTime) + "ms");

            printCool("&b> &fYour server had &b"
                    + chunkAmountServer.get()
                    + " &fchunks pre-loaded, karhu cached &b"
                    + chunkAmountCache.get()
                    + " &fchunks");
        });

        runTicks();

        printCool("&b> &fTPS counter & Tick handler initialized");

        Plugin via =
                Bukkit.getPluginManager().getPlugin("ViaVersion");

        isFloodgate =
                Bukkit.getPluginManager().getPlugin("floodgate") != null;

        isViaRewind =
                Bukkit.getPluginManager().getPlugin("ViaRewind") != null;

        isViaVersion = via != null;

        isProtocolSupport =
                Bukkit.getPluginManager().getPlugin("ProtocolSupport") != null;

        if (!isFloodgate && configManager.isGeyserSupport()) {
            printCool(ChatColor.DARK_RED
                    + "Geyser support is enabled, but floodgate plugin was not found");
        }

        if (isFloodgate && !configManager.isGeyserSupport()) {
            printCool(ChatColor.DARK_RED
                    + "Floodgate was found, but geyser support is disabled in config");
        }

        loadServerProperties();

        this.metrics = new Metrics(this, 11204);

        Tasker.taskAsync(() -> waveManager.importFromDb());

        /*
         * ProtocolLib check
         */
        Plugin plib = Bukkit.getPluginManager().getPlugin("ProtocolLib");

        if (plib != null && via != null) {

            if (!plib.getDescription()
                    .getVersion()
                    .startsWith("5")) {

                printCool("&b> &cThis version of ProtocolLib doesn't support Karhu");

                Bukkit.getServer()
                        .getScheduler()
                        .cancelTasks(this);

                printCool("&b> &cCritical error occurred contact support (DEBUG: 55)");

                Bukkit.getPluginManager().disablePlugin(this);
                return;
            }
        }

        final long loadMs =
                System.currentTimeMillis() - start;

        if (isAPIAvailable()) {
            APICaller.callInit(loadMs);
        }

        printCool(ChatColor.DARK_GREEN
                + "Finished loading in "
                + loadMs
                + "ms");
    }

    @Override
    public void onDisable() {
        Tasker.stop();

        try {
            PacketEvents.getAPI().terminate();
        } catch (Exception ignored) {
        }

        KarhuThreadManager.shutdown();

        if (chunkManager != null) {
            chunkManager.unloadAll();
        }

        if (dataManager != null) {
            dataManager.getPlayerDataMap().clear();
        }

        if (checkState != null) {
            checkState.getCheckClasses().clear();
            checkState.getAutobanMap().clear();
            checkState.getEnabledMap().clear();
            checkState.getVlMap().clear();
        }

        if (metrics != null) {
            metrics.shutdown();
        }

        if (pledge != null) {
            pledge.destroy();
        }

        instance = null;
    }

    private void registerBukkitListeners() {
        Bukkit.getPluginManager()
                .registerEvents(new BukkitHandler(), this);

        Bukkit.getPluginManager()
                .registerEvents(new InventoryHandler(), this);

        Bukkit.getPluginManager()
                .registerEvents(new NoLookBreakListener(), this);

        Bukkit.getPluginManager()
                .registerEvents(new BlockReachListener(), this);

        Bukkit.getPluginManager()
                .registerEvents(new ChunkListeners(), this);

        Bukkit.getPluginManager()
                .registerEvents(new PlayerVelocityHandler(), this);
    }

    private void registerCommands() {
        new CommandAPI(framework);
        new KarhuCommand(framework);
        new AlertsCommand(framework);
        new LogsCommand(framework);
    }

    public void runTicks() {
        new BukkitRunnable() {

            int ticks;

            @Override
            public void run() {
                long nano = System.nanoTime() / 1_000_000L;
                long timeStamp = System.currentTimeMillis();

                if (isServerLagging(timeStamp)) {
                    lastPerformanceDrop = timeStamp;
                }

                if (serverTick == Long.MAX_VALUE) {
                    serverTick = 0;
                }

                ++serverTick;
                ticks++;

                if (ticks >= 20) {
                    tpsMilliseconds = nano - lastTimeStamp;

                    if (tpsMilliseconds > 0) {
                        tps = 1000D / tpsMilliseconds * 20;
                    }

                    lastTimeStamp = nano;
                    ticks = 0;
                }

                if (dataManager != null) {
                    dataManager.getPlayerDataMap()
                            .forEach((uuid, karhuPlayer) -> {

                                if (karhuPlayer.isForceRunCollisions()) {
                                    karhuPlayer.getCollisionHandler()
                                            .cacheBlocks();
                                }
                            });
                }

                lastTick = timeStamp;
            }

        }.runTaskTimer(this, 0L, 1L);
    }

    public static boolean isAPIAvailable() {
        return apiAvailability == null
                ? (apiAvailability =
                Bukkit.getPluginManager()
                        .isPluginEnabled("KarhuAPI"))
                : apiAvailability;
    }

    public boolean isServerLagging(long time) {
        return tps < 19.6
                || (time - lastTick) > configManager.getMaxTickLenght();
    }

    public boolean hasRecentlyDropped(long time) {
        return (System.currentTimeMillis() - lastPerformanceDrop) <= time;
    }

    public double getTPS() {
        return Math.min(MathUtil.round(tps, 2), 20.0);
    }

    public void printCool(String text) {
        Bukkit.getConsoleSender().sendMessage(
                ChatColor.translateAlternateColorCodes('&', text)
        );
    }

    public void initPockets() {
        PacketEvents.setAPI(
                SpigotPacketEventsBuilder.build(this)
        );

        PacketEvents.getAPI().load();

        SERVER_VERSION =
                PacketEvents.getAPI()
                        .getServerManager()
                        .getVersion();
    }

    private void loadServerProperties() {
        try {
            File file = new File("server.properties");

            if (!file.exists()) {
                file = new File(
                        getDataFolder()
                                .getParentFile()
                                .getParentFile(),
                        "server.properties"
                );
            }

            if (!file.exists()) {
                crackedServer = false;
                KarhuLogger.critical(
                        "Couldn't find server.properties, cracked server is set to false."
                );
                return;
            }

            try (BufferedReader reader =
                         new BufferedReader(new FileReader(file))) {

                Properties props = new Properties();
                props.load(reader);

                crackedServer =
                        Boolean.parseBoolean(
                                props.getProperty("online-mode")
                        );
            }

        } catch (IOException e) {
            crackedServer = false;

            KarhuLogger.critical(
                    "Couldn't read server.properties, cracked server is set to false."
            );
        }
    }
}