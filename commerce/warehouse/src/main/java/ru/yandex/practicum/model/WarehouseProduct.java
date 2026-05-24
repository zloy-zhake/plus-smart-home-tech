package ru.yandex.practicum.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "products")
public class WarehouseProduct {

    @Id
    private UUID productId;

    private boolean fragile;
    private double width;
    private double height;
    private double depth;
    private double weight;
    private long quantity;
}
