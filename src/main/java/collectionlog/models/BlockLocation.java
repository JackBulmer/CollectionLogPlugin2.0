package collectionlog.models;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;

/**
 * Immutable block location for use in HashMaps and database queries.
 * Properly implements hashCode() and equals() based on world, x, y, z coordinates.
 */
public class BlockLocation {
    private final String worldName;
    private final int x;
    private final int y;
    private final int z;

    public BlockLocation(String worldName, int x, int y, int z) {
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public BlockLocation(Location location) {
        Objects.requireNonNull(location, "location cannot be null");
        World world = location.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("Location world cannot be null");
        }
        this.worldName = world.getName();
        this.x = location.getBlockX();
        this.y = location.getBlockY();
        this.z = location.getBlockZ();
    }

    public String getWorldName() {
        return worldName;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    /**
     * Convert to Bukkit Location (requires world to be loaded)
     */
    public Location toLocation(World world) {
        return new Location(world, x, y, z);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BlockLocation that = (BlockLocation) o;
        return x == that.x
                && y == that.y
                && z == that.z
                && Objects.equals(worldName, that.worldName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(worldName, x, y, z);
    }

    @Override
    public String toString() {
        return worldName + "," + x + "," + y + "," + z;
    }

    /**
     * Parse from string format "world,x,y,z"
     */
    public static BlockLocation fromString(String str) {
        String[] parts = str.split(",");
        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid BlockLocation string: " + str);
        }
        try {
            return new BlockLocation(
                    parts[0],
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3])
            );
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid coordinate in BlockLocation string: " + str, e);
        }
    }
}
