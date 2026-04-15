package cn.minerealms.iic.turrets;

public class LaserDamageConfig {

    private static float basicDamage = 4.0F;
    private static float advancedDamage = 8.0F;
    private static float eliteDamage = 12.0F;
    private static float ultimateDamage = 17.0F;

    public static float getBasicDamage() {
        return basicDamage;
    }

    public static void setBasicDamage(float damage) {
        basicDamage = damage;
    }

    public static float getAdvancedDamage() {
        return advancedDamage;
    }

    public static void setAdvancedDamage(float damage) {
        advancedDamage = damage;
    }

    public static float getEliteDamage() {
        return eliteDamage;
    }

    public static void setEliteDamage(float damage) {
        eliteDamage = damage;
    }

    public static float getUltimateDamage() {
        return ultimateDamage;
    }

    public static void setUltimateDamage(float damage) {
        ultimateDamage = damage;
    }
}
