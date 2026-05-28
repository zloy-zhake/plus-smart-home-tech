package ru.yandex.practicum.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.dto.*;
import ru.yandex.practicum.exception.NoDeliveryFoundException;
import ru.yandex.practicum.feign.OrderClient;
import ru.yandex.practicum.feign.WarehouseClient;
import ru.yandex.practicum.model.Address;
import ru.yandex.practicum.model.Delivery;
import ru.yandex.practicum.repository.DeliveryRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeliveryService {

    private static final double BASE_RATE = 5.0;

    private final DeliveryRepository deliveryRepository;
    private final WarehouseClient warehouseClient;
    private final OrderClient orderClient;

    public DeliveryDto planDelivery(DeliveryDto dto) {
        Delivery delivery = new Delivery();
        delivery.setOrderId(dto.getOrderId());
        delivery.setFromAddress(toAddress(dto.getFromAddress()));
        delivery.setToAddress(toAddress(dto.getToAddress()));
        delivery.setDeliveryState(DeliveryState.CREATED);
        delivery.setDeliveryWeight(dto.getDeliveryWeight());
        delivery.setDeliveryVolume(dto.getDeliveryVolume());
        delivery.setFragile(dto.isFragile());
        delivery = deliveryRepository.save(delivery);
        return toDeliveryDto(delivery);
    }

    public double deliveryCost(OrderDto order) {
        AddressDto warehouseAddr = warehouseClient.getWarehouseAddress();

        // Шаг 1: базовая ставка × множитель адреса склада
        double multiplier = resolveWarehouseMultiplier(warehouseAddr);
        double step = BASE_RATE * multiplier;
        // Шаг 2: прибавляем базовую ставку
        step = step + BASE_RATE;

        // Шаг 3: надбавка за хрупкость
        if (order.isFragile()) {
            step = step + step * 0.2;
        }

        // Шаг 4: вес
        step = step + order.getDeliveryWeight() * 0.3;

        // Шаг 5: объём
        step = step + order.getDeliveryVolume() * 0.2;

        // Шаг 6: надбавка если адрес доставки не совпадает с улицей склада
        Delivery delivery = deliveryRepository.findByOrderId(order.getOrderId())
                .orElseThrow(() -> new NoDeliveryFoundException(
                        "Доставка не найдена для заказа: " + order.getOrderId()));
        String warehouseStreet = warehouseAddr.getStreet();
        String deliveryStreet = delivery.getToAddress() != null ? delivery.getToAddress().getStreet() : null;
        if (warehouseStreet == null || !warehouseStreet.equals(deliveryStreet)) {
            step = step + step * 0.2;
        }

        return step;
    }

    public void deliveryPicked(UUID orderId) {
        Delivery delivery = findByOrderId(orderId);
        delivery.setDeliveryState(DeliveryState.IN_PROGRESS);
        deliveryRepository.save(delivery);
        warehouseClient.shippedToDelivery(
                new ShippedToDeliveryRequest(orderId, delivery.getDeliveryId()));
    }

    public void deliverySuccessful(UUID orderId) {
        Delivery delivery = findByOrderId(orderId);
        delivery.setDeliveryState(DeliveryState.DELIVERED);
        deliveryRepository.save(delivery);
        orderClient.delivery(orderId);
    }

    public void deliveryFailed(UUID orderId) {
        Delivery delivery = findByOrderId(orderId);
        delivery.setDeliveryState(DeliveryState.FAILED);
        deliveryRepository.save(delivery);
        orderClient.deliveryFailed(orderId);
    }

    private Delivery findByOrderId(UUID orderId) {
        return deliveryRepository.findByOrderId(orderId)
                .orElseThrow(() -> new NoDeliveryFoundException(
                        "Доставка не найдена для заказа: " + orderId));
    }

    private double resolveWarehouseMultiplier(AddressDto address) {
        String combined = address.getCountry() + address.getCity()
                + address.getStreet() + address.getHouse() + address.getFlat();
        if (combined.contains("ADDRESS_2")) return 2.0;
        if (combined.contains("ADDRESS_1")) return 1.0;
        return 1.0;
    }

    private Address toAddress(AddressDto dto) {
        if (dto == null) return null;
        Address address = new Address();
        address.setCountry(dto.getCountry());
        address.setCity(dto.getCity());
        address.setStreet(dto.getStreet());
        address.setHouse(dto.getHouse());
        address.setFlat(dto.getFlat());
        return address;
    }

    private DeliveryDto toDeliveryDto(Delivery delivery) {
        DeliveryDto dto = new DeliveryDto();
        dto.setDeliveryId(delivery.getDeliveryId());
        dto.setOrderId(delivery.getOrderId());
        dto.setFromAddress(toAddressDto(delivery.getFromAddress()));
        dto.setToAddress(toAddressDto(delivery.getToAddress()));
        dto.setDeliveryState(delivery.getDeliveryState());
        dto.setDeliveryWeight(delivery.getDeliveryWeight());
        dto.setDeliveryVolume(delivery.getDeliveryVolume());
        dto.setFragile(delivery.isFragile());
        return dto;
    }

    private AddressDto toAddressDto(Address address) {
        if (address == null) return null;
        return new AddressDto(
                address.getCountry(),
                address.getCity(),
                address.getStreet(),
                address.getHouse(),
                address.getFlat()
        );
    }
}
