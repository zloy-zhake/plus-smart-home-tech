package ru.yandex.practicum.exception;

import feign.FeignException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.yandex.practicum.dto.ErrorResponse;

@RestControllerAdvice
public class ErrorHandler {

    @ExceptionHandler
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleNotAuthorized(NotAuthorizedUserException e) {
        return new ErrorResponse(e.getMessage(), HttpStatus.UNAUTHORIZED.name());
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleNoProducts(NoProductsInShoppingCartException e) {
        return new ErrorResponse(e.getMessage(), HttpStatus.BAD_REQUEST.name());
    }

    // Пробрасываем ошибку от warehouse (например, недостаточно товара) клиенту как есть
    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleFeignException(FeignException e) {
        HttpStatus status = HttpStatus.resolve(e.status());
        String statusName = status != null ? status.name() : String.valueOf(e.status());
        return ResponseEntity.status(e.status())
                .body(new ErrorResponse(e.getMessage(), statusName));
    }
}
