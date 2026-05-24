package ru.yandex.practicum.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.RestController;
import ru.yandex.practicum.dto.*;
import ru.yandex.practicum.feign.ShoppingStoreClient;
import ru.yandex.practicum.model.Product;
import ru.yandex.practicum.service.ShoppingStoreService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ShoppingStoreController implements ShoppingStoreClient {

    private final ShoppingStoreService shoppingStoreService;

    @Override
    public ProductsPageDto getProducts(ProductCategory category, int page, int size, List<String> sort) {
        // Spring MVC splits "productName,asc" into ["productName","asc"] via StringToCollectionConverter.
        // Rejoin all tokens and process as field+direction pairs.
        String[] tokens = String.join(",", sort).split(",");
        List<Sort.Order> orders = new ArrayList<>();
        for (int i = 0; i < tokens.length; i++) {
            String field = tokens[i].trim();
            if (i + 1 < tokens.length &&
                    (tokens[i + 1].trim().equalsIgnoreCase("asc") || tokens[i + 1].trim().equalsIgnoreCase("desc"))) {
                orders.add(tokens[i + 1].trim().equalsIgnoreCase("desc")
                        ? Sort.Order.desc(field) : Sort.Order.asc(field));
                i++;
            } else {
                orders.add(Sort.Order.asc(field));
            }
        }
        Sort sortObj = orders.isEmpty() ? Sort.unsorted() : Sort.by(orders);
        PageRequest pageRequest = PageRequest.of(page, size, sortObj);
        Page<Product> products = shoppingStoreService.getProducts(category, pageRequest);
        return new ProductsPageDto(
                products.getContent().stream().map(this::toDto).toList(),
                products.getTotalElements(),
                products.getTotalPages(),
                products.getSize(),
                products.getNumber()
        );
    }

    @Override
    public ProductDto updateProduct(ProductDto product) {
        return toDto(shoppingStoreService.updateProduct(product));
    }

    @Override
    public ProductDto createNewProduct(ProductDto product) {
        return toDto(shoppingStoreService.createNewProduct(product));
    }

    @Override
    public ProductDto getProductById(UUID productId) {
        return toDto(shoppingStoreService.getProduct(productId));
    }

    @Override
    public Boolean removeProductFromStore(UUID productId) {
        return shoppingStoreService.removeProductFromStore(productId);
    }

    @Override
    public Boolean setProductQuantityState(SetProductQuantityStateRequest request) {
        return shoppingStoreService.setProductQuantityState(request);
    }

    private ProductDto toDto(Product product) {
        return new ProductDto(
                product.getProductId(),
                product.getProductName(),
                product.getDescription(),
                product.getImageSrc(),
                product.getQuantityState(),
                product.getProductState(),
                product.getProductCategory(),
                product.getPrice()
        );
    }
}
