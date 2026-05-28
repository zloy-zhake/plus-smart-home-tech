package ru.yandex.practicum.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import ru.yandex.practicum.dto.PaymentState;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID paymentId;

    private UUID orderId;

    private BigDecimal productPrice;

    private BigDecimal deliveryPrice;

    private BigDecimal feeTotal;

    private BigDecimal totalPayment;

    @Enumerated(EnumType.STRING)
    private PaymentState state;
}
