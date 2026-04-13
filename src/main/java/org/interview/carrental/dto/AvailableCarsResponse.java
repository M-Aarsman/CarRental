package org.interview.carrental.dto;

import java.util.List;

public record AvailableCarsResponse(
        List<CarOffer> offers
) {
}
