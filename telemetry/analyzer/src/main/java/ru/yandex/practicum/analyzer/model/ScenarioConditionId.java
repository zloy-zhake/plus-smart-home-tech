package ru.yandex.practicum.analyzer.model;

import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@EqualsAndHashCode
@Embeddable
public class ScenarioConditionId implements Serializable {

    private Long scenarioId;
    private String sensorId;
    private Long conditionId;
}
