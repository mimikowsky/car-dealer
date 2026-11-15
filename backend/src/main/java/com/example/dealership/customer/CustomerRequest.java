package com.example.dealership.customer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CustomerRequest(
        @NotBlank
        @Size(max = 100)
        String firstName,

        @NotBlank
        @Size(max = 100)
        String lastName,

        @NotBlank
        @Email
        @Size(max = 255)
        String email,

        @NotBlank
        @Size(max = 20)
        @Pattern(regexp = "\\+?[0-9 -]{6,20}", message = "must contain 6 to 20 digits, spaces or dashes, optionally starting with +")
        String phone,

        @Size(max = 10)
        @Pattern(regexp = "[0-9]{10}", message = "must be exactly 10 digits")
        String nip
) {
}
