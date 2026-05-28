package ru.yandex.practicum.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import ru.yandex.practicum.dto.DeliveryState;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "deliveries")
public class Delivery {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID deliveryId;

    private UUID orderId;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "country", column = @Column(name = "from_country")),
            @AttributeOverride(name = "city",    column = @Column(name = "from_city")),
            @AttributeOverride(name = "street",  column = @Column(name = "from_street")),
            @AttributeOverride(name = "house",   column = @Column(name = "from_house")),
            @AttributeOverride(name = "flat",    column = @Column(name = "from_flat"))
    })
    private Address fromAddress;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "country", column = @Column(name = "to_country")),
            @AttributeOverride(name = "city",    column = @Column(name = "to_city")),
            @AttributeOverride(name = "street",  column = @Column(name = "to_street")),
            @AttributeOverride(name = "house",   column = @Column(name = "to_house")),
            @AttributeOverride(name = "flat",    column = @Column(name = "to_flat"))
    })
    private Address toAddress;

    @Enumerated(EnumType.STRING)
    private DeliveryState deliveryState;

    private double deliveryWeight;

    private double deliveryVolume;

    private boolean fragile;
}
