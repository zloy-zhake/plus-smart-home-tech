package ru.yandex.practicum.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.dto.*;
import ru.yandex.practicum.exception.NoPaymentFoundException;
import ru.yandex.practicum.exception.NotEnoughInfoInOrderToCalculateException;
import ru.yandex.practicum.feign.OrderClient;
import ru.yandex.practicum.feign.ShoppingStoreClient;
import ru.yandex.practicum.model.Payment;
import ru.yandex.practicum.repository.PaymentRepository;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final BigDecimal TAX_RATE = new BigDecimal("0.10");

    private final PaymentRepository paymentRepository;
    private final ShoppingStoreClient shoppingStoreClient;
    private final OrderClient orderClient;

    public BigDecimal productCost(OrderDto order) {
        if (order.getProducts() == null || order.getProducts().isEmpty()) {
            throw new NotEnoughInfoInOrderToCalculateException(
                    "Список товаров заказа пуст, расчёт невозможен");
        }
        Set<UUID> productIds = order.getProducts().keySet();
        Map<UUID, ProductDto> productMap = shoppingStoreClient.getProductsByIds(productIds)
                .stream()
                .collect(Collectors.toMap(ProductDto::getProductId, p -> p));

        return order.getProducts().entrySet().stream()
                .map(e -> productMap.get(e.getKey()).getPrice().multiply(BigDecimal.valueOf(e.getValue())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getTotalCost(OrderDto order) {
        if (order.getProducts() == null || order.getProducts().isEmpty()) {
            throw new NotEnoughInfoInOrderToCalculateException(
                    "Список товаров заказа пуст, расчёт невозможен");
        }
        if (order.getDeliveryPrice() == null) {
            throw new NotEnoughInfoInOrderToCalculateException(
                    "Стоимость доставки не рассчитана, итоговый расчёт невозможен");
        }
        BigDecimal cost = productCost(order);
        BigDecimal fee = cost.multiply(TAX_RATE);
        return cost.add(fee).add(order.getDeliveryPrice());
    }

    public PaymentDto payment(OrderDto order) {
        if (order.getOrderId() == null) {
            throw new NotEnoughInfoInOrderToCalculateException(
                    "Идентификатор заказа отсутствует");
        }
        if (order.getDeliveryPrice() == null) {
            throw new NotEnoughInfoInOrderToCalculateException(
                    "Стоимость доставки не рассчитана, оплата невозможна");
        }
        BigDecimal cost = productCost(order);
        BigDecimal fee = cost.multiply(TAX_RATE);
        BigDecimal total = cost.add(fee).add(order.getDeliveryPrice());

        Payment payment = new Payment();
        payment.setOrderId(order.getOrderId());
        payment.setProductPrice(cost);
        payment.setDeliveryPrice(order.getDeliveryPrice());
        payment.setFeeTotal(fee);
        payment.setTotalPayment(total);
        payment.setState(PaymentState.PENDING);
        payment = paymentRepository.save(payment);

        return toPaymentDto(payment);
    }

    public void paymentSuccess(UUID paymentId) {
        Payment payment = findPayment(paymentId);
        payment.setState(PaymentState.SUCCESS);
        paymentRepository.save(payment);
        orderClient.payment(payment.getOrderId());
    }

    public void paymentFailed(UUID paymentId) {
        Payment payment = findPayment(paymentId);
        payment.setState(PaymentState.FAILED);
        paymentRepository.save(payment);
        orderClient.paymentFailed(payment.getOrderId());
    }

    private Payment findPayment(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new NoPaymentFoundException("Платёж не найден: " + paymentId));
    }

    private PaymentDto toPaymentDto(Payment payment) {
        PaymentDto dto = new PaymentDto();
        dto.setPaymentId(payment.getPaymentId());
        dto.setTotalPayment(payment.getTotalPayment());
        dto.setDeliveryTotal(payment.getDeliveryPrice());
        dto.setFeeTotal(payment.getFeeTotal());
        return dto;
    }
}
