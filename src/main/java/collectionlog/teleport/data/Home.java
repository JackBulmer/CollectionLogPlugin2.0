package collectionlog.teleport.data;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;

public class Home {
    private final String name;
    private Material material;
    private final String world;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;

    public Home(String name, Material material, String world, double x, double y, double z, float yaw, float pitch) {
        this.name = name;
        this.material = material;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public String getName() { return name; }
    public Material getMaterial() { return material; }
    public void setMaterial(Material material) { this.material = material; }
    public String getWorld() { return world; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }

    public Location toLocation() {
        org.bukkit.World w = Bukkit.getWorld(world);
        if (w == null) return null;
        return new Location(w, x, y, z, yaw, pitch);
    }
}
