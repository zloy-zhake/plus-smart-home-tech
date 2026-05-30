package ru.yandex.practicum.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.dto.*;
import ru.yandex.practicum.exception.NoSpecifiedProductInWarehouseException;
import ru.yandex.practicum.exception.ProductInShoppingCartLowQuantityInWarehouse;
import ru.yandex.practicum.exception.SpecifiedProductAlreadyInWarehouseException;
import ru.yandex.practicum.model.OrderBooking;
import ru.yandex.practicum.model.WarehouseProduct;
import ru.yandex.practicum.repository.OrderBookingRepository;
import ru.yandex.practicum.repository.WarehouseProductRepository;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WarehouseService {

    private static final String[] ADDRESSES = {"ADDRESS_1", "ADDRESS_2"};
    private static final String CURRENT_ADDRESS =
            ADDRESSES[Random.from(new SecureRandom()).nextInt(0, ADDRESSES.length)];

    private final WarehouseProductRepository warehouseProductRepository;
    private final OrderBookingRepository orderBookingRepository;

    public void newProductInWarehouse(NewProductInWarehouseRequest request) {
        if (warehouseProductRepository.existsById(request.getProductId())) {
            throw new SpecifiedProductAlreadyInWarehouseException(
                    "Товар уже зарегистрирован на складе: " + request.getProductId());
        }
        WarehouseProduct product = new WarehouseProduct();
        product.setProductId(request.getProductId());
        product.setFragile(request.isFragile());
        product.setWidth(request.getDimension().getWidth());
        product.setHeight(request.getDimension().getHeight());
        product.setDepth(request.getDimension().getDepth());
        product.setWeight(request.getWeight());
        product.setQuantity(0);
        warehouseProductRepository.save(product);
    }

    public void addProductToWarehouse(AddProductToWarehouseRequest request) {
        WarehouseProduct product = warehouseProductRepository.findById(request.getProductId())
                .orElseThrow(() -> new NoSpecifiedProductInWarehouseException(
                        "Товар не найден на складе: " + request.getProductId()));
        product.setQuantity(product.getQuantity() + request.getQuantity());
        warehouseProductRepository.save(product);
    }

    public BookedProductsDto checkProductQuantityEnoughForShoppingCart(ShoppingCartDto cartDto) {
        Set<UUID> productIds = cartDto.getProducts().keySet();

        Map<UUID, WarehouseProduct> warehouseProducts = warehouseProductRepository.findAllById(productIds)
                .stream()
                .collect(Collectors.toMap(WarehouseProduct::getProductId, p -> p));

        BigDecimal deliveryWeight = BigDecimal.ZERO;
        BigDecimal deliveryVolume = BigDecimal.ZERO;
        boolean fragile = false;

        for (Map.Entry<UUID, Long> entry : cartDto.getProducts().entrySet()) {
            UUID productId = entry.getKey();
            long requestedQuantity = entry.getValue();

            WarehouseProduct product = warehouseProducts.get(productId);
            if (product == null) {
                throw new ProductInShoppingCartLowQuantityInWarehouse(
                        "Товар отсутствует на складе: " + productId);
            }

            if (product.getQuantity() < requestedQuantity) {
                throw new ProductInShoppingCartLowQuantityInWarehouse(
                        "Недостаточно товара на складе: " + productId);
            }

            deliveryWeight = deliveryWeight.add(
                BigDecimal.valueOf(product.getWeight()).multiply(BigDecimal.valueOf(requestedQuantity)));
            deliveryVolume = deliveryVolume.add(
                BigDecimal.valueOf(product.getWidth())
                    .multiply(BigDecimal.valueOf(product.getHeight()))
                    .multiply(BigDecimal.valueOf(product.getDepth()))
                    .multiply(BigDecimal.valueOf(requestedQuantity)));
            if (product.isFragile()) {
                fragile = true;
            }
        }

        return new BookedProductsDto(deliveryWeight, deliveryVolume, fragile);
    }

    public AddressDto getWarehouseAddress() {
        return new AddressDto(
                CURRENT_ADDRESS, CURRENT_ADDRESS, CURRENT_ADDRESS, CURRENT_ADDRESS, CURRENT_ADDRESS);
    }

    @Transactional
    public BookedProductsDto assemblyProductsForOrder(AssemblyProductsForOrderRequest request) {
        Set<UUID> productIds = request.getProducts().keySet();

        Map<UUID, WarehouseProduct> warehouseProducts = warehouseProductRepository.findAllById(productIds)
                .stream()
                .collect(Collectors.toMap(WarehouseProduct::getProductId, p -> p));

        BigDecimal deliveryWeight = BigDecimal.ZERO;
        BigDecimal deliveryVolume = BigDecimal.ZERO;
        boolean fragile = false;

        for (Map.Entry<UUID, Long> entry : request.getProducts().entrySet()) {
            UUID productId = entry.getKey();
            long requestedQuantity = entry.getValue();

            WarehouseProduct product = warehouseProducts.get(productId);
            if (product == null) {
                throw new ProductInShoppingCartLowQuantityInWarehouse(
                        "Товар отсутствует на складе: " + productId);
            }
            if (product.getQuantity() < requestedQuantity) {
                throw new ProductInShoppingCartLowQuantityInWarehouse(
                        "Недостаточно товара для сборки заказа: " + productId);
            }

            product.setQuantity(product.getQuantity() - requestedQuantity);

            deliveryWeight = deliveryWeight.add(
                BigDecimal.valueOf(product.getWeight()).multiply(BigDecimal.valueOf(requestedQuantity)));
            deliveryVolume = deliveryVolume.add(
                BigDecimal.valueOf(product.getWidth())
                    .multiply(BigDecimal.valueOf(product.getHeight()))
                    .multiply(BigDecimal.valueOf(product.getDepth()))
                    .multiply(BigDecimal.valueOf(requestedQuantity)));
            if (product.isFragile()) {
                fragile = true;
            }
        }

        warehouseProductRepository.saveAll(warehouseProducts.values());

        OrderBooking booking = new OrderBooking();
        booking.setOrderId(request.getOrderId());
        booking.setProducts(request.getProducts());
        orderBookingRepository.save(booking);

        return new BookedProductsDto(deliveryWeight, deliveryVolume, fragile);
    }

    public void shippedToDelivery(ShippedToDeliveryRequest request) {
        OrderBooking booking = orderBookingRepository.findByOrderId(request.getOrderId())
                .orElseThrow(() -> new RuntimeException(
                        "Бронь заказа не найдена: " + request.getOrderId()));
        booking.setDeliveryId(request.getDeliveryId());
        orderBookingRepository.save(booking);
    }

    @Transactional
    public void acceptReturn(Map<UUID, Long> products) {
        List<WarehouseProduct> warehouseProducts = warehouseProductRepository.findAllById(products.keySet());

        if (warehouseProducts.size() < products.size()) {
            Set<UUID> foundIds = warehouseProducts.stream()
                    .map(WarehouseProduct::getProductId)
                    .collect(Collectors.toSet());
            products.keySet().stream()
                    .filter(id -> !foundIds.contains(id))
                    .findFirst()
                    .ifPresent(id -> { throw new NoSpecifiedProductInWarehouseException(
                            "Товар не найден на складе: " + id); });
        }

        warehouseProducts.forEach(p -> p.setQuantity(p.getQuantity() + products.get(p.getProductId())));
        warehouseProductRepository.saveAll(warehouseProducts);
    }
}
