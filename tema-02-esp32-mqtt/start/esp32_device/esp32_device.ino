
#include "config.h"
#include "connection.h"

#include <freertos/FreeRTOS.h>
#include <freertos/task.h>
#include <freertos/queue.h>

#include <stdlib.h>
#include <string.h>

// =====================================================
// PARAMETRI SI STRUCTURI
// =====================================================

// Temperatura interna a cipului ESP32-C3.
float read_temperature(void) {
    return temperatureRead();
}

// Parametrii modificabili prin MQTT.
volatile uint32_t period_ms = PERIOD_MS_INITIAL;
volatile float threshold = THRESHOLD_INITIAL;

// Eveniment transmis catre taskul sporadic.
struct AlarmEvent {
    float temperature;
    float threshold;
};

// Comanda copiata in coada.
struct CommandMessage {
    char text[COMMAND_MAX_LEN];
};

// Cozile FreeRTOS.
QueueHandle_t alarm_queue = nullptr;
QueueHandle_t command_queue = nullptr;


// =====================================================
// PUBLICARE RASPUNSURI LA COMENZI
// Format: DEVICE_ID;timestamp;rezultat
// =====================================================


void publish_command_response(const char* result) {
    char message[192];

    snprintf(
        message,
        sizeof(message),
        "%s;%lld;%s",
        DEVICE_ID,
        (long long)epoch_ms(),
        result
    );

    if (!mqtt_publish("status", message)) {
        Serial.println("[mqtt] Eroare la publicarea confirmarii.");
    }
}



// =====================================================
// TASK 1: PERIODIC
// Citeste temperatura si publica telemetria.
// =====================================================

void periodic_task(void* parameter) {
    TickType_t last_wake_time = xTaskGetTickCount();

    while (true) {
        uint32_t current_period = period_ms;

        if (current_period == 0) {
            current_period = PERIOD_MS_INITIAL;
        }

        float temperature = read_temperature();
        float current_threshold = threshold;

        // Publicare telemetrie.
        char message[128];

        snprintf(
            message,
            sizeof(message),
            "%s;%lld;%.2f",
            DEVICE_ID,
            (long long)epoch_ms(),
            (double)temperature
        );

        mqtt_publish("telemetry", message);

        // Generare eveniment daca temperatura depaseste pragul.
        if (temperature > current_threshold) {
            AlarmEvent event;

            event.temperature = temperature;
            event.threshold = current_threshold;

            // Timeout 0: taskul periodic nu asteapta coada.
            if (xQueueSend(alarm_queue, &event, 0) != pdPASS) {
                Serial.println(
                    "[periodic] Coada de alarme este plina."
                );
            }
        }

        // Asteptare periodica, raportata la momentul anterior.
        vTaskDelayUntil(
            &last_wake_time,
            pdMS_TO_TICKS(current_period)
        );
    }
}


// =====================================================
// TASK 2: SPORADIC
// Preia evenimentele si publica alarmele.
// Respecta intervalul minim dintre alarme.
// =====================================================

void sporadic_task(void* parameter) {
    uint32_t last_alarm_time = 0;
    bool alarm_published = false;

    AlarmEvent event;

    while (true) {
        // Asteapta un eveniment din coada.
        if (xQueueReceive(
                alarm_queue,
                &event,
                portMAX_DELAY
            ) == pdPASS) {

            uint32_t now = millis();

            bool interval_respected =
                !alarm_published ||
                (uint32_t)(now - last_alarm_time)
                    >= MIN_INTERARRIVAL_MS;

            if (interval_respected) {
                char message[128];

                snprintf(
                    message,
                    sizeof(message),
                    "%s;%lld;%.2f;%.2f",
                    DEVICE_ID,
                    (long long)epoch_ms(),
                    (double)event.temperature,
                    (double)event.threshold
                );

                if (mqtt_publish("alarm", message)) {
                    last_alarm_time = millis();
                    alarm_published = true;

                    Serial.println(
                        "[sporadic] Alarma publicata."
                    );
                } else {
                    Serial.println(
                        "[sporadic] Publicarea alarmei a esuat."
                    );
                }
            } else {
                // Evenimentul este eliminat daca intervalul
                // minim nu a trecut. Coada nu se acumuleaza.
                Serial.println(
                    "[sporadic] Alarma omisa: "
                    "intervalul minim nu a trecut."
                );
            }
        }
    }
}


// =====================================================
// TASK 3: APERIODIC
// Proceseaza comenzile primite prin MQTT.
// =====================================================

void aperiodic_task(void* parameter) {
    CommandMessage command;

    while (true) {
        if (xQueueReceive(
                command_queue,
                &command,
                portMAX_DELAY
            ) == pdPASS) {

            char* text = command.text;

            Serial.printf(
                "[aperiodic] Comanda: %s\n",
                text
            );

            // ---------------------------------------------
            // SET_INTERVAL <ms>
            // Exemplu: SET_INTERVAL 3000
            // ---------------------------------------------

            if (strncmp(text, "SET_INTERVAL ", 13) == 0) {
                char* end = nullptr;

                unsigned long value = strtoul(
                    text + 13,
                    &end,
                    10
                );

                if (end != text + 13 &&
                    *end == '\0' &&
                    value >= 100 &&
                    value <= 3600000UL) {

                    period_ms = (uint32_t)value;

                    char result[80];

                    snprintf(
                        result,
                        sizeof(result),
                        "OK SET_INTERVAL %lu",
                        value
                    );

                    publish_command_response(result);

                    Serial.printf(
                        "[aperiodic] Perioada setata: %lu ms\n",
                        value
                    );
                } else {
                    publish_command_response(
                        "ERROR SET_INTERVAL invalid_value"
                    );
                }
            }

            // ---------------------------------------------
            // SET_THRESHOLD <valoare>
            // Exemplu: SET_THRESHOLD 35
            // ---------------------------------------------

            else if (
                strncmp(text, "SET_THRESHOLD ", 14) == 0
            ) {
                char* end = nullptr;

                float value = strtof(text + 14, &end);

                if (end != text + 14 &&
                    *end == '\0' &&
                    value > -40.0f &&
                    value < 150.0f) {

                    threshold = value;

                    char result[80];

                    snprintf(
                        result,
                        sizeof(result),
                        "OK SET_THRESHOLD %.2f",
                        (double)value
                    );

                    publish_command_response(result);

                    Serial.printf(
                        "[aperiodic] Prag setat: %.2f\n",
                        (double)value
                    );
                } else {
                    publish_command_response(
                        "ERROR SET_THRESHOLD invalid_value"
                    );
                }
            }

            // ---------------------------------------------
            // RESET
            // Restabileste parametrii initiali.
            // ---------------------------------------------

            else if (strcmp(text, "RESET") == 0) {
                period_ms = PERIOD_MS_INITIAL;
                threshold = THRESHOLD_INITIAL;

                publish_command_response("OK RESET");

                Serial.println(
                    "[aperiodic] Parametrii initiali restaurati."
                );
            }

            // ---------------------------------------------
            // STATUS
            // Trimite starea curenta a dispozitivului.
            // ---------------------------------------------

            else if (strcmp(text, "STATUS") == 0) {
                char result[128];

                snprintf(
                    result,
                    sizeof(result),
                    "OK STATUS period=%lu threshold=%.2f temperature=%.2f",
                    (unsigned long)period_ms,
                    (double)threshold,
                    (double)read_temperature()
                );

                publish_command_response(result);

                Serial.println(
                    "[aperiodic] Starea a fost publicata."
                );
            }

            // ---------------------------------------------
            // COMANDA NECUNOSCUTA
            // ---------------------------------------------

            else {
                publish_command_response(
                    "ERROR UNKNOWN_COMMAND"
                );

                Serial.println(
                    "[aperiodic] Comanda necunoscuta."
                );
            }
        }
    }
}


// =====================================================
// CALLBACK MQTT
// Copiaza mesajul in coada pentru taskul aperiodic.
// =====================================================

void command_received(const char* text) {
    Serial.printf(
        "[mqtt] Comanda primita: %s\n",
        text
    );

    if (command_queue == nullptr) {
        Serial.println(
            "[mqtt] Coada de comenzi nu este disponibila."
        );
        return;
    }

    CommandMessage command;

    snprintf(
        command.text,
        sizeof(command.text),
        "%s",
        text
    );

    if (xQueueSend(command_queue, &command, 0) != pdPASS) {
        Serial.println(
            "[mqtt] Coada de comenzi este plina."
        );
    }
}


// =====================================================
// SETUP
// Initializare si pornire taskuri.
// =====================================================

void setup() {
    Serial.begin(115200);
    Serial.setTxTimeoutMs(0);

    uint32_t start_time = millis();

    while (!Serial && millis() - start_time < 3000) {
        delay(10);
    }

    delay(200);
    Serial.println();

    Serial.printf(
        "=== tema 02: STUDENT %s, DEVICE_ID %s ===\n",
        STUDENT,
        DEVICE_ID
    );

    // Conexiuni.
    wifi_connect();
    time_sync();

    // TODO 0a: cozile se creeaza inainte de MQTT.
    alarm_queue = xQueueCreate(10, sizeof(AlarmEvent));

    command_queue = xQueueCreate(
        10,
        sizeof(CommandMessage)
    );

    if (alarm_queue == nullptr ||
        command_queue == nullptr) {

        Serial.println(
            "Eroare: nu s-au putut crea cozile!"
        );

        while (true) {
            delay(1000);
        }
    }

    Serial.println("Cozile FreeRTOS au fost create.");

    // Porneste clientul MQTT.
    mqtt_start();

    // TODO 0b: porneste toate cele trei taskuri.
    BaseType_t periodic_result = xTaskCreate(
        periodic_task,
        "PeriodicTask",
        4096,
        nullptr,
        3,
        nullptr
    );

    BaseType_t sporadic_result = xTaskCreate(
        sporadic_task,
        "SporadicTask",
        4096,
        nullptr,
        2,
        nullptr
    );

    BaseType_t aperiodic_result = xTaskCreate(
        aperiodic_task,
        "AperiodicTask",
        4096,
        nullptr,
        2,
        nullptr
    );

    if (periodic_result != pdPASS ||
        sporadic_result != pdPASS ||
        aperiodic_result != pdPASS) {

        Serial.println(
            "Eroare: nu s-au putut porni toate taskurile!"
        );
    } else {
        Serial.println(
            "Taskurile periodic, sporadic si aperiodic "
            "au fost pornite."
        );
    }
}


// =====================================================
// LOOP
// Aplicatia este gestionata de taskurile FreeRTOS.
// =====================================================

void loop() {
    vTaskDelay(pdMS_TO_TICKS(1000));
}
