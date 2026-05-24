package ru.yandex.practicum.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import ru.yandex.practicum.dto.*;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "shopping-store")
public interface ShoppingStoreClient {

    @GetMapping("/api/v1/shopping-store")
    ProductsPageDto getProducts(@RequestParam ProductCategory category,
                                @RequestParam int page,
                                @RequestParam int size,
                                @RequestParam List<String> sort);

    @PutMapping("/api/v1/shopping-store")
    ProductDto updateProduct(@RequestBody ProductDto product);

    @PostMapping("/api/v1/shopping-store")
    ProductDto createNewProduct(@RequestBody ProductDto product);

    @GetMapping("/api/v1/shopping-store/{productId}")
    ProductDto getProductById(@PathVariable UUID productId);

    @PostMapping("/api/v1/shopping-store/removeProductFromStore")
    Boolean removeProductFromStore(@RequestBody UUID productId);

    @PostMapping("/api/v1/shopping-store/quantityState")
    Boolean setProductQuantityState(@RequestBody SetProductQuantityStateRequest request);
}
