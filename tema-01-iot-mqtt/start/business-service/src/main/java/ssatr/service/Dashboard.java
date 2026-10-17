package ssatr.service;

import java.util.HashMap;
import java.util.Map;

public class Dashboard {

    private final Map<String, DeviceState> devices = new HashMap<>();

    private static class DeviceState {
        double temperature;
        long lastMessage;
        int alarms;
        String status = "-";
    }

    public synchronized void updateTelemetry(String deviceId, double temperature) {
        DeviceState state = devices.computeIfAbsent(deviceId, k -> new DeviceState());
        state.temperature = temperature;
        state.lastMessage = System.currentTimeMillis();
    }

    public synchronized void updateAlarm(String deviceId, double temperature, double threshold) {
        DeviceState state = devices.computeIfAbsent(deviceId, k -> new DeviceState());
        state.temperature = temperature;
        state.lastMessage = System.currentTimeMillis();
        state.alarms++;
    }

    public synchronized void updateStatus(String deviceId, String status) {
        DeviceState state = devices.computeIfAbsent(deviceId, k -> new DeviceState());
        state.status = status;
        state.lastMessage = System.currentTimeMillis();
    }

    public synchronized void print() {
        System.out.printf("%-15s %-10s %-20s %-10s %-20s%n",
                "DEVICE", "TEMP", "ULTIMUL MESAJ", "ALARME", "STATUS");

        for (Map.Entry<String, DeviceState> entry : devices.entrySet()) {
            DeviceState state = entry.getValue();
            long seconds = (System.currentTimeMillis() - state.lastMessage) / 1000;

            System.out.printf("%-15s %-10.2f acum %d s %-10d %-20s%n",
                    entry.getKey(),
                    state.temperature,
                    seconds,
                    state.alarms,
                    state.status);
        }
    }
}
