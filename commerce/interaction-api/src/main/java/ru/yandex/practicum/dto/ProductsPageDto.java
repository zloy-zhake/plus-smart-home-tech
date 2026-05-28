package ru.yandex.practicum.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductsPageDto {
    private List<ProductDto> content;
    private long totalElements;
    private int totalPages;
    private int size;
    private int number;
}
