package com.agrosense.frontend.exception;

import com.agrosense.frontend.dto.Views.ApiMessage;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final String API_PREFIX = "/api/";

    @ExceptionHandler({NotFoundException.class, NoResourceFoundException.class,
            MethodArgumentTypeMismatchException.class})
    public Object handleNotFound(HttpServletRequest request) {
        return respond(request, HttpStatus.NOT_FOUND, "No encontramos lo que buscabas.");
    }

    @ExceptionHandler(BusinessRuleException.class)
    public Object handleBusinessRule(BusinessRuleException exception, HttpServletRequest request) {
        return respond(request, HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Object handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .findFirst()
                .orElse("Revisa los datos enviados.");
        return respond(request, HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Object handleUnreadable(HttpServletRequest request) {
        return respond(request, HttpStatus.BAD_REQUEST, "Revisa los datos enviados.");
    }

    @ExceptionHandler(Exception.class)
    public Object handleUnexpected(Exception exception, HttpServletRequest request) throws Exception {
        // Spring Security turns its own exceptions into redirects and 403 responses.
        if (exception instanceof AccessDeniedException || exception instanceof AuthenticationException) {
            throw exception;
        }
        log.error("Unexpected error handling {} {}", request.getMethod(), request.getRequestURI(), exception);
        return respond(request, HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocurrió un error inesperado. Inténtalo de nuevo.");
    }

    private Object respond(HttpServletRequest request, HttpStatus status, String message) {
        if (request.getRequestURI().startsWith(request.getContextPath() + API_PREFIX)) {
            return ResponseEntity.status(status).body(new ApiMessage(message));
        }
        ModelAndView view = new ModelAndView("error");
        view.setStatus(status);
        view.addObject("status", status.value());
        view.addObject("message", message);
        return view;
    }
}
