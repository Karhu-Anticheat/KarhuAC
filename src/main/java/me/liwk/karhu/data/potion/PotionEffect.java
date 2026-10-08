package me.liwk.karhu.data.potion;


import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PotionEffect {
    SPEED("Speed", 1),
    SLOWNESS("Slowness", 2),
    HASTE("Haste", 3),
    MINING_FATIGUE("Mining Fatigue", 4),
    STRENGTH("Strength", 5),
    INSTANT_HEALTH("Instant Health", 6),
    INSTANT_DAMAGE("Instant Damage", 7),
    JUMP_BOOST("Jump Boost", 8),
    NAUSEA("Nausea", 9),
    REGENERATION("Regeneration", 10),
    RESISTANCE("Resistance", 11),
    FIRE_RESISTANCE("Fire Resistance", 12),
    WATER_BREATHING("Water Breathing", 13),
    INVISIBILITY("Invisibility", 14),
    BLINDNESS("Blindness", 15),
    NIGHT_VISION("Night Vision", 16),
    HUNGER("Hunger", 17),
    WEAKNESS("Weakness", 18),
    POISON("Poison", 19),
    WITHER("Wither", 20),
    HEALTH_BOOST("Health Boost", 21),
    ABSORPTION("Absorption", 22),
    SATURATION("Saturation", 23),
    GLOWING("Glowing", 24),
    LEVITATION("Levitation", 25),
    LUCK("Luck", 26),
    BAD_LUCK("Bad Luck", 27),
    SLOW_FALLING("Slow Falling", 28),
    CONDUIT_POWER("Conduit Power", 29),
    DOLPHIN_GRACE("Dolphin Grace", 30),
    BAD_OMEN("Bad Omen", 31),
    HERO_OF_THE_VILLAGE("Hero of the Village", 32),
    DARKNESS("Darkness", 33),
    TRIAL_OMEN( "trial_omen", 34),
    RAID_OMEN("raid_omen", 35),
    WIND_CHARGED("wind_charged", 36),
    WEAVING("weaving", 37),
    OOZING("oozing", 38),
    INFESTED("infested", 39),
    BREATH_OF_THE_NAUTILUS("breath_of_the_nautilus", 40);

    private final String name;
    // Could use ordinal() but getId() is more explicit
    private final int id;
}
