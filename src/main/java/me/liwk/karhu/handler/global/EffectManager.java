package me.liwk.karhu.handler.global;

import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.liwk.karhu.data.KarhuPlayer;
import me.liwk.karhu.data.potion.PotionData;
import me.liwk.karhu.data.potion.PotionEffect;
import org.bukkit.Bukkit;

import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
public class EffectManager {

    private final KarhuPlayer data;

    @Getter
    private final Map<Integer, PotionData> effects = new HashMap<>();

    public void addPotionEffect(int id, int amp) {
        int idFromValues = data.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_20_2) ? id : id - 1;
        int idForMap = data.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_20_2) ? id + 1 : id;
        PotionEffect[] values = PotionEffect.values();
        // Effects added in newer Minecraft versions are unknown here, ignore them instead of throwing
        if (idFromValues < 0 || idFromValues >= values.length) return;
        PotionEffect potionEffect = values[idFromValues];

        effects.put(idForMap, new PotionData(potionEffect, amp));
    }

    public void removePotionEffect(int id) {
        int idForMap = data.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_20_2) ? id + 1 : id;
        effects.remove(idForMap);
    }
    public PotionData getEffect(PotionEffect potionEffect) {
        return effects.get(potionEffect.getId());
    }

    public int getEffectStrenght(PotionEffect potionEffect) {
        if(!hasEffect(potionEffect)) return 0;
        return effects.get(potionEffect.getId()).getAmplifier();
    }

    public boolean hasEffect(PotionEffect potionEffect) {
        return effects.get(potionEffect.getId()) != null;
    }

}
