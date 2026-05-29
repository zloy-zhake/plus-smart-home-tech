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

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeliveryService {

    private static final BigDecimal BASE_RATE = new BigDecimal("5.0");
    private static final BigDecimal MULTIPLIER_2 = new BigDecimal("2");
    private static final BigDecimal FRAGILE_RATE = new BigDecimal("0.2");
    private static final BigDecimal WEIGHT_RATE = new BigDecimal("0.3");
    private static final BigDecimal VOLUME_RATE = new BigDecimal("0.2");
    private static final BigDecimal ADDRESS_DIFF_RATE = new BigDecimal("0.2");

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

    public BigDecimal deliveryCost(OrderDto order) {
        AddressDto warehouseAddr = warehouseClient.getWarehouseAddress();

        // Шаг 1: базовая ставка × множитель адреса склада
        BigDecimal multiplier = resolveWarehouseMultiplier(warehouseAddr);
        BigDecimal step = BASE_RATE.multiply(multiplier);
        // Шаг 2: прибавляем базовую ставку
        step = step.add(BASE_RATE);

        // Шаг 3: надбавка за хрупкость
        if (order.isFragile()) {
            step = step.add(step.multiply(FRAGILE_RATE));
        }

        // Шаг 4: вес
        step = step.add(order.getDeliveryWeight().multiply(WEIGHT_RATE));

        // Шаг 5: объём
        step = step.add(order.getDeliveryVolume().multiply(VOLUME_RATE));

        // Шаг 6: надбавка если адрес доставки не совпадает с улицей склада
        Delivery delivery = deliveryRepository.findByOrderId(order.getOrderId())
                .orElseThrow(() -> new NoDeliveryFoundException(
                        "Доставка не найдена для заказа: " + order.getOrderId()));
        String warehouseStreet = warehouseAddr.getStreet();
        String deliveryStreet = delivery.getToAddress() != null ? delivery.getToAddress().getStreet() : null;
        if (warehouseStreet == null || !warehouseStreet.equals(deliveryStreet)) {
            step = step.add(step.multiply(ADDRESS_DIFF_RATE));
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

    private BigDecimal resolveWarehouseMultiplier(AddressDto address) {
        String combined = address.getCountry() + address.getCity()
                + address.getStreet() + address.getHouse() + address.getFlat();
        if (combined.contains("ADDRESS_2")) return MULTIPLIER_2;
        return BigDecimal.ONE;
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
