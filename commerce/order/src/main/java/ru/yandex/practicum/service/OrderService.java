package ru.yandex.practicum.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.dto.*;
import ru.yandex.practicum.exception.NoOrderFoundException;
import ru.yandex.practicum.exception.NotAuthorizedUserException;
import ru.yandex.practicum.feign.DeliveryClient;
import ru.yandex.practicum.feign.PaymentClient;
import ru.yandex.practicum.feign.WarehouseClient;
import ru.yandex.practicum.model.Order;
import ru.yandex.practicum.repository.OrderRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final WarehouseClient warehouseClient;
    private final PaymentClient paymentClient;
    private final DeliveryClient deliveryClient;

    @Transactional
    public OrderDto createNewOrder(CreateNewOrderRequest request) {
        BookedProductsDto booked =
                warehouseClient.checkProductQuantityEnoughForShoppingCart(request.getShoppingCart());

        AddressDto fromAddress = warehouseClient.getWarehouseAddress();

        Order order = new Order();
        order.setUsername(request.getUsername());
        order.setShoppingCartId(request.getShoppingCart().getShoppingCartId());
        order.setProducts(request.getShoppingCart().getProducts());
        order.setState(OrderState.NEW);
        order.setDeliveryWeight(booked.getDeliveryWeight());
        order.setDeliveryVolume(booked.getDeliveryVolume());
        order.setFragile(booked.isFragile());
        // Сохраняем ДО вызова planDelivery, чтобы получить реальный orderId из БД
        order = orderRepository.save(order);

        DeliveryDto deliveryDto = deliveryClient.planDelivery(new DeliveryDto(
                null,
                fromAddress,
                request.getDeliveryAddress(),
                order.getOrderId(),
                DeliveryState.CREATED,
                booked.getDeliveryWeight(),
                booked.getDeliveryVolume(),
                booked.isFragile()
        ));

        order.setDeliveryId(deliveryDto.getDeliveryId());
        orderRepository.save(order);

        return toOrderDto(order);
    }

    public OrderDto assembly(UUID orderId) {
        Order order = findOrder(orderId);
        warehouseClient.assemblyProductsForOrder(
                new AssemblyProductsForOrderRequest(orderId, order.getProducts()));
        order.setState(OrderState.ASSEMBLED);
        return toOrderDto(orderRepository.save(order));
    }

    public OrderDto calculateDeliveryCost(UUID orderId) {
        Order order = findOrder(orderId);
        BigDecimal deliveryPrice = deliveryClient.deliveryCost(toOrderDto(order));
        order.setDeliveryPrice(deliveryPrice);
        return toOrderDto(orderRepository.save(order));
    }

    public OrderDto calculateTotalCost(UUID orderId) {
        Order order = findOrder(orderId);
        OrderDto orderDto = toOrderDto(order);
        order.setProductPrice(paymentClient.productCost(orderDto));
        order.setTotalPrice(paymentClient.getTotalCost(toOrderDto(order)));
        return toOrderDto(orderRepository.save(order));
    }

    // CALLBACK от payment-сервиса: устанавливает статус PAID
    public OrderDto payment(UUID orderId) {
        Order order = findOrder(orderId);
        order.setState(OrderState.PAID);
        return toOrderDto(orderRepository.save(order));
    }

    // CALLBACK от payment-сервиса: устанавливает статус PAYMENT_FAILED
    public OrderDto paymentFailed(UUID orderId) {
        Order order = findOrder(orderId);
        order.setState(OrderState.PAYMENT_FAILED);
        return toOrderDto(orderRepository.save(order));
    }

    // CALLBACK от delivery-сервиса: устанавливает статус DELIVERED
    public OrderDto delivery(UUID orderId) {
        Order order = findOrder(orderId);
        order.setState(OrderState.DELIVERED);
        return toOrderDto(orderRepository.save(order));
    }

    // CALLBACK от delivery-сервиса: устанавливает статус DELIVERY_FAILED
    public OrderDto deliveryFailed(UUID orderId) {
        Order order = findOrder(orderId);
        order.setState(OrderState.DELIVERY_FAILED);
        return toOrderDto(orderRepository.save(order));
    }

    public OrderDto assemblyFailed(UUID orderId) {
        Order order = findOrder(orderId);
        order.setState(OrderState.ASSEMBLY_FAILED);
        return toOrderDto(orderRepository.save(order));
    }

    public OrderDto complete(UUID orderId) {
        Order order = findOrder(orderId);
        order.setState(OrderState.COMPLETED);
        return toOrderDto(orderRepository.save(order));
    }

    public OrderDto productReturn(ProductReturnRequest request) {
        Order order = findOrder(request.getOrderId());
        warehouseClient.acceptReturn(request.getProducts());
        order.setState(OrderState.PRODUCT_RETURNED);
        return toOrderDto(orderRepository.save(order));
    }

    public List<OrderDto> getClientOrders(String username) {
        if (username == null || username.isBlank()) {
            throw new NotAuthorizedUserException("Имя пользователя не должно быть пустым");
        }
        return orderRepository.findByUsername(username)
                .stream()
                .map(this::toOrderDto)
                .collect(Collectors.toList());
    }

    private Order findOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new NoOrderFoundException("Заказ не найден: " + orderId));
    }

    private OrderDto toOrderDto(Order order) {
        OrderDto dto = new OrderDto();
        dto.setOrderId(order.getOrderId());
        dto.setShoppingCartId(order.getShoppingCartId());
        dto.setProducts(order.getProducts());
        dto.setPaymentId(order.getPaymentId());
        dto.setDeliveryId(order.getDeliveryId());
        dto.setState(order.getState());
        dto.setDeliveryWeight(order.getDeliveryWeight());
        dto.setDeliveryVolume(order.getDeliveryVolume());
        dto.setFragile(order.isFragile());
        dto.setTotalPrice(order.getTotalPrice());
        dto.setDeliveryPrice(order.getDeliveryPrice());
        dto.setProductPrice(order.getProductPrice());
        return dto;
    }
}
