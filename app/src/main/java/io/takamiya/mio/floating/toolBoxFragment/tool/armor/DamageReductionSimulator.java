package io.takamiya.mio.floating.toolBoxFragment.tool.armor;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class DamageReductionSimulator {

    private DamageReductionSimulator() {}

    public enum DamageType {
        ALL_EXCEPT_LISTED("普通伤害", true, true, false, false, false),
        HUNGER_VOID_KILL("饥饿/虚空/kill", false, false, false, false, false),
        WARDEN_RANGED("监守者远程攻击", false, false, false, false, false),
        SUFFOCATE_DROWN_MAGIC("窒息/溺水/魔法/动能/冰冻", false, true, false, false, false),
        ON_FIRE("着火", false, true, true, false, false),
        LAVA_MAGMA_FIREBALL("熔岩/岩浆块/小火球", false, true, true, false, false),
        EXPLOSION("爆炸", true, true, false, true, false),
        PROJECTILE("弹射物", true, true, false, false, true),
        FALL("摔落", false, true, false, false, false);

        private final String chineseName;
        private final boolean armorAffected;
        private final boolean protectionAffected;
        private final boolean fireAffected;
        private final boolean blastAffected;
        private final boolean projectileAffected;

        DamageType(String chineseName, boolean armorAffected, boolean protectionAffected,
                   boolean fireAffected, boolean blastAffected, boolean projectileAffected) {
            this.chineseName = chineseName;
            this.armorAffected = armorAffected;
            this.protectionAffected = protectionAffected;
            this.fireAffected = fireAffected;
            this.blastAffected = blastAffected;
            this.projectileAffected = projectileAffected;
        }

        public String getChineseName() { return chineseName; }
        public boolean isArmorAffected() { return armorAffected; }
        public boolean isProtectionAffected() { return protectionAffected; }
        public boolean isFireAffected() { return fireAffected; }
        public boolean isBlastAffected() { return blastAffected; }
        public boolean isProjectileAffected() { return projectileAffected; }
    }

    public enum EnchantmentType {
        PROTECTION("保护", 1),
        FIRE_PROTECTION("火焰保护", 2),
        BLAST_PROTECTION("爆炸保护", 2),
        PROJECTILE_PROTECTION("弹射物保护", 2),
        FEATHER_FALLING("摔落缓冲", 3);

        private final String chineseName;
        private final int epfPerLevel;

        EnchantmentType(String chineseName, int epfPerLevel) {
            this.chineseName = chineseName;
            this.epfPerLevel = epfPerLevel;
        }

        public String getChineseName() { return chineseName; }
        public int epfPerLevel() { return epfPerLevel; }
    }

    public enum ArmorSlot {
        HELMET("头盔"),
        CHESTPLATE("胸甲"),
        LEGGINGS("护腿"),
        BOOTS("靴子");

        private final String chineseName;
        ArmorSlot(String chineseName) { this.chineseName = chineseName; }
        public String getChineseName() { return chineseName; }
    }

    public static final class Result {
        private final double rawDamage;
        private final DamageType damageType;
        private final int armor;
        private final double toughness;
        private final int breach;
        private final int enchantEpf;
        private final int resistanceLevel;
        private final double damageAfterArmor;
        private final double armorReductionPct;
        private final double damageAfterEnchant;
        private final double enchantReductionPct;
        private final double damageAfterResistance;
        private final double resistanceReductionPct;
        private final double finalDamage;
        private final double totalReductionPct;

        Result(double rawDamage, DamageType damageType,
               int armor, double toughness, int breach, int enchantEpf, int resistanceLevel,
               double damageAfterArmor, double armorReductionPct,
               double damageAfterEnchant, double enchantReductionPct,
               double damageAfterResistance, double resistanceReductionPct,
               double finalDamage, double totalReductionPct) {
            this.rawDamage = rawDamage;
            this.damageType = damageType;
            this.armor = armor;
            this.toughness = toughness;
            this.breach = breach;
            this.enchantEpf = enchantEpf;
            this.resistanceLevel = resistanceLevel;
            this.damageAfterArmor = damageAfterArmor;
            this.armorReductionPct = armorReductionPct;
            this.damageAfterEnchant = damageAfterEnchant;
            this.enchantReductionPct = enchantReductionPct;
            this.damageAfterResistance = damageAfterResistance;
            this.resistanceReductionPct = resistanceReductionPct;
            this.finalDamage = finalDamage;
            this.totalReductionPct = totalReductionPct;
        }

        public double rawDamage() { return rawDamage; }
        public DamageType damageType() { return damageType; }
        public int armor() { return armor; }
        public double toughness() { return toughness; }
        public int breach() { return breach; }
        public int enchantEpf() { return enchantEpf; }
        public int resistanceLevel() { return resistanceLevel; }
        public double damageAfterArmor() { return damageAfterArmor; }
        public double armorReductionPct() { return armorReductionPct; }
        public double damageAfterEnchant() { return damageAfterEnchant; }
        public double enchantReductionPct() { return enchantReductionPct; }
        public double damageAfterResistance() { return damageAfterResistance; }
        public double resistanceReductionPct() { return resistanceReductionPct; }
        public double finalDamage() { return finalDamage; }
        public double totalReductionPct() { return totalReductionPct; }
    }

    public static final class ArmorMaterial {
        private final String name;
        private final int helmetArmor;
        private final int chestplateArmor;
        private final int leggingsArmor;
        private final int bootsArmor;
        private final int helmetToughness;
        private final int chestplateToughness;
        private final int leggingsToughness;
        private final int bootsToughness;

        public ArmorMaterial(String name,
                             int helmetArmor, int chestplateArmor, int leggingsArmor, int bootsArmor,
                             int helmetToughness, int chestplateToughness, int leggingsToughness, int bootsToughness) {
            this.name = name;
            this.helmetArmor = helmetArmor;
            this.chestplateArmor = chestplateArmor;
            this.leggingsArmor = leggingsArmor;
            this.bootsArmor = bootsArmor;
            this.helmetToughness = helmetToughness;
            this.chestplateToughness = chestplateToughness;
            this.leggingsToughness = leggingsToughness;
            this.bootsToughness = bootsToughness;
        }

        public String name() { return name; }
        public int armorSum() { return helmetArmor + chestplateArmor + leggingsArmor + bootsArmor; }
        public int toughnessSum() { return helmetToughness + chestplateToughness + leggingsToughness + bootsToughness; }

        public int slotArmor(ArmorSlot slot) {
            switch (slot) {
                case HELMET: return helmetArmor;
                case CHESTPLATE: return chestplateArmor;
                case LEGGINGS: return leggingsArmor;
                case BOOTS: return bootsArmor;
                default: return 0;
            }
        }

        public int slotToughness(ArmorSlot slot) {
            switch (slot) {
                case HELMET: return helmetToughness;
                case CHESTPLATE: return chestplateToughness;
                case LEGGINGS: return leggingsToughness;
                case BOOTS: return bootsToughness;
                default: return 0;
            }
        }

        public static final ArmorMaterial NONE = new ArmorMaterial("无", 0,0,0,0, 0,0,0,0);
        public static final ArmorMaterial LEATHER = new ArmorMaterial("皮革", 1,3,2,1, 0,0,0,0);
        public static final ArmorMaterial COPPER = new ArmorMaterial("铜", 2,4,3,1, 0,0,0,0);
        public static final ArmorMaterial GOLD = new ArmorMaterial("金", 2,5,3,1, 0,0,0,0);
        public static final ArmorMaterial CHAINMAIL = new ArmorMaterial("锁链", 2,5,4,1, 0,0,0,0);
        public static final ArmorMaterial IRON = new ArmorMaterial("铁", 2,6,5,2, 0,0,0,0);
        public static final ArmorMaterial DIAMOND = new ArmorMaterial("钻石", 3,8,6,3, 2,2,2,2);
        public static final ArmorMaterial NETHERITE = new ArmorMaterial("下界合金", 3,8,6,3, 3,3,3,3);
        public static final ArmorMaterial TURTLE = new ArmorMaterial("海龟壳", 2,0,0,0, 0,0,0,0);

        private static final Map<String, ArmorMaterial> BY_NAME;
        static {
            Map<String, ArmorMaterial> m = new HashMap<>();
            m.put("无", NONE);
            m.put("皮革", LEATHER);
            m.put("铜", COPPER);
            m.put("金", GOLD);
            m.put("锁链", CHAINMAIL);
            m.put("铁", IRON);
            m.put("钻石", DIAMOND);
            m.put("下界合金", NETHERITE);
            m.put("海龟壳", TURTLE);
            BY_NAME = Collections.unmodifiableMap(m);
        }

        public static ArmorMaterial byName(String name) {
            ArmorMaterial mat = BY_NAME.get(name);
            return mat != null ? mat : NONE;
        }
    }

    public static Set<EnchantmentType> getApplicableEnchantments(DamageType damageType) {
        Set<EnchantmentType> set = EnumSet.noneOf(EnchantmentType.class);
        for (EnchantmentType e : EnchantmentType.values()) {
            if (isApplicable(e, damageType)) set.add(e);
        }
        return set;
    }

    public static Result calculate(
            double rawDamage, DamageType damageType,
            int armor, double toughness, int breach,
            Map<EnchantmentType, Integer> enchantmentLevels,
            int resistanceLevel) {

        int totalEpf = 0;
        if (enchantmentLevels != null) {
            for (Map.Entry<EnchantmentType, Integer> entry : enchantmentLevels.entrySet()) {
                EnchantmentType type = entry.getKey();
                int level = entry.getValue();
                if (isApplicable(type, damageType) && level > 0) {
                    totalEpf += level * type.epfPerLevel();
                }
            }
        }

        double current = rawDamage;
        double armorPct = 0, enchantPct = 0, resistPct = 0;
        double afterArmor = rawDamage, afterEnchant = rawDamage, afterResist = rawDamage;

        if (damageType.isArmorAffected()) {
            double dmg = armorDamage(rawDamage, armor, toughness, breach);
            armorPct = rawDamage > 0 ? (1 - dmg / rawDamage) * 100 : 0;
            armorPct = Math.min(80, armorPct);
            current = dmg;
            afterArmor = dmg;
        }

        int cappedEpf = Math.min(totalEpf, 20);
        if (cappedEpf > 0 && current > 0) {
            double mult = Math.max(0, 1 - cappedEpf / 25.0);
            double dmg = current * mult;
            enchantPct = current > 0 ? (1 - dmg / current) * 100 : 0;
            current = dmg;
            afterEnchant = dmg;
        }

        if (resistanceLevel > 0 && current > 0) {
            double mult = resistanceLevel >= 5 ? 0 : Math.max(0, 1 - resistanceLevel * 0.2);
            double dmg = current * mult;
            resistPct = current > 0 ? (1 - dmg / current) * 100 : 0;
            current = dmg;
            afterResist = dmg;
        }

        double finalDmg = round(current, 4);
        double totalPct = rawDamage > 0 ? round((1 - finalDmg / rawDamage) * 100, 2) : 0;

        return new Result(
            round(rawDamage, 2), damageType,
            armor, toughness, breach, totalEpf, resistanceLevel,
            round(afterArmor, 4), round(armorPct, 2),
            round(afterEnchant, 4), round(enchantPct, 2),
            round(afterResist, 4), round(resistPct, 2),
            finalDmg, totalPct
        );
    }

    static double armorDamage(double rawDamage, int armor, double toughness, int breach) {
        if (armor <= 0) return rawDamage;
        double t = Math.min(toughness, 20);
        double eff = Math.max(armor * 0.2, armor - rawDamage / (2.0 + t * 0.25));
        eff = Math.min(20, eff);
        double reduction = eff / 25.0 - breach * 0.15;
        double mult = Math.max(0, Math.min(1, 1 - reduction));
        return rawDamage * mult;
    }

    private static boolean isApplicable(EnchantmentType type, DamageType dt) {
        switch (type) {
            case PROTECTION: return dt.isProtectionAffected();
            case FIRE_PROTECTION: return dt.isFireAffected();
            case BLAST_PROTECTION: return dt.isBlastAffected();
            case PROJECTILE_PROTECTION: return dt.isProjectileAffected();
            case FEATHER_FALLING: return dt == DamageType.FALL;
            default: return false;
        }
    }

    private static double round(double v, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(v * factor) / factor;
    }
}
