package ssatr.service;

import java.util.concurrent.BlockingQueue;

/**
 * Preia mesajele de un anumit tip ("telemetry", "alarm" sau "status") din coada
 * inbox, le salvează și actualizează dashboard-ul.
 *
 * Formatul mesajelor (câmpuri separate prin ";"):
 *   telemetry:  deviceId;timestamp;temperatura
 *   alarm:      deviceId;timestamp;temperatura;prag
 *   status:     deviceId;timestamp;text
 */
public class MessageProcessor implements Runnable {

    private final String kind;
    private final BlockingQueue<String> inbox;
    private final CsvStorage storage;
    private final Dashboard dashboard;

    public MessageProcessor(String kind, BlockingQueue<String> inbox, CsvStorage storage, Dashboard dashboard) {
        this.kind = kind;
        this.inbox = inbox;
        this.storage = storage;
        this.dashboard = dashboard;
    }

    @Override
public void run() {
    try {
        while (true) {
            String message = inbox.take();

            try {
                String[] parts = message.split(";", -1);

                // Verifica formatul mesajului
                int expectedFields;

                switch (kind) {
                    case "telemetry":
                        expectedFields = 3;
                        break;
                    case "alarm":
                    case "status":
                        expectedFields = 4;
                        break;
                    default:
                        System.out.println("Tip de mesaj necunoscut: " + kind);
                        continue;
                }

                if (parts.length != expectedFields
                        || parts[0].isEmpty()
                        || parts[1].isEmpty()) {
                    System.out.println("Mesaj invalid: " + message);
                    continue;
                }

                // Salveaza mesajul in CSV
                storage.append(kind, message);

                // Actualizeaza dashboard-ul
                switch (kind) {
                    case "telemetry":
                        dashboard.updateTelemetry(
                            parts[0],
                            Double.parseDouble(parts[2])
                        );
                        break;

                    case "alarm":
                        System.out.println("[ALARM] " + message);

                        dashboard.updateAlarm(
                            parts[0],
                            Double.parseDouble(parts[2]),
                            Double.parseDouble(parts[3])
                        );
                        break;

                    case "status":
                        System.out.println("[STATUS] " + message);

                        dashboard.updateStatus(
                            parts[0],
                            parts[2] + ";" + parts[3]
                        );
                        break;
                }

            } catch (IllegalArgumentException e) {
                System.out.println("Mesaj invalid: " + message);
            }
        }
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }
}
}
