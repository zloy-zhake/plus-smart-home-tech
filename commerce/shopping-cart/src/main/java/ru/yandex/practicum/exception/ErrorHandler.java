package ru.yandex.practicum.exception;

import feign.FeignException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ErrorHandler {

    @ExceptionHandler
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, String> handleNotAuthorized(NotAuthorizedUserException e) {
        return Map.of(
                "userMessage", e.getMessage(),
                "httpStatus", HttpStatus.UNAUTHORIZED.name()
        );
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleNoProducts(NoProductsInShoppingCartException e) {
        return Map.of(
                "userMessage", e.getMessage(),
                "httpStatus", HttpStatus.BAD_REQUEST.name()
        );
    }

    // Пробрасываем ошибку от warehouse (например, недостаточно товара) клиенту как есть
    @ExceptionHandler
    public ResponseEntity<String> handleFeignException(FeignException e) {
        return ResponseEntity.status(e.status()).body(e.contentUTF8());
    }
}
