package me.liwk.karhu.database.mysql;

import me.liwk.karhu.Karhu;
import me.liwk.karhu.database.Query;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.Connection;
import java.sql.DriverManager;

public class MySQL {
    private static Connection conn;

    public static void init() {
        try {
            if (conn == null || conn.isClosed()) {
                loadDriver();
                FileConfiguration config = Karhu.getInstance().getConfigManager().getConfig();
                conn = DriverManager.getConnection("jdbc:mysql://" + config.getString("mysql.address") + ":" + config.getString("mysql.port") + "/?useSSL=false",
                        config.getString("mysql.user"),
                        config.getString("mysql.password"));
                conn.setAutoCommit(true);

                Query.use(conn);
                Query.prepare("CREATE DATABASE IF NOT EXISTS `" + config.getString("mysql.database") + "`").execute();
                Query.prepare("USE `" + config.getString("mysql.database") + "`").execute();
                Karhu.getInstance().printCool("&b> &aConnection to MySQL has been established.");
            }
        } catch (Exception e) {
            Karhu.getInstance().printCool("&b> &cConnection to MySQL has failed.");
            e.printStackTrace();
        }
    }

    private static void loadDriver() throws ClassNotFoundException {
        try {
            // Connector/J 8+, bundled with recent Spigot/Paper builds
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            Class.forName("com.mysql.jdbc.Driver");
        }
    }

    public static void use() {
        try {
            init();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
