package me.lukiiy.swiftrun;

public class RunData {
    public long netherTime = 0;
    public long bastionTime = 0;
    public long strongholdTime = 0;
    public long endTime = 0;

    public String boardAct = "Started";

    public String serialize() { // TODO: save start time
        return netherTime + ";" + bastionTime + ";" + strongholdTime + ";" + endTime + ";" + boardAct;
    }

    public void deserialize(String data) {
        if (data == null || data.isEmpty()) return;
        String[] parts = data.split(";", -1);

        try { netherTime = Long.parseLong(parts[0]); } catch (Exception ignored) {}
        try { bastionTime = Long.parseLong(parts[1]); } catch (Exception ignored) {}
        try { strongholdTime = Long.parseLong(parts[2]); } catch (Exception ignored) {}
        try { endTime = Long.parseLong(parts[3]); } catch (Exception ignored) {}
        if (parts.length > 4) boardAct = parts[4];
    }
}
