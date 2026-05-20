package ru.yandex.practicum.collector.controller;

import com.google.protobuf.Empty;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;
import ru.yandex.practicum.collector.model.*;
import ru.yandex.practicum.collector.service.CollectorService;
import ru.yandex.practicum.grpc.telemetry.collector.CollectorControllerGrpc;
import ru.yandex.practicum.grpc.telemetry.event.HubEventProto;
import ru.yandex.practicum.grpc.telemetry.event.SensorEventProto;

import java.time.Instant;

@GrpcService
@RequiredArgsConstructor
public class EventController extends CollectorControllerGrpc.CollectorControllerImplBase {

    private final CollectorService collectorService;

    @Override
    public void collectSensorEvent(SensorEventProto request,
                                   StreamObserver<Empty> responseObserver) {
        try {
            collectorService.collectSensorEvent(toSensorEvent(request));
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(new StatusRuntimeException(
                    Status.INTERNAL.withDescription(e.getMessage()).withCause(e)));
        }
    }

    @Override
    public void collectHubEvent(HubEventProto request,
                                StreamObserver<Empty> responseObserver) {
        try {
            // конвертация Proto → DTO и передача в сервис — шаг 6
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(new StatusRuntimeException(
                    Status.INTERNAL.withDescription(e.getMessage()).withCause(e)));
        }
    }

    private SensorEvent toSensorEvent(SensorEventProto request) {
        SensorEvent event = switch (request.getPayloadCase()) {
            case MOTION_SENSOR_EVENT -> {
                MotionSensorEvent e = new MotionSensorEvent();
                e.setLinkQuality(request.getMotionSensorEvent().getLinkQuality());
                e.setMotion(request.getMotionSensorEvent().getMotion());
                e.setVoltage(request.getMotionSensorEvent().getVoltage());
                yield e;
            }
            case TEMPERATURE_SENSOR_EVENT -> {
                TemperatureSensorEvent e = new TemperatureSensorEvent();
                e.setTemperatureC(request.getTemperatureSensorEvent().getTemperatureC());
                e.setTemperatureF(request.getTemperatureSensorEvent().getTemperatureF());
                yield e;
            }
            case LIGHT_SENSOR_EVENT -> {
                LightSensorEvent e = new LightSensorEvent();
                e.setLinkQuality(request.getLightSensorEvent().getLinkQuality());
                e.setLuminosity(request.getLightSensorEvent().getLuminosity());
                yield e;
            }
            case CLIMATE_SENSOR_EVENT -> {
                ClimateSensorEvent e = new ClimateSensorEvent();
                e.setTemperatureC(request.getClimateSensorEvent().getTemperatureC());
                e.setHumidity(request.getClimateSensorEvent().getHumidity());
                e.setCo2Level(request.getClimateSensorEvent().getCo2Level());
                yield e;
            }
            case SWITCH_SENSOR_EVENT -> {
                SwitchSensorEvent e = new SwitchSensorEvent();
                e.setState(request.getSwitchSensorEvent().getState());
                yield e;
            }
            default -> throw new IllegalArgumentException(
                    "Неизвестный тип события датчика: " + request.getPayloadCase());
        };

        event.setId(request.getId());
        event.setHubId(request.getHubId());
        event.setTimestamp(Instant.ofEpochSecond(
                request.getTimestamp().getSeconds(),
                request.getTimestamp().getNanos()));

        return event;
    }
}
