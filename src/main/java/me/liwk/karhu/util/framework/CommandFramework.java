package me.liwk.karhu.util.framework;

import me.liwk.karhu.Karhu;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.help.GenericCommandHelpTopic;
import org.bukkit.help.HelpTopic;
import org.bukkit.help.HelpTopicComparator;
import org.bukkit.help.IndexHelpTopic;
import org.bukkit.plugin.SimplePluginManager;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.Map.Entry;

public class CommandFramework implements CommandExecutor {

    private Map<String, Entry<Method, Object>> commandMap = new HashMap<>();
    private CommandMap map;
    private String newAliases;
    private Karhu plugin;
    private List<File> files = new ArrayList<>();

    public CommandFramework(Karhu plugin) {
        this.plugin = plugin;

        if (plugin.getServer().getPluginManager() instanceof SimplePluginManager) {
            SimplePluginManager manager = (SimplePluginManager) plugin.getServer().getPluginManager();
            try {
                Field field = SimplePluginManager.class.getDeclaredField("commandMap");
                field.setAccessible(true);
                map = (CommandMap) field.get(manager);
            } catch (IllegalArgumentException | SecurityException | NoSuchFieldException | IllegalAccessException e) {
                Karhu.getInstance().printCool("&b> &cCommandMap not found, couldn't add commands");
            }
        }

    }

    @Override
    public boolean onCommand(CommandSender sender, org.bukkit.command.Command cmd, String label, String[] args) {
        return handleCommand(sender, cmd, label, args);
    }

    public boolean handleCommand(CommandSender sender, org.bukkit.command.Command cmd, String label, String[] args) {
        for (int i = args.length; i >= 0; i--) {
            StringBuilder buffer = new StringBuilder();
            buffer.append(label.toLowerCase());

            for (int x = 0; x < i; x++) {
                buffer.append(".").append(args[x].toLowerCase());
            }

            String cmdLabel = buffer.toString();


            if (commandMap.containsKey(cmdLabel)) {


                Method method = commandMap.get(cmdLabel).getKey();
                Object methodObject = commandMap.get(cmdLabel).getValue();
                Command command = method.getAnnotation(Command.class);

                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    if (!player.hasPermission("karhu.staff") && !player.isOp()) {
                        if(cmdLabel.equalsIgnoreCase("karhu")) {
                            sender.sendMessage(getUnknownCommandMessage());
                        } else {
                            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', Karhu.getInstance().getConfigManager().getNoPermission()));
                        }
                        return true;
                    }
                }

                try {
                    method.invoke(methodObject, new CommandArgs(sender, cmd, label, args,
                            cmdLabel.split("\\.").length - 1));
                } catch (IllegalArgumentException | IllegalAccessException | InvocationTargetException ignored) {
                }

                return true;
            }
        }

        defaultCommand(new CommandArgs(sender, cmd, label, args, 0));
        return true;
    }


    public void registerCommands(Object obj) {
        for (Method m : obj.getClass().getMethods()) {
            if (m.getAnnotation(Command.class) != null) {
                Command command = m.getAnnotation(Command.class);

                if (m.getParameterTypes().length > 1 || m.getParameterTypes()[0] != CommandArgs.class) {
                    continue;
                }

                String commandName = command.name();

                if(commandName.equalsIgnoreCase("karhu")) {
                    if (Karhu.getInstance().getConfigManager().getName().equalsIgnoreCase("vengeance")) {
                        commandName = "vengeance";
                    }
                }

                registerCommand(command, commandName, m, obj);

                if(Karhu.getInstance().getConfigManager().getName().equalsIgnoreCase("karhu") || !command.name().equalsIgnoreCase("karhu")) {
                    return;
                }

                if(command.aliases().length < 1) {
                    newAliases = Karhu.getInstance().getConfigManager().getName().toLowerCase();
                }

                registerCommand(command, newAliases, m, obj);
            }
        }
    }

    public void registerHelp() {
        Set<HelpTopic> help = new TreeSet<>(HelpTopicComparator.helpTopicComparatorInstance());

        commandMap.keySet().stream().filter((s) -> (!s.contains("."))).map((s) -> map.getCommand(s)).map((cmd) -> new GenericCommandHelpTopic(cmd)).forEachOrdered((topic) -> {
            help.add(topic);
        });

        IndexHelpTopic topic = new IndexHelpTopic(plugin.getName(), "All commands for " + plugin.getName(), null, help, "Below is a list of all " + plugin.getName() + " commands:");
        Bukkit.getServer().getHelpMap().addTopic(topic);
    }

    public void unregisterCommands(Object obj) {
        for (Method m : obj.getClass().getMethods()) {
            if (m.getAnnotation(Command.class) != null) {
                Command command = m.getAnnotation(Command.class);
                commandMap.remove(command.name().toLowerCase());
                commandMap.remove(this.plugin.getName() + ":" + command.name().toLowerCase());
                map.getCommand(command.name().toLowerCase()).unregister(map);
            }
        }
    }

    public void registerCommand(Command command, String label, Method m, Object obj) {
        commandMap.put(label.toLowerCase(), new AbstractMap.SimpleEntry<>(m, obj));
        commandMap.put(this.plugin.getName() + ':' + label.toLowerCase(), new AbstractMap.SimpleEntry<>(m, obj));
        String cmdLabel = label.replace(".", ",").split(",")[0].toLowerCase();

        if (map.getCommand(cmdLabel) == null) {
            org.bukkit.command.Command cmd = new BukkitCommand(cmdLabel, this, plugin);
            map.register(plugin.getName(), cmd);
        }

        if (!command.description().isEmpty() && cmdLabel.equals(label)) {
            map.getCommand(cmdLabel).setDescription(command.description());
        }

        if (!command.usage().isEmpty() && cmdLabel.equals(label)) {
            map.getCommand(cmdLabel).setUsage(command.usage());
        }

        CommandManager1_19.syncCommand();

    }

    private void defaultCommand(CommandArgs args) {
        args.getSender().sendMessage("Unknown Command.");
    }

    boolean deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        return directoryToBeDeleted.delete();
    }

    private static String getUnknownCommandMessage() {
        // SpigotConfig is server-side only, so resolve it reflectively to avoid a compile-time dependency on the server jar
        try {
            return (String) Class.forName("org.spigotmc.SpigotConfig").getField("unknownCommandMessage").get(null);
        } catch (ReflectiveOperationException | ClassCastException e) {
            return "Unknown command. Type \"/help\" for help.";
        }
    }
}
