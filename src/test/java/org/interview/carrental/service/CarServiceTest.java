package org.interview.carrental.service;

import org.interview.carrental.config.CarRentalConfig;
import org.interview.carrental.dto.AvailableCarsRequest;
import org.interview.carrental.dto.AvailableCarsResponse;
import org.interview.carrental.dto.CarOffer;
import org.interview.carrental.exception.InvalidRequestException;
import org.interview.carrental.model.Car;
import org.interview.carrental.model.CarType;
import org.interview.carrental.model.Customer;
import org.interview.carrental.model.Reservation;
import org.interview.carrental.model.ReservationStatus;
import org.interview.carrental.repository.CarRepository;
import org.interview.carrental.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CarServiceTest {

    private static final LocalDateTime REQUEST_START = LocalDateTime.now().plusDays(4);

    @Mock
    private CarRepository carRepository;

    @Mock
    private ReservationRepository reservationRepository;

    private CarService carService;

    @BeforeEach
    void setUp() {
        CarRentalConfig config = new CarRentalConfig(Map.of(
                CarType.SEDAN, new BigDecimal("90.00"),
                CarType.SUV, new BigDecimal("120.00"),
                CarType.VAN, new BigDecimal("140.00")
        ));
        carService = new CarService(carRepository, reservationRepository, config);
    }

    @Nested
    @DisplayName("Validation")
    class ValidationTests {

        @Test
        void shouldRejectNullStart() {
            assertThatThrownBy(() -> carService.findAvailableCars(request(List.of(CarType.SUV), null, null, null, 3)))
                    .isInstanceOf(InvalidRequestException.class);

            verifyNoInteractions(carRepository, reservationRepository);
        }

        @Test
        void shouldRejectZeroDays() {
            assertThatThrownBy(() -> carService.findAvailableCars(request(List.of(CarType.SUV), null, null, REQUEST_START, 0)))
                    .isInstanceOf(InvalidRequestException.class);

            verifyNoInteractions(carRepository, reservationRepository);
        }

        @Test
        void shouldRejectNegativeDays() {
            assertThatThrownBy(() -> carService.findAvailableCars(request(List.of(CarType.SUV), null, null, REQUEST_START, -1)))
                    .isInstanceOf(InvalidRequestException.class);

            verifyNoInteractions(carRepository, reservationRepository);
        }
    }

    @Nested
    @DisplayName("Availability")
    class AvailabilityTests {

        @Test
        void shouldReturnOffersForRequestedTypeWithCalculatedPrices() {
            Car volvo = car(CarType.SUV, "Volvo", "XC60");
            Car toyota = car(CarType.SUV, "Toyota", "RAV4");

            when(carRepository.findByTypeIn(List.of(CarType.SUV))).thenReturn(List.of(volvo, toyota));
            stubNoBlockingReservations(volvo, toyota);

            AvailableCarsResponse response = carService.findAvailableCars(
                    request(List.of(CarType.SUV), null, null, REQUEST_START, 3)
            );

            assertThat(response.offers()).hasSize(2);
            assertThat(response.offers())
                    .extracting(CarOffer::brand, CarOffer::model, CarOffer::pricePerDay, CarOffer::totalPrice)
                    .containsExactlyInAnyOrder(
                            tuple("Volvo", "XC60", new BigDecimal("120.00"), new BigDecimal("360.00")),
                            tuple("Toyota", "RAV4", new BigDecimal("120.00"), new BigDecimal("360.00"))
                    );

            verify(carRepository).findByTypeIn(List.of(CarType.SUV));
            verify(reservationRepository).findByCarIdAndStatusNot(volvo.getId(), ReservationStatus.CANCELLED);
            verify(reservationRepository).findByCarIdAndStatusNot(toyota.getId(), ReservationStatus.CANCELLED);
        }

        @Test
        void shouldFilterByBrandAndModelWhenTypesAreNotProvided() {
            Car audiA4 = car(CarType.SEDAN, "Audi", "A4");
            Car audiA6 = car(CarType.SEDAN, "Audi", "A6");
            Car bmw = car(CarType.SEDAN, "BMW", "3 Series");

            when(carRepository.findAll()).thenReturn(List.of(audiA4, audiA6, bmw));

            AvailableCarsResponse response = carService.findAvailableCars(
                    request(null, "Audi", "A4", REQUEST_START, 2)
            );

            assertThat(response.offers()).singleElement().satisfies(offer -> {
                assertThat(offer.brand()).isEqualTo("Audi");
                assertThat(offer.model()).isEqualTo("A4");
                assertThat(offer.type()).isEqualTo(CarType.SEDAN);
                assertThat(offer.pricePerDay()).isEqualByComparingTo("90.00");
                assertThat(offer.totalPrice()).isEqualByComparingTo("180.00");
            });

            verify(carRepository).findAll();
        }

        @Test
        void shouldReturnMixedTypeOffersWithPerCarPricing() {
            Car suv = car(CarType.SUV, "Volvo", "XC60");
            Car van = car(CarType.VAN, "Mercedes", "Vito");

            when(carRepository.findByTypeIn(List.of(CarType.SUV, CarType.VAN))).thenReturn(List.of(suv, van));
            stubNoBlockingReservations(suv, van);

            AvailableCarsResponse response = carService.findAvailableCars(
                    request(List.of(CarType.SUV, CarType.VAN), null, null, REQUEST_START, 2)
            );

            assertThat(response.offers())
                    .extracting(CarOffer::type, CarOffer::pricePerDay, CarOffer::totalPrice)
                    .containsExactlyInAnyOrder(
                            tuple(CarType.SUV, new BigDecimal("120.00"), new BigDecimal("240.00")),
                            tuple(CarType.VAN, new BigDecimal("140.00"), new BigDecimal("280.00"))
                    );
        }

        @Test
        void shouldReturnEmptyOffersWhenNoCarsMatchCriteria() {
            when(carRepository.findByTypeIn(List.of(CarType.VAN))).thenReturn(List.of());

            AvailableCarsResponse response = carService.findAvailableCars(
                    request(List.of(CarType.VAN), null, null, REQUEST_START, 1)
            );

            assertThat(response.offers()).isEmpty();
            verify(carRepository).findByTypeIn(List.of(CarType.VAN));
            verifyNoInteractions(reservationRepository);
        }

        @Test
        void shouldReturnEmptyOffersWhenAllCarsAreUnavailable() {
            Car suvOne = car(CarType.SUV, "Volvo", "XC60");
            Car suvTwo = car(CarType.SUV, "Toyota", "RAV4");

            when(carRepository.findByTypeIn(List.of(CarType.SUV))).thenReturn(List.of(suvOne, suvTwo));
            when(reservationRepository.findByCarIdAndStatusNot(eq(suvOne.getId()), eq(ReservationStatus.CANCELLED)))
                    .thenReturn(List.of(reservation(suvOne, REQUEST_START.minusHours(1), REQUEST_START.plusDays(2))));
            when(reservationRepository.findByCarIdAndStatusNot(eq(suvTwo.getId()), eq(ReservationStatus.CANCELLED)))
                    .thenReturn(List.of(reservation(suvTwo, REQUEST_START.minusDays(1), REQUEST_START.plusHours(1))));

            AvailableCarsResponse response = carService.findAvailableCars(
                    request(List.of(CarType.SUV), null, null, REQUEST_START, 2)
            );

            assertThat(response.offers()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Cleaning buffer and overlap rules")
    class BufferRuleTests {

        @Test
        void shouldExcludeCarWhenReservationOverlapsRequestedWindow() {
            Car car = car(CarType.SUV, "Volvo", "XC60");

            when(carRepository.findByTypeIn(List.of(CarType.SUV))).thenReturn(List.of(car));
            when(reservationRepository.findByCarIdAndStatusNot(eq(car.getId()), eq(ReservationStatus.CANCELLED)))
                    .thenReturn(List.of(reservation(car, REQUEST_START.plusHours(4), REQUEST_START.plusDays(1))));

            AvailableCarsResponse response = carService.findAvailableCars(
                    request(List.of(CarType.SUV), null, null, REQUEST_START, 3)
            );

            assertThat(response.offers()).isEmpty();
        }

        @Test
        void shouldExcludeCarWhenRequestedStartFallsInsideCleaningBuffer() {
            Car car = car(CarType.SUV, "Volvo", "XC60");

            when(carRepository.findByTypeIn(List.of(CarType.SUV))).thenReturn(List.of(car));
            when(reservationRepository.findByCarIdAndStatusNot(eq(car.getId()), eq(ReservationStatus.CANCELLED)))
                    .thenReturn(List.of(reservation(
                            car,
                            REQUEST_START.minusDays(1),
                            REQUEST_START.minusHours(1)
                    )));

            AvailableCarsResponse response = carService.findAvailableCars(
                    request(List.of(CarType.SUV), null, null, REQUEST_START, 1)
            );

            assertThat(response.offers()).isEmpty();
        }

        @Test
        void shouldReturnCarWhenRequestedStartIsAfterReservationEndPlusCleaningBuffer() {
            Car car = car(CarType.SUV, "Volvo", "XC60");

            when(carRepository.findByTypeIn(List.of(CarType.SUV))).thenReturn(List.of(car));
            when(reservationRepository.findByCarIdAndStatusNot(eq(car.getId()), eq(ReservationStatus.CANCELLED)))
                    .thenReturn(List.of(reservation(
                            car,
                            REQUEST_START.minusDays(1),
                            REQUEST_START.minusHours(3)
                    )));

            AvailableCarsResponse response = carService.findAvailableCars(
                    request(List.of(CarType.SUV), null, null, REQUEST_START, 1)
            );

            assertThat(response.offers()).singleElement().satisfies(offer ->
                    assertThat(offer.brand()).isEqualTo("Volvo"));
        }

        @Test
        void shouldIgnoreCancelledReservations() {
            Car car = car(CarType.SUV, "Volvo", "XC60");
            Reservation cancelledReservation = reservation(
                    car,
                    REQUEST_START.minusDays(1),
                    REQUEST_START.plusDays(1)
            );
            cancelledReservation.cancel();

            when(carRepository.findByTypeIn(List.of(CarType.SUV))).thenReturn(List.of(car));
            when(reservationRepository.findByCarIdAndStatusNot(eq(car.getId()), eq(ReservationStatus.CANCELLED)))
                    .thenReturn(List.of());

            AvailableCarsResponse response = carService.findAvailableCars(
                    request(List.of(CarType.SUV), null, null, REQUEST_START, 1)
            );

            assertThat(cancelledReservation.isActive()).isFalse();
            assertThat(response.offers()).hasSize(1);
        }

        @Test
        void shouldTreatCompletedReservationsAsBlockingUntilBufferExpires() {
            Car car = car(CarType.SUV, "Volvo", "XC60");
            Reservation completedReservation = reservation(
                    car,
                    REQUEST_START.minusDays(2),
                    REQUEST_START.minusHours(1)
            );
            ReflectionTestUtils.setField(completedReservation, "status", ReservationStatus.COMPLETED);

            when(carRepository.findByTypeIn(List.of(CarType.SUV))).thenReturn(List.of(car));
            when(reservationRepository.findByCarIdAndStatusNot(eq(car.getId()), eq(ReservationStatus.CANCELLED)))
                    .thenReturn(List.of(completedReservation));

            AvailableCarsResponse response = carService.findAvailableCars(
                    request(List.of(CarType.SUV), null, null, REQUEST_START, 1)
            );

            assertThat(response.offers()).isEmpty();
        }

        @Test
        void shouldExcludeCarWhenNextReservationStartsBeforeCleaningBufferAfterRequestedEnd() {
            Car car = car(CarType.SUV, "Volvo", "XC60");
            LocalDateTime requestedEnd = REQUEST_START.plusDays(2);

            when(carRepository.findByTypeIn(List.of(CarType.SUV))).thenReturn(List.of(car));
            when(reservationRepository.findByCarIdAndStatusNot(eq(car.getId()), eq(ReservationStatus.CANCELLED)))
                    .thenReturn(List.of(reservation(
                            car,
                            requestedEnd,
                            requestedEnd.plusDays(1)
                    )));

            AvailableCarsResponse response = carService.findAvailableCars(
                    request(List.of(CarType.SUV), null, null, REQUEST_START, 2)
            );

            assertThat(response.offers()).isEmpty();
        }

        @Test
        void shouldAllowCarWhenNextReservationStartsExactlyAtCleaningBufferBoundaryAfterRequestedEnd() {
            Car car = car(CarType.SUV, "Volvo", "XC60");
            LocalDateTime requestedEnd = REQUEST_START.plusDays(2);

            when(carRepository.findByTypeIn(List.of(CarType.SUV))).thenReturn(List.of(car));
            when(reservationRepository.findByCarIdAndStatusNot(eq(car.getId()), eq(ReservationStatus.CANCELLED)))
                    .thenReturn(List.of(reservation(
                            car,
                            requestedEnd.plusHours(2),
                            requestedEnd.plusDays(1)
                    )));

            AvailableCarsResponse response = carService.findAvailableCars(
                    request(List.of(CarType.SUV), null, null, REQUEST_START, 2)
            );

            assertThat(response.offers()).hasSize(1);
        }
    }

    private void stubNoBlockingReservations(Car... cars) {
        for (Car car : cars) {
            when(reservationRepository.findByCarIdAndStatusNot(eq(car.getId()), eq(ReservationStatus.CANCELLED)))
                    .thenReturn(List.of());
        }
    }

    private Car car(CarType type, String brand, String model) {
        return Car.create(type, brand, model);
    }

    private AvailableCarsRequest request(
            List<CarType> types,
            String brand,
            String model,
            LocalDateTime start,
            int days
    ) {
        return new AvailableCarsRequest(types, brand, model, null, start, days);
    }

    private Reservation reservation(Car car, LocalDateTime start, LocalDateTime end) {
        return Reservation.create(
                car,
                Customer.create("Anna", "anna@mail.com", "XYZ123"),
                start,
                end,
                BigDecimal.ZERO
        );
    }
}
