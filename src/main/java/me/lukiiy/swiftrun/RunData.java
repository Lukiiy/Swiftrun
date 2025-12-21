package me.lukiiy.swiftrun;

import org.bukkit.Location;

import java.util.HashMap;
import java.util.Map;

public class RunData {
    public Map<String, Long> times = new HashMap<>();
    public Map<String, Location> locations = new HashMap<>();

    public String board = "Started";

    public String serialize() {
        if (times.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();

        for (Map.Entry<String, Long> entry : times.entrySet()) sb.append(entry.getKey()).append("=").append(entry.getValue()).append(";");
        if (!board.isBlank()) sb.append("b=").append(board);

        return sb.toString();
    }

    public void deserialize(String data) {
        if (data == null || data.isEmpty()) return;

        String[] parts = data.split(";", -1);
        for (String part : parts) {
            if (part.isEmpty()) continue;

            String[] stuff = part.split("=", 2);
            if (stuff.length != 2) continue;

            String key = stuff[0];
            String value = stuff[1];

            if (key.equals("b")) {
                board = value;
                continue;
            }

            try { times.put(key, Long.parseLong(value)); } catch (Exception ignored) {}
        }
    }
}
