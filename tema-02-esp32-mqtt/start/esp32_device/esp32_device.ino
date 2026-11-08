
#include "config.h"
#include "connection.h"

#include <freertos/FreeRTOS.h>
#include <freertos/task.h>
#include <freertos/queue.h>

// Citeste temperatura interna a cipului ESP32-C3.
float read_temperature(void) {
    return temperatureRead();
}

// Parametrii curenti, modificabili prin comenzi MQTT.
volatile uint32_t period_ms = PERIOD_MS_INITIAL;
volatile float threshold = THRESHOLD_INITIAL;

// Eveniment transmis catre taskul sporadic.
struct AlarmEvent {
    float temperature;
    float threshold;
};

// Comanda copiata in coada MQTT.
struct CommandMessage {
    char text[COMMAND_MAX_LEN];
};

// Cozile FreeRTOS.
QueueHandle_t alarm_queue = nullptr;
QueueHandle_t command_queue = nullptr;

// TODO 1: Task periodic.
// Citeste temperatura, publica telemetria si trimite evenimentele de alarma.
void periodic_task(void* parameter) {
    TickType_t last_wake_time = xTaskGetTickCount();

    while (true) {
        uint32_t current_period = period_ms;
        if (current_period == 0) {
            current_period = PERIOD_MS_INITIAL;
        }

        float temperature = read_temperature();
        float current_threshold = threshold;

        char message[100];
        snprintf(
            message,
            sizeof(message),
            "%s;%lld;%.2f",
            DEVICE_ID,
            (long long)epoch_ms(),
            temperature
        );

        mqtt_publish("telemetry", message);

        if (temperature > current_threshold) {
            AlarmEvent event;
            event.temperature = temperature;
            event.threshold = current_threshold;

            // Timeout 0: taskul periodic nu asteapta eliberarea cozii.
            if (xQueueSend(alarm_queue, &event, 0) != pdPASS) {
                Serial.println("[periodic] Coada de alarme este plina.");
            }
        }

        // Asteptare relativa la grila periodica.
        // In pasul urmator verificam comportamentul perioadei dinamice.
        vTaskDelay(pdMS_TO_TICKS(current_period));

        // Reinitializam referinta de timp pentru urmatorul ciclu.
        last_wake_time = xTaskGetTickCount();
    }
}

// TODO 2: Task sporadic - il implementam in pasul urmator.

// TODO 3: Task aperiodic - il implementam in pasul urmator.

void command_received(const char* text) {
    Serial.printf("[mqtt] Comanda primita: %s\n", text);

    CommandMessage command;
    snprintf(command.text, sizeof(command.text), "%s", text);

    if (command_queue != nullptr) {
        if (xQueueSend(command_queue, &command, 0) != pdPASS) {
            Serial.println("[mqtt] Coada de comenzi este plina.");
        }
    }
}

void setup() {
    Serial.begin(115200);
    Serial.setTxTimeoutMs(0);

    uint32_t t = millis();
    while (!Serial && millis() - t < 3000) {
        delay(10);
    }

    delay(200);
    Serial.println();
    Serial.printf(
        "=== tema 02: STUDENT %s, DEVICE_ID %s ===\n",
        STUDENT,
        DEVICE_ID
    );

    wifi_connect();
    time_sync();

    // TODO 0a: cozile se creeaza inainte de pornirea MQTT.
    alarm_queue = xQueueCreate(10, sizeof(AlarmEvent));
    command_queue = xQueueCreate(10, sizeof(CommandMessage));

    if (alarm_queue == nullptr || command_queue == nullptr) {
        Serial.println("Eroare: nu s-au putut crea cozile!");
        while (true) {
            delay(1000);
        }
    }

    Serial.println("Cozile FreeRTOS au fost create.");

    mqtt_start();

    // TODO 0b: pornim taskul periodic.
    xTaskCreate(
        periodic_task,
        "PeriodicTask",
        4096,
        nullptr,
        3,
        nullptr
    );

    Serial.println("Taskul periodic a fost pornit.");
}

void loop() {
    delay(1000);
}
