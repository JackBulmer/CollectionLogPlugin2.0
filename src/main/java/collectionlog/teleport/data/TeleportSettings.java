package collectionlog.teleport.data;

public class TeleportSettings {
    private int maxHomes;
    private int tpCost;
    private boolean interDimensional;
    private int countdownSeconds;

    public TeleportSettings(int maxHomes, int tpCost, boolean interDimensional, int countdownSeconds) {
        this.maxHomes = maxHomes;
        this.tpCost = tpCost;
        this.interDimensional = interDimensional;
        this.countdownSeconds = countdownSeconds;
    }

    public int getMaxHomes() { return maxHomes; }
    public void setMaxHomes(int maxHomes) { this.maxHomes = Math.min(18, Math.max(0, maxHomes)); }
    public int getTpCost() { return tpCost; }
    public void setTpCost(int tpCost) { this.tpCost = Math.max(0, tpCost); }
    public boolean isInterDimensional() { return interDimensional; }
    public void setInterDimensional(boolean interDimensional) { this.interDimensional = interDimensional; }
    public int getCountdownSeconds() { return countdownSeconds; }
    public void setCountdownSeconds(int countdownSeconds) { this.countdownSeconds = Math.max(0, countdownSeconds); }
}
