package ru.yandex.practicum.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ErrorHandler {

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleAlreadyInWarehouse(SpecifiedProductAlreadyInWarehouseException e) {
        return Map.of(
                "userMessage", e.getMessage(),
                "httpStatus", HttpStatus.BAD_REQUEST.name()
        );
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleNotInWarehouse(NoSpecifiedProductInWarehouseException e) {
        return Map.of(
                "userMessage", e.getMessage(),
                "httpStatus", HttpStatus.BAD_REQUEST.name()
        );
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleLowQuantity(ProductInShoppingCartLowQuantityInWarehouse e) {
        return Map.of(
                "userMessage", e.getMessage(),
                "httpStatus", HttpStatus.BAD_REQUEST.name()
        );
    }
}
