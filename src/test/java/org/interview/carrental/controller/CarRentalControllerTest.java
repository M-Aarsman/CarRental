package org.interview.carrental.controller;

import org.interview.carrental.dto.AvailableCarsRequest;
import org.interview.carrental.dto.AvailableCarsResponse;
import org.interview.carrental.dto.CarOffer;
import org.interview.carrental.dto.ReservationRequest;
import org.interview.carrental.dto.ReservationResponse;
import org.interview.carrental.exception.CarNotAvailableException;
import org.interview.carrental.exception.CarNotFoundException;
import org.interview.carrental.exception.InvalidRequestException;
import org.interview.carrental.exception.ReservationNotFoundException;
import org.interview.carrental.model.CarType;
import org.interview.carrental.service.CarService;
import org.interview.carrental.service.ReservationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CarRentalController.class)
@DisplayName("Car rental controller")
class CarRentalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CarService carService;

    @MockitoBean
    private ReservationService reservationService;

    @Autowired
    private ObjectMapper objectMapper;

    @Nested
    @DisplayName("GET /cars/available")
    class GetAvailableCarsTests {

        @Test
        void shouldReturnAvailableCars() throws Exception {
            when(carService.findAvailableCars(any(AvailableCarsRequest.class)))
                    .thenReturn(availableCarsResponse(
                            carOffer(CarType.SUV, "Volvo", "XC60", "120.00", "360.00")
                    ));

            performGetAvailableCars(null, null, null, "2026-04-12T10:00:00", 3)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.offers[0].brand").value("Volvo"))
                    .andExpect(jsonPath("$.offers[0].model").value("XC60"))
                    .andExpect(jsonPath("$.offers[0].pricePerDay").value(120.00))
                    .andExpect(jsonPath("$.offers[0].totalPrice").value(360.00));

            verify(carService).findAvailableCars(any(AvailableCarsRequest.class));
        }

        @Test
        void shouldReturnCarsByType() throws Exception {
            when(carService.findAvailableCars(any(AvailableCarsRequest.class)))
                    .thenReturn(availableCarsResponse(
                            carOffer(CarType.SUV, "Volvo", "XC60", "120.00", "360.00")
                    ));

            performGetAvailableCars(
                    List.of(CarType.SUV),
                    null,
                    null,
                    "2026-04-12T10:00:00",
                    3
            )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.offers[0].type").value("SUV"))
                    .andExpect(jsonPath("$.offers[0].brand").value("Volvo"));

            verify(carService).findAvailableCars(argThat(request ->
                    request.getTypes().equals(List.of(CarType.SUV))
                            && request.getBrand() == null
                            && request.getModel() == null
                            && request.getCarId() == null
                            && request.getStart().equals(LocalDateTime.of(2026, 4, 12, 10, 0))
                            && request.getDays() == 3
            ));
        }

        @Test
        void shouldReturnCarsByMultipleTypes() throws Exception {
            when(carService.findAvailableCars(any(AvailableCarsRequest.class)))
                    .thenReturn(availableCarsResponse(
                            carOffer(CarType.SUV, "Volvo", "XC60", "120.00", "240.00"),
                            carOffer(CarType.VAN, "Mercedes", "Vito", "140.00", "280.00")
                    ));

            performGetAvailableCars(
                    List.of(CarType.SUV, CarType.VAN),
                    null,
                    null,
                    "2026-04-12T10:00:00",
                    2
            )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.offers.length()").value(2));
        }

        @Test
        void shouldReturnCarsByBrand() throws Exception {
            when(carService.findAvailableCars(argThat(request -> "Audi".equals(request.getBrand()))))
                    .thenReturn(availableCarsResponse(
                            carOffer(CarType.SEDAN, "Audi", "A4", "90.00", "90.00")
                    ));

            performGetAvailableCars(
                    null,
                    "Audi",
                    null,
                    "2026-04-12T10:00:00",
                    1
            )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.offers[0].brand").value("Audi"));
        }

        @Test
        void shouldReturnCarsByModel() throws Exception {
            when(carService.findAvailableCars(argThat(request -> "A4".equals(request.getModel()))))
                    .thenReturn(availableCarsResponse(
                            carOffer(CarType.SEDAN, "Audi", "A4", "90.00", "90.00")
                    ));

            performGetAvailableCars(
                    null,
                    null,
                    "A4",
                    "2026-04-12T10:00:00",
                    1
            )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.offers[0].model").value("A4"));
        }

        @Test
        void shouldReturnBadRequestWhenMissingStart() throws Exception {
            when(carService.findAvailableCars(any(AvailableCarsRequest.class)))
                    .thenThrow(new InvalidRequestException("Start date must be provided"));

            mockMvc.perform(get("/cars/available")
                            .param("days", "3"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void shouldReturnBadRequestForInvalidAvailabilityQuery() throws Exception {
            when(carService.findAvailableCars(any(AvailableCarsRequest.class)))
                    .thenThrow(new InvalidRequestException("Days must be greater than zero"));

            performGetAvailableCars(null, null, null, "2026-04-12T10:00:00", 0)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Days must be greater than zero"))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.timestamp").exists());
        }

        @Test
        void shouldReturnInternalServerErrorForUnexpectedAvailabilityFailure() throws Exception {
            when(carService.findAvailableCars(any(AvailableCarsRequest.class)))
                    .thenThrow(new RuntimeException("Unexpected failure"));

            performGetAvailableCars(null, null, null, "2026-04-12T10:00:00", 3)
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.message").value("Internal server error"))
                    .andExpect(jsonPath("$.status").value(500))
                    .andExpect(jsonPath("$.timestamp").exists());
        }
    }

    @Nested
    @DisplayName("POST /reservations")
    class CreateReservationTests {

        @Test
        void shouldCreateReservation() throws Exception {
            ReservationRequest request = reservationRequest();

            ReservationResponse response = new ReservationResponse(
                    UUID.randomUUID(),
                    "Volvo",
                    "XC60",
                    CarType.SUV,
                    LocalDateTime.now(),
                    LocalDateTime.now().plusDays(3),
                    null,
                    "Anna"
            );

            when(reservationService.createReservation(any()))
                    .thenReturn(response);

            performCreateReservation(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.carBrand").value("Volvo"))
                    .andExpect(jsonPath("$.customerName").value("Anna"));

            verify(reservationService).createReservation(any());
        }

        @Test
        void shouldReturnBadRequestWhenReservationRequestIsInvalid() throws Exception {
            when(reservationService.createReservation(any()))
                    .thenThrow(new InvalidRequestException("Start date must be in the future"));

            performCreateReservation(reservationRequest())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Start date must be in the future"))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.timestamp").exists());
        }

        @Test
        void shouldReturnNotFoundWhenCarDoesNotExist() throws Exception {
            when(reservationService.createReservation(any()))
                    .thenThrow(new CarNotFoundException("Car not found"));

            performCreateReservation(reservationRequest())
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Car not found"))
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.timestamp").exists());
        }

        @Test
        void shouldReturnConflictWhenCarIsNotAvailable() throws Exception {
            when(reservationService.createReservation(any()))
                    .thenThrow(new CarNotAvailableException("Car is not available for selected period"));

            performCreateReservation(reservationRequest())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Car is not available for selected period"))
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.timestamp").exists());
        }

        @Test
        void shouldReturnInternalServerErrorWhenCreatingReservationFailsUnexpectedly() throws Exception {
            when(reservationService.createReservation(any()))
                    .thenThrow(new RuntimeException("Unexpected failure"));

            performCreateReservation(reservationRequest())
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.message").value("Internal server error"))
                    .andExpect(jsonPath("$.status").value(500))
                    .andExpect(jsonPath("$.timestamp").exists());
        }
    }

    @Nested
    @DisplayName("DELETE /reservations/{id}")
    class CancelReservationTests {

        @Test
        void shouldCancelReservation() throws Exception {
            UUID id = UUID.randomUUID();

            doNothing().when(reservationService).cancelReservation(id);

            performCancelReservation(id)
                    .andExpect(status().isNoContent());

            verify(reservationService).cancelReservation(any());
        }

        @Test
        void shouldReturnNotFoundWhenReservationNotFound() throws Exception {
            UUID id = UUID.randomUUID();

            doThrow(new ReservationNotFoundException("Reservation not found"))
                    .when(reservationService).cancelReservation(id);

            performCancelReservation(id)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Reservation not found"))
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.timestamp").exists());
        }

        @Test
        void shouldReturnInternalServerErrorWhenCancelFailsUnexpectedly() throws Exception {
            UUID id = UUID.randomUUID();

            doThrow(new RuntimeException("Unexpected failure"))
                    .when(reservationService).cancelReservation(id);

            performCancelReservation(id)
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.message").value("Internal server error"))
                    .andExpect(jsonPath("$.status").value(500))
                    .andExpect(jsonPath("$.timestamp").exists());
        }
    }

    private ResultActions performGetAvailableCars(
            List<CarType> types,
            String brand,
            String model,
            String start,
            int days
    ) throws Exception {

        var request = get("/cars/available")
                .param("start", start)
                .param("days", String.valueOf(days));

        if (types != null) {
            for (CarType type : types) {
                request.param("types", type.name());
            }
        }

        if (brand != null) {
            request.param("brand", brand);
        }

        if (model != null) {
            request.param("model", model);
        }

        return mockMvc.perform(request);
    }

    private ResultActions performCreateReservation(ReservationRequest request) throws Exception {
        return mockMvc.perform(post("/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private ResultActions performCancelReservation(UUID id) throws Exception {
        return mockMvc.perform(delete("/reservations/{id}", id));
    }

    private AvailableCarsResponse availableCarsResponse(CarOffer... offers) {
        return new AvailableCarsResponse(List.of(offers));
    }

    private CarOffer carOffer(
            CarType type,
            String brand,
            String model,
            String pricePerDay,
            String totalPrice
    ) {
        return new CarOffer(
                UUID.randomUUID(),
                type,
                brand,
                model,
                new BigDecimal(pricePerDay),
                new BigDecimal(totalPrice)
        );
    }

    private ReservationRequest reservationRequest() {
        return new ReservationRequest(
                null,
                CarType.SUV,
                "Anna",
                "anna@mail.com",
                "XYZ123",
                LocalDateTime.of(2026, 4, 12, 10, 0),
                3
        );
    }
}
