package ru.yandex.practicum.analyzer.processor;

import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.analyzer.model.Scenario;
import ru.yandex.practicum.analyzer.model.ScenarioAction;
import ru.yandex.practicum.analyzer.model.ScenarioCondition;
import ru.yandex.practicum.analyzer.repository.ScenarioRepository;
import ru.yandex.practicum.grpc.telemetry.event.ActionTypeProto;
import ru.yandex.practicum.grpc.telemetry.event.DeviceActionProto;
import ru.yandex.practicum.grpc.telemetry.event.DeviceActionRequest;
import ru.yandex.practicum.grpc.telemetry.hubrouter.HubRouterControllerGrpc.HubRouterControllerBlockingStub;
import ru.yandex.practicum.kafka.telemetry.event.ClimateSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.LightSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.MotionSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorStateAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;
import ru.yandex.practicum.kafka.telemetry.event.SwitchSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.TemperatureSensorAvro;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Component
public class SnapshotProcessor {

    private final KafkaConsumer<String, SensorsSnapshotAvro> consumer;
    private final ScenarioRepository scenarioRepository;
    private final HubRouterControllerBlockingStub hubRouterClient;

    @Value("${analyzer.kafka.topics.snapshots}")
    private String snapshotsTopic;

    public SnapshotProcessor(
            KafkaConsumer<String, SensorsSnapshotAvro> consumer,
            ScenarioRepository scenarioRepository,
            @GrpcClient("hub-router") HubRouterControllerBlockingStub hubRouterClient) {
        this.consumer = consumer;
        this.scenarioRepository = scenarioRepository;
        this.hubRouterClient = hubRouterClient;
    }

    public void stop() {
        consumer.wakeup();
    }

    public void start() {
        try {
            consumer.subscribe(List.of(snapshotsTopic));
            while (true) {
                ConsumerRecords<String, SensorsSnapshotAvro> records =
                        consumer.poll(Duration.ofSeconds(5));
                for (ConsumerRecord<String, SensorsSnapshotAvro> record : records) {
                    analyzeSnapshot(record.value());
                }
                consumer.commitSync();
            }
        } catch (WakeupException ignored) {
        } catch (Exception e) {
            log.error("Ошибка обработки снапшота", e);
        } finally {
            consumer.close();
        }
    }

    private void analyzeSnapshot(SensorsSnapshotAvro snapshot) {
        List<Scenario> scenarios = scenarioRepository.findByHubId(snapshot.getHubId());
        scenarios.stream()
                .filter(scenario -> allConditionsMet(scenario, snapshot))
                .flatMap(scenario -> scenario.getActions().stream()
                        .map(action -> toDeviceActionRequest(scenario, action, snapshot)))
                .forEach(hubRouterClient::handleDeviceAction);
    }

    private boolean allConditionsMet(Scenario scenario, SensorsSnapshotAvro snapshot) {
        return scenario.getConditions().stream()
                .allMatch(sc -> checkCondition(sc, snapshot));
    }

    private boolean checkCondition(ScenarioCondition sc, SensorsSnapshotAvro snapshot) {
        SensorStateAvro state = snapshot.getSensorsState().get(sc.getSensor().getId());
        if (state == null) {
            return false;
        }

        Integer conditionValue = sc.getCondition().getValue();
        if (conditionValue == null) {
            return false;
        }

        Object data = state.getData();
        int actualValue;
        try {
            actualValue = switch (sc.getCondition().getType()) {
                case MOTION -> ((MotionSensorAvro) data).getMotion() ? 1 : 0;
                case LUMINOSITY -> ((LightSensorAvro) data).getLuminosity();
                case SWITCH -> ((SwitchSensorAvro) data).getState() ? 1 : 0;
                case TEMPERATURE -> data instanceof ClimateSensorAvro c
                        ? c.getTemperature_c()
                        : ((TemperatureSensorAvro) data).getTemperature_c();
                case CO2LEVEL -> ((ClimateSensorAvro) data).getCo2_level();
                case HUMIDITY -> ((ClimateSensorAvro) data).getHumidity();
            };
        } catch (ClassCastException e) {
            log.warn("Неожиданный тип данных датчика {} для условия {}",
                    sc.getSensor().getId(), sc.getCondition().getType());
            return false;
        }

        return switch (sc.getCondition().getOperation()) {
            case EQUALS -> actualValue == conditionValue;
            case GREATER_THAN -> actualValue > conditionValue;
            case LOWER_THAN -> actualValue < conditionValue;
        };
    }

    private DeviceActionRequest toDeviceActionRequest(Scenario scenario, ScenarioAction sa,
                                                      SensorsSnapshotAvro snapshot) {
        DeviceActionProto.Builder actionBuilder = DeviceActionProto.newBuilder()
                .setSensorId(sa.getSensor().getId())
                .setType(ActionTypeProto.valueOf(sa.getAction().getType().name()));
        if (sa.getAction().getValue() != null) {
            actionBuilder.setValue(sa.getAction().getValue());
        }

        return DeviceActionRequest.newBuilder()
                .setHubId(snapshot.getHubId())
                .setScenarioName(scenario.getName())
                .setAction(actionBuilder.build())
                .setTimestamp(toTimestamp(snapshot.getTimestamp()))
                .build();
    }

    private com.google.protobuf.Timestamp toTimestamp(Instant instant) {
        return com.google.protobuf.Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }
}
