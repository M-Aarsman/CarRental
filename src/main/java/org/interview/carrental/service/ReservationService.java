package org.interview.carrental.service;

import lombok.RequiredArgsConstructor;
import org.interview.carrental.config.CarRentalConfig;
import org.interview.carrental.dto.ReservationRequest;
import org.interview.carrental.dto.ReservationResponse;
import org.interview.carrental.exception.CarNotAvailableException;
import org.interview.carrental.exception.CarNotFoundException;
import org.interview.carrental.exception.InvalidRequestException;
import org.interview.carrental.exception.ReservationNotFoundException;
import org.interview.carrental.model.Car;
import org.interview.carrental.model.Customer;
import org.interview.carrental.model.Reservation;
import org.interview.carrental.model.ReservationStatus;
import org.interview.carrental.repository.CarRepository;
import org.interview.carrental.repository.CustomerRepository;
import org.interview.carrental.repository.ReservationRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final CarRepository carRepository;
    private final CustomerRepository customerRepository;
    private final ReservationRepository reservationRepository;
    private final CarRentalConfig carRentalConfig;

    public ReservationResponse createReservation(ReservationRequest request) {
        validateCreationRequest(request);

        Car carToReserve;
        if(request.carId() != null) {
            validateRequestedCarAvailability(request);
            carToReserve = carRepository.findById(request.carId()).get();
        } else {
            carToReserve = findAvailableCarsForRequestedType(request);
        }
        var customer = findCustomer(request);
        var end = request.start().plusDays(request.days());
        var totalPrice =  carRentalConfig.getPricing().get(carToReserve.getType()).multiply(BigDecimal.valueOf(request.days()));

        var reservation = Reservation.create(carToReserve,
                customer,
                request.start(),
                end,
                totalPrice);

        reservationRepository.save(reservation);

        return new ReservationResponse(reservation.getId(),
                carToReserve.getBrand(),
                carToReserve.getModel(),
                carToReserve.getType(),
                request.start(),
                end,
                totalPrice,
                customer.getName());
    }

    private Car findAvailableCarsForRequestedType(ReservationRequest request) {
        var carInRequestedType = carRepository.findByType(request.carType());
        return carInRequestedType.stream()
                .filter(car -> isCarAvailable(car.getId(), request))
                .findFirst()
                .orElseThrow(() -> new CarNotAvailableException("No car available"));

    }

    public void cancelReservation(UUID id) {
        validateCancellationRequest(id);

        Optional<Reservation> reservationOpt = reservationRepository.findById(id);
        validateCancellationAvailability(reservationOpt, id);

        var reservation = reservationOpt.orElseThrow();

        if(reservation.isActive()) {
            reservation.cancel();
        }
        reservationRepository.save(reservation);
    }

    private void validateCreationRequest(ReservationRequest request) {
        if(request.carType() == null && request.carId() == null) {
            throw new InvalidRequestException("nor cartType none cartId was provided");
        }

        if(request.start() == null || request.days() < 1) {
            throw new InvalidRequestException("given time range is not sufficient");
        }

        if(request.customerName().isBlank() || request.email().isBlank() || request.licenseNumber().isBlank()) {
            throw new InvalidRequestException("unsufficient Customer Data");
        }
    }

    private void validateCancellationRequest(UUID id) {
        if(id == null) {
            throw new InvalidRequestException("ID must be provided for cancellation");
        }
    }

    private void validateCancellationAvailability(Optional<Reservation> reservation, UUID id) {
        if(reservation.isEmpty()) {
            throw new ReservationNotFoundException("reservation with id: " + id + "not found");
        }
        if(reservation.get().getStatus().equals(ReservationStatus.COMPLETED)) {
            throw new InvalidRequestException(("Reservation with id: " + id + " is completed and can not be canceled"));
        }
    }

    private void validateRequestedCarAvailability(ReservationRequest request) {

        var carId = request.carId();
        var carType = request.carType();
        var car = carRepository.findById(carId).orElse(null);

        if(car == null) {
            throw new CarNotFoundException("Car with give ID: " + carId + " not found");
        }

        if (carType != null && !car.getType().equals(carType)) {
            throw new InvalidRequestException("car type is incorrect with one saved to that carId: " + carId);
        }

        if(!isCarAvailable(carId, request)) {
            throw new CarNotAvailableException("requested car " + carId + " is not available in time range");
        }
    }

    private boolean isCarAvailable(UUID carId, ReservationRequest request) {

        var requestEndTime = request.start().plusDays(request.days());
        var reservationsWithCar = reservationRepository.findByCarId(carId);

        return  reservationsWithCar.stream()
                .filter(reservation -> ! reservation.getStatus().equals(ReservationStatus.CANCELLED))
                .noneMatch(reservation -> request.start().isBefore(reservation.getEnd().plusHours(carRentalConfig.getCleaningBufferHours()))
                        && requestEndTime.isAfter(reservation.getStart()));
    }

    private Customer findCustomer(ReservationRequest request) {
        var customer  = customerRepository.findByEmailAndLicenceNumber(request.email(), request.licenseNumber());
        if(customer.isEmpty()) {
            return customerRepository.save(Customer.create(request.customerName(), request.email(), request.licenseNumber()));
        }

        if(!customer.get().getName().equalsIgnoreCase(request.customerName())) {
            throw new InvalidRequestException("Customer with given license already exist and do not match provided data");
        }

        return customer.get();
    }
}
