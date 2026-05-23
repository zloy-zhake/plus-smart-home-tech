package ru.yandex.practicum.analyzer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class HubEventService {

    private final ScenarioRepository scenarioRepository;
    private final SensorRepository sensorRepository;
    private final ConditionRepository conditionRepository;
    private final ActionRepository actionRepository;

    public void handleEvent(HubEventAvro event) {
        Object payload = event.getPayload();
        if (payload instanceof DeviceAddedEventAvro added) {
            handleDeviceAdded(event.getHubId(), added);
        } else if (payload instanceof DeviceRemovedEventAvro removed) {
            handleDeviceRemoved(event.getHubId(), removed);
        } else if (payload instanceof ScenarioAddedEventAvro added) {
            handleScenarioAdded(event.getHubId(), added);
        } else if (payload instanceof ScenarioRemovedEventAvro removed) {
            handleScenarioRemoved(event.getHubId(), removed);
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
            sensorIds.add(c.getSensorId());
        }
        for (DeviceActionAvro a : event.getActions()) {
            sensorIds.add(a.getSensorId());
        }

        if (!sensorRepository.existsByIdInAndHubId(sensorIds, hubId)) {
            log.warn("Датчики сценария {} не зарегистрированы в хабе {}", event.getName(), hubId);
            return;
        }

        scenarioRepository.findByHubIdAndName(hubId, event.getName())
                .ifPresent(this::deleteScenarioWithCleanup);

        Map<String, Sensor> sensorMap = sensorRepository.findAllByIdInAndHubId(sensorIds, hubId)
                .stream()
                .collect(Collectors.toMap(Sensor::getId, s -> s));

        Scenario scenario = new Scenario();
        scenario.setHubId(hubId);
        scenario.setName(event.getName());

        List<Condition> conditions = new ArrayList<>();
        for (ScenarioConditionAvro avroCondition : event.getConditions()) {
            Condition condition = new Condition();
            condition.setType(ConditionType.valueOf(avroCondition.getType().name()));
            condition.setOperation(ConditionOperation.valueOf(avroCondition.getOperation().name()));
            Object val = avroCondition.getValue();
            condition.setValue(val == null ? null : val instanceof Boolean b ? (b ? 1 : 0) : (Integer) val);
            conditions.add(condition);
        }
        conditionRepository.saveAll(conditions);

        List<ScenarioConditionAvro> avroConditions = event.getConditions();
        for (int i = 0; i < avroConditions.size(); i++) {
            ScenarioCondition sc = new ScenarioCondition();
            sc.setId(new ScenarioConditionId());
            sc.setScenario(scenario);
            sc.setSensor(sensorMap.get(avroConditions.get(i).getSensorId()));
            sc.setCondition(conditions.get(i));
            scenario.getConditions().add(sc);
        }

        List<Action> actions = new ArrayList<>();
        for (DeviceActionAvro avroAction : event.getActions()) {
            Action action = new Action();
            action.setType(ActionType.valueOf(avroAction.getType().name()));
            action.setValue((Integer) avroAction.getValue());
            actions.add(action);
        }
        actionRepository.saveAll(actions);

        List<DeviceActionAvro> avroActions = event.getActions();
        for (int i = 0; i < avroActions.size(); i++) {
            ScenarioAction sa = new ScenarioAction();
            sa.setId(new ScenarioActionId());
            sa.setScenario(scenario);
            sa.setSensor(sensorMap.get(avroActions.get(i).getSensorId()));
            sa.setAction(actions.get(i));
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
        scenarioRepository.flush();
    }
}
