
package ssatr.service;

import java.util.Scanner;

/**
 * Consola operatorului: citește comenzi de la tastatură.
 *
 *   show                               afișează dashboard-ul
 *   <deviceId> <COMANDA> [parametru]   trimite o comandă unui device
 *   help                               afișează comenzile
 *   exit                               oprește serviciul
 */
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

        if (parts.length < 2 || parts[1].trim().isEmpty()) {
            System.out.println(
                "Eroare: format invalid. Exemplu: device-1 SET_INTERVAL 1000"
            );
            return;
        }

        String deviceId = parts[0];
        String command = parts[1].trim();

        String[] commandParts = command.split("\\s+");

        switch (commandParts[0]) {
            case "SET_INTERVAL":
            case "SET_THRESHOLD":
                if (commandParts.length != 2) {
                    System.out.println("Eroare: comanda necesita un parametru.");
                    return;
                }
                break;

            case "RESET":
            case "STATUS":
                if (commandParts.length != 1) {
                    System.out.println("Eroare: comanda nu accepta parametri.");
                    return;
                }
                break;

            default:
                System.out.println("Eroare: comanda necunoscuta.");
                return;
        }

        mqtt.publish(Config.commandTopic(deviceId), command);

        System.out.println(
            "Comanda trimisa catre " + deviceId + ": " + command
        );
    }

    private void printHelp() {
        System.out.println("Comenzi: show | <deviceId> SET_INTERVAL <ms> | <deviceId> SET_THRESHOLD <valoare>");
        System.out.println("         <deviceId> RESET | <deviceId> STATUS | help | exit");
    }
}
