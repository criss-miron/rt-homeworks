
package ssatr.service;

import java.util.concurrent.BlockingQueue;

public class MessageProcessor implements Runnable {

    private final String kind;
    private final BlockingQueue<String> inbox;
    private final CsvStorage storage;
    private final Dashboard dashboard;

    public MessageProcessor(
            String kind,
            BlockingQueue<String> inbox,
            CsvStorage storage,
            Dashboard dashboard) {

        this.kind = kind;
        this.inbox = inbox;
        this.storage = storage;
        this.dashboard = dashboard;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                String message = inbox.take();

                // Salvam mesajul primit in CSV.
                storage.append(kind, message);

                try {
                    processMessage(message);
                } catch (IllegalArgumentException e) {
                    System.err.printf(
                            "[invalid] Mesaj %s: %s | Motiv: %s%n",
                            kind, message, e.getMessage());
                } catch (RuntimeException e) {
                    System.err.printf(
                            "[error] Eroare la procesarea mesajului %s: %s%n",
                            message, e.getMessage());
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (RuntimeException e) {
                System.err.println(
                        "[error] Eroare la preluarea sau salvarea mesajului: "
                                + e.getMessage());
            }
        }
    }

    private void processMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("Mesaj gol");
        }

        String[] fields = message.split(";", -1);

        if (fields.length < 3) {
            throw new IllegalArgumentException("Prea putine campuri");
        }

        String deviceId = fields[0].trim();

        if (deviceId.isEmpty()) {
            throw new IllegalArgumentException("Lipseste deviceId");
        }

        long timestamp;

        try {
            timestamp = Long.parseLong(fields[1].trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Timestamp-ul nu este numeric");
        }

        switch (kind) {
            case "telemetry": {
                if (fields.length != 3) {
                    throw new IllegalArgumentException(
                            "Telemetria trebuie sa aiba 3 campuri");
                }

                double temperature = parseNumber(fields[2]);

                dashboard.updateTelemetry(
                        deviceId, timestamp, temperature);
                break;
            }

            case "alarm": {
                if (fields.length != 4) {
                    throw new IllegalArgumentException(
                            "Alarma trebuie sa aiba 4 campuri");
                }

                double temperature = parseNumber(fields[2]);
                double threshold = parseNumber(fields[3]);

                dashboard.updateAlarm(
                        deviceId, timestamp, temperature, threshold);

                System.out.printf(
                        "[alarm] Device=%s temperatura=%.2f prag=%.2f%n",
                        deviceId, temperature, threshold);
                break;
            }

            case "status": {
                StringBuilder text = new StringBuilder(fields[2]);

                for (int i = 3; i < fields.length; i++) {
                    text.append(';').append(fields[i]);
                }

                String status = text.toString();

                dashboard.updateStatus(deviceId, timestamp, status);

                System.out.printf(
                        "[status] Device=%s raspuns=%s%n",
                        deviceId, status);
                break;
            }

            default:
                throw new IllegalArgumentException(
                        "Tip de mesaj necunoscut: " + kind);
        }
    }

    private double parseNumber(String value) {
        try {
            double number = Double.parseDouble(value.trim());

            if (!Double.isFinite(number)) {
                throw new IllegalArgumentException(
                        "Valoarea numerica nu este finita");
            }

            return number;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Valoare numerica invalida: " + value);
        }
    }
}
