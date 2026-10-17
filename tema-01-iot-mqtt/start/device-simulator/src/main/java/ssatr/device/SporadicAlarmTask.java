package ssatr.device;

import java.util.concurrent.BlockingQueue;

/**
 * TASK SPORADIC: tratează alarmele de temperatură.
 *
 * Evenimentele sosesc în coada alarmEvents (puse de taskul periodic).
 * Intervalul minim între sosiri este MIN_INTERARRIVAL_MS: între două eliberări
 * ale taskului (două alarme publicate) trebuie să treacă cel puțin atât.
 * Intervalul este impus de cod: evenimentele care sosesc mai des sunt
 * respinse, ca să nu fie inundat brokerul.
 */
public class SporadicAlarmTask implements Runnable {

    static final long MIN_INTERARRIVAL_MS = 5000;

    private final String deviceId;
    private final MqttConnection mqtt;
    private final BlockingQueue<Double> alarmEvents;

    public SporadicAlarmTask(String deviceId, MqttConnection mqtt, BlockingQueue<Double> alarmEvents) {
        this.deviceId = deviceId;
        this.mqtt = mqtt;
        this.alarmEvents = alarmEvents;
    }

    @Override
    public void run() {
        long lastRelease = 0;

    try {
        while (true) {
            double temperature = alarmEvents.take();

            long timestamp = System.currentTimeMillis();
            long sinceLast = timestamp - lastRelease;

            if (lastRelease != 0 && sinceLast < MIN_INTERARRIVAL_MS) {
                System.out.printf(
                    "[sporadic] Alarma RESPINSA: au trecut doar %d ms%n",
                    sinceLast
                );
                continue;
            }

            lastRelease = timestamp;

            double threshold = PeriodicTelemetryTask.ALARM_THRESHOLD;

            String message = deviceId + ";"
                    + timestamp + ";"
                    + temperature + ";"
                    + threshold;

            mqtt.publish(Config.alarmTopic(deviceId), message);

            System.out.println("[sporadic] Alarma publicata: " + message);
        }
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }
    }
}
