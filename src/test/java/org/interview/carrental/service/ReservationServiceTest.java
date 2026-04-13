package org.interview.carrental.service;

import org.interview.carrental.config.CarRentalConfig;
import org.interview.carrental.dto.ReservationRequest;
import org.interview.carrental.dto.ReservationResponse;
import org.interview.carrental.exception.CarNotAvailableException;
import org.interview.carrental.exception.CarNotFoundException;
import org.interview.carrental.exception.InvalidRequestException;
import org.interview.carrental.exception.ReservationNotFoundException;
import org.interview.carrental.model.Car;
import org.interview.carrental.model.CarType;
import org.interview.carrental.model.Customer;
import org.interview.carrental.model.Reservation;
import org.interview.carrental.model.ReservationStatus;
import org.interview.carrental.repository.CarRepository;
import org.interview.carrental.repository.CustomerRepository;
import org.interview.carrental.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Reservation service")
class ReservationServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 4, 13, 12, 0);
    private static final LocalDateTime REQUEST_START = NOW.plusDays(2);

    @Mock
    private CarRepository carRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private ReservationRepository reservationRepository;

    private CarRentalConfig carRentalConfig;

    private ReservationService reservationService;

    @Nested
    @DisplayName("Validation")
    class ValidationTests {

        @BeforeEach
        void setUp() {
            reservationService = new ReservationService(
                    carRepository,
                    customerRepository,
                    reservationRepository,
                    carRentalConfig
            );
        }

        @Test
        void shouldRejectWhenNeitherCarIdNorCarTypeIsProvided() {
            ReservationRequest request = new ReservationRequest(
                    null,
                    null,
                    "Anna",
                    "anna@example.com",
                    "LIC-123",
                    REQUEST_START,
                    2
            );

            assertThatThrownBy(() -> reservationService.createReservation(request))
                    .isInstanceOf(InvalidRequestException.class);

            verifyNoInteractions(carRepository, customerRepository, reservationRepository);
        }

        @Test
        void shouldRejectWhenCustomerNameIsMissing() {
            assertThatThrownBy(() -> reservationService.createReservation(new ReservationRequest(
                    null,
                    CarType.SUV,
                    " ",
                    "anna@example.com",
                    "LIC-123",
                    REQUEST_START,
                    2
            )))
                    .isInstanceOf(InvalidRequestException.class);

            verifyNoInteractions(carRepository, customerRepository, reservationRepository);
        }

        @Test
        void shouldRejectWhenEmailIsMissing() {
            assertThatThrownBy(() -> reservationService.createReservation(new ReservationRequest(
                    null,
                    CarType.SUV,
                    "Anna",
                    " ",
                    "LIC-123",
                    REQUEST_START,
                    2
            )))
                    .isInstanceOf(InvalidRequestException.class);

            verifyNoInteractions(carRepository, customerRepository, reservationRepository);
        }

        @Test
        void shouldRejectWhenLicenseNumberIsMissing() {
            assertThatThrownBy(() -> reservationService.createReservation(new ReservationRequest(
                    null,
                    CarType.SUV,
                    "Anna",
                    "anna@example.com",
                    " ",
                    REQUEST_START,
                    2
            )))
                    .isInstanceOf(InvalidRequestException.class);

            verifyNoInteractions(carRepository, customerRepository, reservationRepository);
        }

        @Test
        void shouldRejectWhenStartIsMissing() {
            assertThatThrownBy(() -> reservationService.createReservation(new ReservationRequest(
                    null,
                    CarType.SUV,
                    "Anna",
                    "anna@example.com",
                    "LIC-123",
                    null,
                    2
            )))
                    .isInstanceOf(InvalidRequestException.class);

            verifyNoInteractions(carRepository, customerRepository, reservationRepository);
        }

        @Test
        void shouldRejectWhenDaysIsZero() {
            assertThatThrownBy(() -> reservationService.createReservation(new ReservationRequest(
                    null,
                    CarType.SUV,
                    "Anna",
                    "anna@example.com",
                    "LIC-123",
                    REQUEST_START,
                    0
            )))
                    .isInstanceOf(InvalidRequestException.class);

            verifyNoInteractions(carRepository, customerRepository, reservationRepository);
        }

        @Test
        void shouldRejectWhenDaysIsNegative() {
            assertThatThrownBy(() -> reservationService.createReservation(new ReservationRequest(
                    null,
                    CarType.SUV,
                    "Anna",
                    "anna@example.com",
                    "LIC-123",
                    REQUEST_START,
                    -1
            )))
                    .isInstanceOf(InvalidRequestException.class);

            verifyNoInteractions(carRepository, customerRepository, reservationRepository);
        }
    }

    @Nested
    @DisplayName("Create reservation")
    class CreateReservationTests {
        @BeforeEach
        void setUp() {
            Map<CarType, BigDecimal> priceMap = Map.of(
                    CarType.SEDAN, new BigDecimal("90.00"),
                    CarType.SUV, new BigDecimal("120.00"),
                    CarType.VAN, new BigDecimal("140.00")
            );

            carRentalConfig = new CarRentalConfig(priceMap, 2);

            reservationService = new ReservationService(
                    carRepository,
                    customerRepository,
                    reservationRepository,
                    carRentalConfig
            );
        }

        @Test
        void shouldCreateReservationForSpecificCarAndExistingCustomerWhenCarIsAvailable() {
            Car car = car(CarType.SUV, "Volvo", "XC60");
            Customer customer = customer("Anna", "anna@example.com", "LIC-123");

            when(carRepository.findById(car.getId())).thenReturn(Optional.of(car));
            stubNoBlockingReservations(car);
            when(customerRepository.findByEmailAndLicenceNumber(any(), any())).thenReturn(Optional.of(customer));
            when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ReservationResponse response = reservationService.createReservation(new ReservationRequest(
                    car.getId(),
                    CarType.SUV,
                    customer.getName(),
                    customer.getEmail(),
                    "LIC-123",
                    REQUEST_START,
                    3
            ));

            assertThat(response.carBrand()).isEqualTo("Volvo");
            assertThat(response.carModel()).isEqualTo("XC60");
            assertThat(response.carType()).isEqualTo(CarType.SUV);
            assertThat(response.customerName()).isEqualTo("Anna");
            assertThat(response.start()).isEqualTo(REQUEST_START);
            assertThat(response.end()).isEqualTo(REQUEST_START.plusDays(3));
            assertThat(response.totalPrice()).isEqualByComparingTo("360.00");

            ArgumentCaptor<Reservation> reservationCaptor = ArgumentCaptor.forClass(Reservation.class);
            verify(reservationRepository).save(reservationCaptor.capture());
            assertThat(reservationCaptor.getValue().getCar()).isEqualTo(car);
            assertThat(reservationCaptor.getValue().getCustomer()).isEqualTo(customer);
            assertThat(reservationCaptor.getValue().getStatus()).isEqualTo(ReservationStatus.RESERVED);
            assertThat(reservationCaptor.getValue().getTotalPrice()).isEqualByComparingTo("360.00");

            verify(customerRepository, never()).save(any(Customer.class));
        }

        @Test
        void shouldCreateReservationForAnyAvailableCarOfRequestedTypeAndCreateCustomerWhenMissing() {
            Car unavailableCar = car(CarType.SUV, "Volvo", "XC60");
            Car availableCar = car(CarType.SUV, "Toyota", "RAV4");

            when(carRepository.findByType(CarType.SUV)).thenReturn(List.of(unavailableCar, availableCar));
            stubBlockingReservations(unavailableCar, reservation(
                    unavailableCar,
                    REQUEST_START.minusHours(1),
                    REQUEST_START.plusDays(1),
                    ReservationStatus.RESERVED
            ));
            stubNoBlockingReservations(availableCar);
            when(customerRepository.findByEmailAndLicenceNumber(any(), any())).thenReturn(Optional.empty());
            when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ReservationResponse response = reservationService.createReservation(new ReservationRequest(
                    null,
                    CarType.SUV,
                    "New Customer",
                    "new@example.com",
                    "LIC-999",
                    REQUEST_START,
                    2
            ));

            assertThat(response.carBrand()).isEqualTo("Toyota");
            assertThat(response.carModel()).isEqualTo("RAV4");
            assertThat(response.carType()).isEqualTo(CarType.SUV);
            assertThat(response.totalPrice()).isEqualByComparingTo("240.00");
            assertThat(response.end()).isEqualTo(REQUEST_START.plusDays(2));

            ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
            verify(customerRepository).save(customerCaptor.capture());
            assertThat(customerCaptor.getValue().getName()).isEqualTo("New Customer");
            assertThat(customerCaptor.getValue().getEmail()).isEqualTo("new@example.com");
            assertThat(customerCaptor.getValue().getLicenceNumber()).isEqualTo("LIC-999");
        }

        @Test
        void shouldThrowCarNotFoundWhenSpecificCarDoesNotExist() {
            UUID carId = UUID.randomUUID();
            when(carRepository.findById(carId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reservationService.createReservation(new ReservationRequest(
                    carId,
                    null,
                    "Anna",
                    "anna@example.com",
                    "LIC-123",
                    REQUEST_START,
                    2
            )))
                    .isInstanceOf(CarNotFoundException.class);

            verify(carRepository).findById(carId);
            verifyNoInteractions(customerRepository, reservationRepository);
        }

        @Test
        void shouldRejectWhenCarIdDoesNotMatchRequestedType() {
            Car car = car(CarType.SEDAN, "Audi", "A4");
            when(carRepository.findById(car.getId())).thenReturn(Optional.of(car));

            assertThatThrownBy(() -> reservationService.createReservation(new ReservationRequest(
                    car.getId(),
                    CarType.SUV,
                    "Anna",
                    "anna@example.com",
                    "LIC-123",
                    REQUEST_START,
                    2
            )))
                    .isInstanceOf(InvalidRequestException.class);

            verify(customerRepository, never()).findByEmail(any());
            verify(reservationRepository, never()).save(any(Reservation.class));
        }

        @Test
        void shouldThrowCarNotAvailableWhenNoCarsExistForRequestedType() {
            when(carRepository.findByType(CarType.VAN)).thenReturn(List.of());

            assertThatThrownBy(() -> reservationService.createReservation(new ReservationRequest(
                    null,
                    CarType.VAN,
                    "Anna",
                    "anna@example.com",
                    "LIC-123",
                    REQUEST_START,
                    2
            )))
                    .isInstanceOf(CarNotAvailableException.class);

            verify(customerRepository, never()).findByEmail(any());
            verify(reservationRepository, never()).save(any(Reservation.class));
        }

        @Test
        void shouldThrowCarNotAvailableWhenSpecificCarIsBlockedByReservedReservation() {
            Car car = car(CarType.SUV, "Volvo", "XC60");
            when(carRepository.findById(car.getId())).thenReturn(Optional.of(car));
            stubBlockingReservations(car, reservation(
                    car,
                    REQUEST_START.minusHours(3),
                    REQUEST_START.plusDays(1),
                    ReservationStatus.RESERVED
            ));

            assertThatThrownBy(() -> reservationService.createReservation(new ReservationRequest(
                    car.getId(),
                    CarType.SUV,
                    "Anna",
                    "anna@example.com",
                    "LIC-123",
                    REQUEST_START,
                    3
            )))
                    .isInstanceOf(CarNotAvailableException.class);

            verify(customerRepository, never()).findByEmail(any());
            verify(reservationRepository, never()).save(any(Reservation.class));
        }

        @Test
        void shouldThrowCarNotAvailableWhenAllCarsOfRequestedTypeAreBlocked() {
            Car first = car(CarType.SUV, "Volvo", "XC60");
            Car second = car(CarType.SUV, "Toyota", "RAV4");
            when(carRepository.findByType(CarType.SUV)).thenReturn(List.of(first, second));
            stubBlockingReservations(first, reservation(
                    first,
                    REQUEST_START.minusDays(1),
                    REQUEST_START.plusHours(1),
                    ReservationStatus.RESERVED
            ));
            stubBlockingReservations(second, reservation(
                    second,
                    REQUEST_START.minusHours(2),
                    REQUEST_START.plusDays(2),
                    ReservationStatus.RESERVED
            ));

            assertThatThrownBy(() -> reservationService.createReservation(new ReservationRequest(
                    null,
                    CarType.SUV,
                    "Anna",
                    "anna@example.com",
                    "LIC-123",
                    REQUEST_START,
                    3
            )))
                    .isInstanceOf(CarNotAvailableException.class);

            verify(customerRepository, never()).findByEmail(any());
            verify(reservationRepository, never()).save(any(Reservation.class));
        }

        @Test
        void shouldIgnoreCancelledReservationsWhenCheckingAvailability() {
            Car car = car(CarType.SUV, "Volvo", "XC60");
            Customer customer = customer("Anna", "anna@example.com", "LIC-123");

            when(carRepository.findById(car.getId())).thenReturn(Optional.of(car));
            stubReservations(car, reservation(
                    car,
                    REQUEST_START.minusHours(2),
                    REQUEST_START.plusHours(2),
                    ReservationStatus.CANCELLED
            ));
            when(customerRepository.findByEmailAndLicenceNumber(any(), any())).thenReturn(Optional.of(customer));
            when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ReservationResponse response = reservationService.createReservation(new ReservationRequest(
                    car.getId(),
                    CarType.SUV,
                    "Anna",
                    "anna@example.com",
                    "LIC-123",
                    REQUEST_START,
                    2
            ));

            assertThat(response.reservationId()).isNotNull();
            verify(reservationRepository).save(any(Reservation.class));
        }

        @Test
        void shouldRejectWhenRequestedStartFallsInsideCleaningBuffer() {
            Car car = car(CarType.VAN, "Mercedes", "Vito");
            when(carRepository.findById(car.getId())).thenReturn(Optional.of(car));
            stubBlockingReservations(car, reservation(
                    car,
                    REQUEST_START.minusDays(1),
                    REQUEST_START.minusMinutes(30),
                    ReservationStatus.RESERVED
            ));

            assertThatThrownBy(() -> reservationService.createReservation(new ReservationRequest(
                    car.getId(),
                    CarType.VAN,
                    "Anna",
                    "anna@example.com",
                    "LIC-123",
                    REQUEST_START,
                    1
            )))
                    .isInstanceOf(CarNotAvailableException.class);
        }

        @Test
        void shouldAllowReservationWhenRequestedStartIsExactlyAfterCleaningBufferBoundary() {
            Car car = car(CarType.VAN, "Mercedes", "Vito");
            Customer customer = customer("Anna", "anna@example.com", "LIC-123");

            when(carRepository.findById(car.getId())).thenReturn(Optional.of(car));
            stubReservations(car, reservation(
                    car,
                    REQUEST_START.minusDays(1).plusHours(1),
                    REQUEST_START.minusHours(2),
                    ReservationStatus.RESERVED
            ));
            when(customerRepository.findByEmailAndLicenceNumber(any(), any())).thenReturn(Optional.of(customer));
            when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ReservationResponse response = reservationService.createReservation(new ReservationRequest(
                    car.getId(),
                    CarType.VAN,
                    "Anna",
                    "anna@example.com",
                    "LIC-123",
                    REQUEST_START,
                    1
            ));

            assertThat(response.reservationId()).isNotNull();
            verify(reservationRepository).save(any(Reservation.class));
        }
    }

    @Nested
    @DisplayName("Cancel reservation")
    class CancelReservationTests {

        @BeforeEach
        void setUp() {
            reservationService = new ReservationService(
                    carRepository,
                    customerRepository,
                    reservationRepository,
                    carRentalConfig
            );
        }

        @Test
        void shouldCancelReservedReservation() {
            Reservation reservation = reservation(
                    car(CarType.SUV, "Volvo", "XC60"),
                    REQUEST_START,
                    REQUEST_START.plusDays(3),
                    ReservationStatus.RESERVED
            );
            UUID reservationId = reservation.getId();

            when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
            when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

            reservationService.cancelReservation(reservationId);

            assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
            verify(reservationRepository).save(reservation);
        }

        @Test
        void shouldBeIdempotentWhenReservationIsAlreadyCancelled() {
            Reservation reservation = reservation(
                    car(CarType.SUV, "Volvo", "XC60"),
                    REQUEST_START,
                    REQUEST_START.plusDays(2),
                    ReservationStatus.CANCELLED
            );
            UUID reservationId = reservation.getId();

            when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
            when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

            reservationService.cancelReservation(reservationId);

            assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
            verify(reservationRepository).save(reservation);
        }

        @Test
        void shouldThrowWhenReservationDoesNotExist() {
            UUID reservationId = UUID.randomUUID();
            when(reservationRepository.findById(reservationId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reservationService.cancelReservation(reservationId))
                    .isInstanceOf(ReservationNotFoundException.class);

            verify(reservationRepository, never()).save(any(Reservation.class));
        }

        @Test
        void shouldRejectCancellationForCompletedReservation() {
            Reservation reservation = reservation(
                    car(CarType.SEDAN, "Audi", "A4"),
                    REQUEST_START,
                    REQUEST_START.plusDays(2),
                    ReservationStatus.COMPLETED
            );
            UUID reservationId = reservation.getId();
            when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));

            assertThatThrownBy(() -> reservationService.cancelReservation(reservationId))
                    .isInstanceOf(InvalidRequestException.class);

            assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.COMPLETED);
            verify(reservationRepository, never()).save(any(Reservation.class));
        }
    }

    private void stubNoBlockingReservations(Car car) {
        stubReservations(car, List.of());
    }

    private void stubBlockingReservations(Car car, Reservation reservation) {
        stubReservations(car, List.of(reservation));
    }

    private void stubReservations(Car car, Reservation... reservations) {
        stubReservations(car, List.of(reservations));
    }

    private void stubReservations(Car car, List<Reservation> reservations) {
        lenient().when(reservationRepository.findByCarId(car.getId())).thenReturn(reservations);
        lenient().when(reservationRepository.findByCarIdAndStatusNot(eq(car.getId()), eq(ReservationStatus.CANCELLED)))
                .thenReturn(reservations);
    }

    private Car car(CarType type, String brand, String model) {
        return Car.create(type, brand, model);
    }

    private Customer customer(String name, String email, String licenceNumber) {
        return Customer.create(name, email, licenceNumber);
    }

    private Reservation reservation(Car car, LocalDateTime start, LocalDateTime end, ReservationStatus status) {

        Reservation reservation =  Reservation.create(car, customer("Anna", "anna@example.com", "LIC-123"), start, end, BigDecimal.TEN);

        if(status.equals(ReservationStatus.COMPLETED)) {
            reservation.complete(end);
        } else if (status.equals(ReservationStatus.CANCELLED)) {
            reservation.cancel();
        }

        return reservation;
    }
}
