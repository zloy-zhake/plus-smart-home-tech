package ru.yandex.practicum.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookedProductsDto {
    private BigDecimal deliveryWeight;
    private BigDecimal deliveryVolume;
    private boolean fragile;
}
