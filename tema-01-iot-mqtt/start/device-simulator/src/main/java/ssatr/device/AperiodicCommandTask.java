package ssatr.device;

import java.util.concurrent.BlockingQueue;

/**
 * TASK APERIODIC: execută comenzile primite de la business service.
 *
 * Comenzile sosesc oricând, pe MQTT, fără un interval minim între ele, și sunt
 * puse în coada commands. Fiecare comandă eliberează un job al taskului.
 * Comenzi care trebuie suportate:
 *
 *   SET_INTERVAL <ms>       schimbă perioada T a taskului periodic
 *   SET_THRESHOLD <valoare> schimbă pragul de alarmă
 *   RESET                   readuce senzorul la valoarea inițială
 *   STATUS                  raportează perioada și pragul curente
 *
 * După executarea fiecărei comenzi, device-ul publică pe Config.statusTopic(deviceId)
 * o confirmare: "deviceId;timestamp;text", de exemplu
 * "device-1;1727600000000;OK SET_INTERVAL 1000".
 */
public class AperiodicCommandTask implements Runnable {

    private final String deviceId;
    private final TemperatureSensor sensor;
    private final MqttConnection mqtt;
    private final BlockingQueue<String> commands;
    private final PeriodicTelemetryTask periodic;

    public AperiodicCommandTask(String deviceId, TemperatureSensor sensor, MqttConnection mqtt,
                                BlockingQueue<String> commands, PeriodicTelemetryTask periodic) {
        this.deviceId = deviceId;
        this.sensor = sensor;
        this.mqtt = mqtt;
        this.commands = commands;
        this.periodic = periodic;
    }

@Override
public void run() {
    try {
        while (true) {
            String command = commands.take();
            String[] parts = command.trim().split("\\s+");

            String result;

            try {
                switch (parts[0]) {
                    case "SET_INTERVAL":
                        if (parts.length != 2) {
                            throw new IllegalArgumentException("parametru invalid");
                        }

                        long period = Long.parseLong(parts[1]);

                        if (period <= 0) {
                            throw new IllegalArgumentException("interval invalid");
                        }

                        periodic.setPeriodMs(period);
                        result = "OK SET_INTERVAL " + period;
                        break;

                    case "SET_THRESHOLD":
                        if (parts.length != 2) {
                            throw new IllegalArgumentException("parametru invalid");
                        }

                        double threshold = Double.parseDouble(parts[1]);

                        if (!Double.isFinite(threshold)) {
                            throw new IllegalArgumentException("prag invalid");
                        }

                        periodic.setThreshold(threshold);
                        result = "OK SET_THRESHOLD " + threshold;
                        break;

                    case "RESET":
                        sensor.reset();
                        result = "OK RESET";
                        break;

                    case "STATUS":
                        result = "OK STATUS";
                        break;

                    default:
                        result = "ERROR comanda necunoscuta";
                        break;
                }
            } catch (IllegalArgumentException e) {
                result = "ERROR " + e.getMessage();
            }

            String message = deviceId + ";"
                    + System.currentTimeMillis() + ";"
                    + result;

            mqtt.publish(Config.statusTopic(deviceId), message);

            System.out.println("[aperiodic] " + message);
        }
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }
}
}
