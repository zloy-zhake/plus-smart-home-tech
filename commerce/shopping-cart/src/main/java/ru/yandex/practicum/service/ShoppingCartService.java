package ru.yandex.practicum.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.dto.ChangeProductQuantityRequest;
import ru.yandex.practicum.dto.ShoppingCartDto;
import ru.yandex.practicum.exception.NoProductsInShoppingCartException;
import ru.yandex.practicum.exception.NotAuthorizedUserException;
import ru.yandex.practicum.feign.WarehouseClient;
import ru.yandex.practicum.model.ShoppingCart;
import ru.yandex.practicum.repository.ShoppingCartRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShoppingCartService {

    private final ShoppingCartRepository cartRepository;
    private final WarehouseClient warehouseClient;

    public ShoppingCartDto getShoppingCart(String username) {
        validateUsername(username);
        return toDto(findOrCreateCart(username));
    }

    public ShoppingCartDto addProductToShoppingCart(String username, Map<UUID, Long> products) {
        validateUsername(username);
        ShoppingCart cart = findOrCreateCart(username);
        products.forEach((id, qty) -> cart.getProducts().merge(id, qty, Long::sum));
        warehouseClient.checkProductQuantityEnoughForShoppingCart(toDto(cart));
        cartRepository.save(cart);
        return toDto(cart);
    }

    public void deactivateCurrentShoppingCart(String username) {
        validateUsername(username);
        cartRepository.findByUsernameAndActiveTrue(username).ifPresent(cart -> {
            cart.setActive(false);
            cartRepository.save(cart);
        });
    }

    public ShoppingCartDto removeFromShoppingCart(String username, List<UUID> productIds) {
        validateUsername(username);
        ShoppingCart cart = cartRepository.findByUsernameAndActiveTrue(username)
                .orElseThrow(() -> new NoProductsInShoppingCartException("Активная корзина не найдена"));
        for (UUID productId : productIds) {
            if (!cart.getProducts().containsKey(productId)) {
                throw new NoProductsInShoppingCartException("Товар не найден в корзине: " + productId);
            }
        }
        productIds.forEach(cart.getProducts()::remove);
        cartRepository.save(cart);
        return toDto(cart);
    }

    public ShoppingCartDto changeProductQuantity(String username, ChangeProductQuantityRequest request) {
        validateUsername(username);
        ShoppingCart cart = cartRepository.findByUsernameAndActiveTrue(username)
                .orElseThrow(() -> new NoProductsInShoppingCartException("Активная корзина не найдена"));
        if (!cart.getProducts().containsKey(request.getProductId())) {
            throw new NoProductsInShoppingCartException("Товар не найден в корзине: " + request.getProductId());
        }
        cart.getProducts().put(request.getProductId(), request.getNewQuantity());
        cartRepository.save(cart);
        return toDto(cart);
    }

    private ShoppingCart findOrCreateCart(String username) {
        return cartRepository.findByUsernameAndActiveTrue(username).orElseGet(() -> {
            ShoppingCart newCart = new ShoppingCart();
            newCart.setUsername(username);
            return cartRepository.save(newCart);
        });
    }

    private void validateUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new NotAuthorizedUserException("Имя пользователя не указано");
        }
    }

    private ShoppingCartDto toDto(ShoppingCart cart) {
        return new ShoppingCartDto(cart.getShoppingCartId(), cart.getProducts());
    }
}
