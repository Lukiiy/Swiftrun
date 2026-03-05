package me.lukiiy.swiftrun;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class RunData {
    private final Map<String, Long> times = new LinkedHashMap<>();
    private final Map<String, Location> locations = new LinkedHashMap<>();

    public void setTime(String id, long time) {
        if (times.containsKey(id)) return;

        times.put(id, time);
    }

    public void setLocation(String id, Location location) {
        if (locations.containsKey(id)) return;

        locations.put(id, location);
    }

    public Map<String, Long> getTimes() {
        return Collections.unmodifiableMap(times);
    }

    public Map<String, Location> getLocations() {
        return Collections.unmodifiableMap(locations);
    }

    public String board = "Started";

    public String serialize() {
        if (times.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();

        for (Map.Entry<String, Long> entry : times.entrySet()) sb.append(entry.getKey()).append("=").append(entry.getValue()).append(";"); // times: id=millis;

        for (Map.Entry<String, Location> entry : locations.entrySet()) { // locations: l=id,x,y,z,world;
            Location loc = entry.getValue();
            String world = loc.getWorld() == null ? "_" : loc.getWorld().getName();

            sb.append("l=").append(entry.getKey()).append(",").append(loc.getBlockX()).append(",").append(loc.getBlockY()).append(",").append(loc.getBlockZ()).append(",").append(world).append(";");
        }

        if (!board.isBlank()) sb.append("b=").append(board);

        return sb.toString();
    }

    public void deserialize(String data) {
        if (data == null || data.isEmpty()) return;

        String[] parts = data.split(";", -1);
        for (String part : parts) {
            if (part == null || part.isEmpty()) continue;

            if (part.startsWith("l=")) {
                String[] bits = part.substring(2).split(",", 5);
                if (bits.length != 5) continue;

                try {
                    int x = Integer.parseInt(bits[1]);
                    int y = Integer.parseInt(bits[2]);
                    int z = Integer.parseInt(bits[3]);
                    String worldName = bits[4];

                    if (!worldName.equalsIgnoreCase("_")) locations.put(bits[0], new Location(Bukkit.getWorld(worldName), x, y, z));
                } catch (Exception ignored) {}
                continue;
            }

            String[] keyVal = part.split("=", 2);
            if (keyVal.length != 2) continue;

            String key = keyVal[0];
            String value = keyVal[1];

            if ("b".equals(key)) {
                board = value;
                continue;
            }

            try {
                times.put(key, Long.parseLong(value));
            } catch (Exception ignored) {}
        }
    }
}
