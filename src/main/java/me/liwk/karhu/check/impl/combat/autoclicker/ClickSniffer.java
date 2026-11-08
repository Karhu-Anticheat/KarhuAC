package me.liwk.karhu.check.impl.combat.autoclicker;

import me.liwk.karhu.Karhu;
import me.liwk.karhu.api.check.CheckInfo;
import me.liwk.karhu.api.check.Category;
import me.liwk.karhu.api.check.SubCategory;
import me.liwk.karhu.check.type.PacketCheck;
import me.liwk.karhu.data.KarhuPlayer;
import me.liwk.karhu.event.Event;
import me.liwk.karhu.event.FlyingEvent;
import me.liwk.karhu.event.SwingEvent;
import me.liwk.karhu.util.MathUtil;
import org.apache.commons.math3.stat.descriptive.moment.Kurtosis;
import org.apache.commons.math3.stat.descriptive.moment.Skewness;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.apache.commons.math3.stat.descriptive.summary.Product;
import org.apache.commons.math3.util.FastMath;
import org.bukkit.Bukkit;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

import static me.liwk.karhu.util.MathUtil.*;

@CheckInfo(name = "ClickerDebugger", category = Category.COMBAT, subCategory = SubCategory.AUTOCLICKER, experimental = true, silent = true)
public final class ClickSniffer extends PacketCheck {

    int flying;
    double lastSTD;
    Deque<Integer> samples = new ArrayDeque<>();

    public ClickSniffer(KarhuPlayer data, Karhu karhu) { //Had to change name because of epic terminating lithium
        super(data, karhu);
    }

    @Override
    public void handle(final Event packet) {
        if (packet instanceof SwingEvent) {
            if (flying <= 5 && !data.isHasDig() && !data.isPlacing() && !data.isUsingItem()) samples.add(flying);
            if (samples.size() == 250) {
                int outliers = getOutliers(samples);
                int w = getW(samples);

                double kur = new Kurtosis().evaluate(MathUtil.dequeTranslator(samples));
                double ske = new Skewness().evaluate(MathUtil.dequeTranslator(samples));

                double ratio = getRatio(samples);

                double std = new StandardDeviation().evaluate(MathUtil.dequeTranslator(samples));
                double product = new Product().evaluate(MathUtil.dequeTranslator(samples)) * FastMath.pow(2, -10);

                double cps = 20.0 / average(samples);
                int duplicates = MathUtil.getRepeated(samples);

                double entropy = getEntropy(samples);

                double sdd = Math.abs(std - lastSTD);

                karhu.getAlertsManager().getDebugToggled().stream().map(Bukkit::getPlayer).filter(Objects::nonNull).forEach(admin -> {
                    admin.sendMessage("§c§m-§b§m-§9§7Sniffed §n" + data.getName() + "§c§m-§b§m-§9");
                    admin.sendMessage("§cOutliers/RAT: §f" + outliers + " | " + ratio);
                    admin.sendMessage("§cW/E: §f" + w + " | " + entropy);
                    admin.sendMessage("§cKU/SK: §f" + kur + " | " + ske);
                    admin.sendMessage("§cSTD/SDD: §f" + std + " | " + sdd);
                    admin.sendMessage("§cPROD: §f" + product);
                    admin.sendMessage("§cCPS/DUP: §f" + cps + " | " + duplicates);
                });

                samples.clear();
                lastSTD = std;

            }

            flying = 0;
        } else if (packet instanceof FlyingEvent) {
            if(!((FlyingEvent) packet).isTeleport()) {
                ++flying;
            }
        }
    }
}
