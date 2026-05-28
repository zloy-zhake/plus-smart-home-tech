package ru.yandex.practicum.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import ru.yandex.practicum.dto.DeliveryDto;
import ru.yandex.practicum.dto.OrderDto;
import ru.yandex.practicum.feign.DeliveryClient;
import ru.yandex.practicum.service.DeliveryService;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class DeliveryController implements DeliveryClient {

    private final DeliveryService deliveryService;

    @Override
    public DeliveryDto planDelivery(DeliveryDto delivery) {
        return deliveryService.planDelivery(delivery);
    }

    @Override
    public BigDecimal deliveryCost(OrderDto order) {
        return BigDecimal.valueOf(deliveryService.deliveryCost(order));
    }

    @Override
    public void deliveryPicked(UUID orderId) {
        deliveryService.deliveryPicked(orderId);
    }

    @Override
    public void deliverySuccessful(UUID orderId) {
        deliveryService.deliverySuccessful(orderId);
    }

    @Override
    public void deliveryFailed(UUID orderId) {
        deliveryService.deliveryFailed(orderId);
    }
}
