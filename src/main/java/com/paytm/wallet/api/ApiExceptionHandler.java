package com.paytm.wallet.api;

import com.paytm.wallet.dto.ApiModels.ErrorResponse;
import com.paytm.wallet.service.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(BadRequestException.class) 
    ResponseEntity<ErrorResponse> bad(BadRequestException e) { 
        return error(HttpStatus.BAD_REQUEST, e.code, e.getMessage()); 
    }
    @ExceptionHandler(ConflictException.class) 
    ResponseEntity<ErrorResponse> conflict(ConflictException e) { 
        return error(HttpStatus.CONFLICT, e.code, e.getMessage()); 
    }
    @ExceptionHandler(ForbiddenException.class) 
    ResponseEntity<ErrorResponse> forbidden(ForbiddenException e) { 
        return error(HttpStatus.FORBIDDEN, e.code, e.getMessage()); 
    }
    @ExceptionHandler(NotFoundException.class) 
    ResponseEntity<ErrorResponse> missing(NotFoundException e) { 
        return error(HttpStatus.NOT_FOUND, e.code, e.getMessage()); 
    }
    @ExceptionHandler(MethodArgumentNotValidException.class) 
    ResponseEntity<ErrorResponse> invalid(MethodArgumentNotValidException e) { 
        return error(HttpStatus.BAD_REQUEST, "validation_error", "Check required fields and amount_paise > 0"); 
    }
    private ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message) { 
        return ResponseEntity.status(status).body(new ErrorResponse(code, message)); 
    }
}
