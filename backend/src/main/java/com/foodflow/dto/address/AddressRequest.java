package com.foodflow.dto.address;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressRequest(

        @NotBlank(message = "Label is required")
        @Size(max = 30, message = "Label must be at most 30 characters")
        String label,

        @NotBlank(message = "Address line 1 is required")
        @Size(max = 255, message = "Address line 1 must be at most 255 characters")
        String line1,

        @Size(max = 255, message = "Address line 2 must be at most 255 characters")
        String line2,

        @NotBlank(message = "City is required")
        @Size(max = 100, message = "City must be at most 100 characters")
        String city,

        @NotBlank(message = "State is required")
        @Size(max = 100, message = "State must be at most 100 characters")
        String state,

        // Indian PIN code: 6 digits, not starting with 0.
        @NotBlank(message = "Postal code is required")
        @Pattern(regexp = "^[1-9][0-9]{5}$", message = "Postal code must be a valid 6-digit PIN code")
        String postalCode,

        // Optional; the first address is always made the default.
        Boolean makeDefault) {
}
