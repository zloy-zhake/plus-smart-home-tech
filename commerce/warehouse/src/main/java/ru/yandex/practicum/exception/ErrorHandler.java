package ru.yandex.practicum.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.yandex.practicum.dto.ErrorResponse;

@RestControllerAdvice
public class ErrorHandler {

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleAlreadyInWarehouse(SpecifiedProductAlreadyInWarehouseException e) {
        return new ErrorResponse(e.getMessage(), HttpStatus.BAD_REQUEST.name());
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleNotInWarehouse(NoSpecifiedProductInWarehouseException e) {
        return new ErrorResponse(e.getMessage(), HttpStatus.BAD_REQUEST.name());
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleLowQuantity(ProductInShoppingCartLowQuantityInWarehouse e) {
        return new ErrorResponse(e.getMessage(), HttpStatus.BAD_REQUEST.name());
    }
}
