package ru.yandex.practicum.analyzer.processor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.analyzer.model.Action;
import ru.yandex.practicum.analyzer.model.ActionType;
import ru.yandex.practicum.analyzer.model.Condition;
import ru.yandex.practicum.analyzer.model.ConditionOperation;
import ru.yandex.practicum.analyzer.model.ConditionType;
import ru.yandex.practicum.analyzer.model.Scenario;
import ru.yandex.practicum.analyzer.model.ScenarioAction;
import ru.yandex.practicum.analyzer.model.ScenarioActionId;
import ru.yandex.practicum.analyzer.model.ScenarioCondition;
import ru.yandex.practicum.analyzer.model.ScenarioConditionId;
import ru.yandex.practicum.analyzer.model.Sensor;
import ru.yandex.practicum.analyzer.repository.ActionRepository;
import ru.yandex.practicum.analyzer.repository.ConditionRepository;
import ru.yandex.practicum.analyzer.repository.ScenarioRepository;
import ru.yandex.practicum.analyzer.repository.SensorRepository;
import ru.yandex.practicum.kafka.telemetry.event.DeviceActionAvro;
import ru.yandex.practicum.kafka.telemetry.event.DeviceAddedEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.DeviceRemovedEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.HubEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.ScenarioAddedEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.ScenarioConditionAvro;
import ru.yandex.practicum.kafka.telemetry.event.ScenarioRemovedEventAvro;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class HubEventProcessor implements Runnable {

    private final KafkaConsumer<String, HubEventAvro> consumer;
    private final ScenarioRepository scenarioRepository;
    private final SensorRepository sensorRepository;
    private final ConditionRepository conditionRepository;
    private final ActionRepository actionRepository;

    @Value("${analyzer.kafka.topics.hubs}")
    private String hubsTopic;

    @Override
    public void run() {
        try {
            consumer.subscribe(List.of(hubsTopic));
            while (true) {
                ConsumerRecords<String, HubEventAvro> records =
                        consumer.poll(Duration.ofSeconds(5));
                for (ConsumerRecord<String, HubEventAvro> record : records) {
                    handleEvent(record.value());
                }
            }
        } catch (WakeupException ignored) {
        } catch (Exception e) {
            log.error("Ошибка обработки событий хаба", e);
        } finally {
            consumer.close();
        }
    }

    public void stop() {
        consumer.wakeup();
    }

    private void handleEvent(HubEventAvro event) {
        Object payload = event.getPayload();
        if (payload instanceof DeviceAddedEventAvro added) {
            handleDeviceAdded(event.getHub_id(), added);
        } else if (payload instanceof DeviceRemovedEventAvro removed) {
            handleDeviceRemoved(event.getHub_id(), removed);
        } else if (payload instanceof ScenarioAddedEventAvro added) {
            handleScenarioAdded(event.getHub_id(), added);
        } else if (payload instanceof ScenarioRemovedEventAvro removed) {
            handleScenarioRemoved(event.getHub_id(), removed);
        }
    }

    private void handleDeviceAdded(String hubId, DeviceAddedEventAvro event) {
        if (!sensorRepository.existsById(event.getId())) {
            Sensor sensor = new Sensor();
            sensor.setId(event.getId());
            sensor.setHubId(hubId);
            sensorRepository.save(sensor);
        }
    }

    private void handleDeviceRemoved(String hubId, DeviceRemovedEventAvro event) {
        String removedId = event.getId();
        scenarioRepository.findByHubId(hubId).stream()
                .filter(s -> s.getConditions().stream()
                        .anyMatch(c -> c.getSensor().getId().equals(removedId))
                        || s.getActions().stream()
                        .anyMatch(a -> a.getSensor().getId().equals(removedId)))
                .forEach(this::deleteScenarioWithCleanup);
        sensorRepository.findByIdAndHubId(removedId, hubId)
                .ifPresent(sensorRepository::delete);
    }

    private void handleScenarioAdded(String hubId, ScenarioAddedEventAvro event) {
        List<String> sensorIds = new ArrayList<>();
        for (ScenarioConditionAvro c : event.getConditions()) {
            sensorIds.add(c.getSensor_id());
        }
        for (DeviceActionAvro a : event.getActions()) {
            sensorIds.add(a.getSensor_id());
        }

        if (!sensorRepository.existsByIdInAndHubId(sensorIds, hubId)) {
            log.warn("Датчики сценария {} не зарегистрированы в хабе {}", event.getName(), hubId);
            return;
        }

        scenarioRepository.findByHubIdAndName(hubId, event.getName())
                .ifPresent(this::deleteScenarioWithCleanup);

        Scenario scenario = new Scenario();
        scenario.setHubId(hubId);
        scenario.setName(event.getName());

        for (ScenarioConditionAvro avroCondition : event.getConditions()) {
            Condition condition = new Condition();
            condition.setType(ConditionType.valueOf(avroCondition.getType().name()));
            condition.setOperation(ConditionOperation.valueOf(avroCondition.getOperation().name()));
            Object val = avroCondition.getValue();
            condition.setValue(val == null ? null : val instanceof Boolean b ? (b ? 1 : 0) : (Integer) val);
            conditionRepository.save(condition);

            Sensor sensor = sensorRepository.findByIdAndHubId(avroCondition.getSensor_id(), hubId).orElseThrow();

            ScenarioCondition sc = new ScenarioCondition();
            sc.setId(new ScenarioConditionId());
            sc.setScenario(scenario);
            sc.setSensor(sensor);
            sc.setCondition(condition);
            scenario.getConditions().add(sc);
        }

        for (DeviceActionAvro avroAction : event.getActions()) {
            Action action = new Action();
            action.setType(ActionType.valueOf(avroAction.getType().name()));
            action.setValue((Integer) avroAction.getValue());
            actionRepository.save(action);

            Sensor sensor = sensorRepository.findByIdAndHubId(avroAction.getSensor_id(), hubId).orElseThrow();

            ScenarioAction sa = new ScenarioAction();
            sa.setId(new ScenarioActionId());
            sa.setScenario(scenario);
            sa.setSensor(sensor);
            sa.setAction(action);
            scenario.getActions().add(sa);
        }

        scenarioRepository.save(scenario);
    }

    private void handleScenarioRemoved(String hubId, ScenarioRemovedEventAvro event) {
        scenarioRepository.findByHubIdAndName(hubId, event.getName())
                .ifPresent(this::deleteScenarioWithCleanup);
    }

    private void deleteScenarioWithCleanup(Scenario scenario) {
        List<Long> conditionIds = scenario.getConditions().stream()
                .map(sc -> sc.getCondition().getId())
                .toList();
        List<Long> actionIds = scenario.getActions().stream()
                .map(sa -> sa.getAction().getId())
                .toList();
        scenarioRepository.delete(scenario);
        conditionRepository.deleteAllById(conditionIds);
        actionRepository.deleteAllById(actionIds);
    }
}
