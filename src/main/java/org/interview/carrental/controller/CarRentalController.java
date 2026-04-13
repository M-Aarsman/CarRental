package org.interview.carrental.controller;

import lombok.AllArgsConstructor;
import org.interview.carrental.dto.AvailableCarsRequest;
import org.interview.carrental.dto.AvailableCarsResponse;
import org.interview.carrental.dto.ReservationRequest;
import org.interview.carrental.dto.ReservationResponse;
import org.interview.carrental.model.Car;
import org.interview.carrental.model.CarType;
import org.interview.carrental.service.CarService;
import org.interview.carrental.service.ReservationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({"", "/"})
@AllArgsConstructor
public class CarRentalController {

    private final CarService carService;
    private final ReservationService reservationService;

    @GetMapping("/cars/available")
    public AvailableCarsResponse getAvailableCars(@ModelAttribute AvailableCarsRequest request) {
        return carService.findAvailableCars(request);
    }

    @PostMapping("/reservations")
    public ReservationResponse createReservation(
            @RequestBody ReservationRequest request
    ) {
        return reservationService.createReservation(request);
    }

    @DeleteMapping("/reservations/{id}")
    public ResponseEntity<Void> cancelReservation(@PathVariable UUID id) {
        reservationService.cancelReservation(id);
        return ResponseEntity.noContent().build();
    }
}
