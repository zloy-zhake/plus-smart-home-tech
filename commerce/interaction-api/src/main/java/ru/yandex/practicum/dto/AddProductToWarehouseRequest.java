package ru.yandex.practicum.dto;

import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddProductToWarehouseRequest {
    private UUID productId;
    @Min(1)
    private long quantity;
}
