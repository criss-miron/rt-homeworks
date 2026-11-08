
package ssatr.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public class Dashboard {

    private static class DeviceState {
        double temperature;
        long lastTimestamp;
        int alarms;
        boolean hasTemperature;
        String lastStatus = "-";
    }

    private final Map<String, DeviceState> devices = new LinkedHashMap<>();

    public synchronized void updateTelemetry(
            String deviceId, long timestamp, double temperature) {

        DeviceState state = devices.computeIfAbsent(
                deviceId, id -> new DeviceState());

        state.temperature = temperature;
        state.lastTimestamp = timestamp;
        state.hasTemperature = true;
    }

    public synchronized void updateAlarm(
            String deviceId, long timestamp,
            double temperature, double threshold) {

        DeviceState state = devices.computeIfAbsent(
                deviceId, id -> new DeviceState());

        state.temperature = temperature;
        state.lastTimestamp = timestamp;
        state.hasTemperature = true;
        state.alarms++;
    }

    public synchronized void updateStatus(
            String deviceId, long timestamp, String status) {

        DeviceState state = devices.computeIfAbsent(
                deviceId, id -> new DeviceState());

        state.lastTimestamp = timestamp;
        state.lastStatus = status;
    }

    public synchronized void print() {
        System.out.println();
        System.out.printf(
                "%-20s %-12s %-25s %-10s %-25s%n",
                "DEVICE", "TEMP (C)", "ULTIMUL MESAJ", "ALARME", "ULTIMUL STATUS");

        System.out.println(
                "------------------------------------------------------------------------------------------");

        long now = System.currentTimeMillis();

        for (Map.Entry<String, DeviceState> entry : devices.entrySet()) {
            String deviceId = entry.getKey();
            DeviceState state = entry.getValue();

            String temperature = state.hasTemperature
                    ? String.format("%.2f", state.temperature)
                    : "-";

            long ageSeconds = Math.max(
                    0, (now - state.lastTimestamp) / 1000);

            String lastMessage = ageSeconds + " s";

            System.out.printf(
                    "%-20s %-12s %-25s %-10d %-25s%n",
                    deviceId,
                    temperature,
                    lastMessage,
                    state.alarms,
                    state.lastStatus);
        }

        if (devices.isEmpty()) {
            System.out.println("Nu exista inca dispozitive inregistrate.");
        }

        System.out.println();
    }
}
