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
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNoDeliveryFound(NoDeliveryFoundException e) {
        return new ErrorResponse(e.getMessage(), HttpStatus.NOT_FOUND.name());
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleFeignException(FeignException e) {
        HttpStatus status = HttpStatus.resolve(e.status());
        String statusName = status != null ? status.name() : String.valueOf(e.status());
        return ResponseEntity.status(e.status())
                .body(new ErrorResponse(e.getMessage(), statusName));
    }
}
