
package ssatr.service;

import java.util.Scanner;

public class CommandConsole implements Runnable {

    private final MqttConnection mqtt;
    private final Dashboard dashboard;

    public CommandConsole(MqttConnection mqtt, Dashboard dashboard) {
        this.mqtt = mqtt;
        this.dashboard = dashboard;
    }

    @Override
    public void run() {
        Scanner scanner = new Scanner(System.in);
        printHelp();

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();

            if (line.isEmpty()) {
                continue;
            } else if (line.equals("exit")) {
                break;
            } else if (line.equals("help")) {
                printHelp();
            } else if (line.equals("show")) {
                dashboard.print();
            } else {
                sendCommand(line);
            }
        }
    }

    private void sendCommand(String line) {
        String[] parts = line.split("\\s+", 2);

        if (parts.length != 2) {
            System.out.println(
                    "Eroare: foloseste <deviceId> <COMANDA> [parametru]");
            return;
        }

        String deviceId = parts[0].trim();
        String command = parts[1].trim();

        if (deviceId.isEmpty() || command.isEmpty()) {
            System.out.println("Eroare: deviceId sau comanda lipsa.");
            return;
        }

        String[] commandParts = command.split("\\s+");

        boolean valid;

        switch (commandParts[0]) {
            case "SET_INTERVAL":
                valid = commandParts.length == 2
                        && isPositiveInteger(commandParts[1]);
                break;

            case "SET_THRESHOLD":
                valid = commandParts.length == 2
                        && isNumber(commandParts[1]);
                break;

            case "RESET":
            case "STATUS":
                valid = commandParts.length == 1;
                break;

            default:
                valid = false;
        }

        if (!valid) {
            System.out.println(
                    "Eroare: comanda invalida. Scrie help pentru exemple.");
            return;
        }

        String topic = Config.commandTopic(deviceId);
        mqtt.publish(topic, command);

        System.out.printf(
                "[command] Publicata catre %s: %s%n",
                topic, command);
    }

    private boolean isPositiveInteger(String value) {
        try {
            return Long.parseLong(value) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isNumber(String value) {
        try {
            return Double.isFinite(Double.parseDouble(value));
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void printHelp() {
        System.out.println(
                "Comenzi: show | <deviceId> SET_INTERVAL <ms> "
                        + "| <deviceId> SET_THRESHOLD <valoare>");
        System.out.println(
                "         <deviceId> RESET | <deviceId> STATUS | help | exit");
    }
}
