package org.interview.carrental.service;

import lombok.RequiredArgsConstructor;
import org.interview.carrental.config.CarRentalConfig;
import org.interview.carrental.dto.AvailableCarsRequest;
import org.interview.carrental.dto.AvailableCarsResponse;
import org.interview.carrental.dto.CarOffer;
import org.interview.carrental.exception.InvalidRequestException;
import org.interview.carrental.model.Car;
import org.interview.carrental.model.CarType;
import org.interview.carrental.model.Reservation;
import org.interview.carrental.model.ReservationStatus;
import org.interview.carrental.repository.CarRepository;
import org.interview.carrental.repository.ReservationRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CarService {
    private final CarRepository carRepository;
    private final ReservationRepository reservationRepository;

    private final CarRentalConfig config;

    public AvailableCarsResponse findAvailableCars(AvailableCarsRequest request) {
        validateRequest(request);

        LocalDateTime start = request.getStart();
        int days = request.getDays();
        LocalDateTime end = start.plusDays(days);

        if (request.getCarId() != null) {
            return getSpecificRequestedCar(request.getCarId(), start, end, days);
        }

        List<Car> cars = getBaseCars(request);
        cars = applyFilters(cars, request);

        List<Car> availableCars = cars.stream()
                .filter(car -> isCarAvailable(car, start, end))
                .toList();
        List<CarOffer> carOffers = availableCars.stream().map(car -> createCarOffer(car, days)).toList();

        return new AvailableCarsResponse(carOffers);
    }

    private void validateRequest(AvailableCarsRequest request) {
        if(request.getStart() == null || request.getDays() < 1 || request.getStart().isBefore(LocalDateTime.now())) {
            throw new InvalidRequestException("Provided time range is incorrect");
        }
    }

    private List<Car> getBaseCars(AvailableCarsRequest request) {
        if (request.getCarId() != null) {
            return carRepository.findById(request.getCarId())
                    .map(List::of)
                    .orElse(Collections.emptyList());
        }

        if (request.getTypes() != null && !request.getTypes().isEmpty()) {
            return carRepository.findByTypeIn(request.getTypes());
        }

        return carRepository.findAll();
    }

    private AvailableCarsResponse getSpecificRequestedCar(UUID carId, LocalDateTime start, LocalDateTime end, int days) {
        return carRepository.findById(carId)
                .filter(car -> isCarAvailable(car, start, end))
                .map(car -> createCarOffer(car, days))
                .map(carOffer -> new AvailableCarsResponse(List.of(carOffer)))
                .orElse(new AvailableCarsResponse(Collections.emptyList()));
    }

    private CarOffer createCarOffer(Car car, int reservationDays) {
        BigDecimal price = config.getPricing().get(car.getType());
        BigDecimal totalPrice = price.multiply(BigDecimal.valueOf(reservationDays));
        return new CarOffer(car.getId(),
                car.getType(),
                car.getBrand(),
                car.getModel(),
                price,
                totalPrice);
    }

    private boolean isCarAvailable(Car car, LocalDateTime start, LocalDateTime end) {
        List<Reservation> reservations =
                reservationRepository.findByCarIdAndStatusNot(
                        car.getId(),
                        ReservationStatus.CANCELLED
                );
        return reservations.stream()
                .noneMatch(reservation -> start.isBefore(reservation.getEnd().plusHours(config.getCleaningBufferHours()))
                                                    && end.plusHours(config.getCleaningBufferHours()).isAfter(reservation.getStart()));
    }

    private List<Car> applyFilters(List<Car> cars, AvailableCarsRequest request) {
        return cars.stream()
                .filter(car -> request.getBrand() == null ||
                        car.getBrand().equalsIgnoreCase(request.getBrand()))
                .filter(car -> request.getModel() == null ||
                        car.getModel().equalsIgnoreCase(request.getModel()))
                .toList();
    }
}
