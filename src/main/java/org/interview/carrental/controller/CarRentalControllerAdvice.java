package org.interview.carrental.controller;

import org.interview.carrental.dto.ErrorResponse;
import org.interview.carrental.exception.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class CarRentalControllerAdvice {
    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(InvalidRequestException ex) {
        return ResponseEntity
                .badRequest()
                .body(new ErrorResponse(ex.getMessage(), 400));
    }

    @ExceptionHandler({
            CarNotFoundException.class,
            ReservationNotFoundException.class
    })
    public ResponseEntity<ErrorResponse> handleNotFound(CarRentalException ex) {
        return ResponseEntity
                .status(404)
                .body(new ErrorResponse(ex.getMessage(), 404));
    }

    @ExceptionHandler(CarNotAvailableException.class)
    public ResponseEntity<ErrorResponse> handleConflict(CarNotAvailableException ex) {
        return ResponseEntity
                .status(409)
                .body(new ErrorResponse(ex.getMessage(), 409));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        return ResponseEntity
                .status(500)
                .body(new ErrorResponse("Internal server error", 500));
    }
}
