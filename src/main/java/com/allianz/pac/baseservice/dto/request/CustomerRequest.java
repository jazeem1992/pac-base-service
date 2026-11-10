package com.allianz.pac.baseservice.dto.request;

import com.allianz.pac.baseservice.common.enums.CustomerStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request payload to create or update a customer. Unknown JSON properties are ignored.
 *
 * @param firstName   first name
 * @param lastName    last name
 * @param email       unique e-mail address (stored lower case)
 * @param phoneNumber optional phone number in international format
 * @param status      customer status
 */
@Schema(description = "Customer create/update request")
public record CustomerRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Email @Size(max = 255) String email,
        @Pattern(regexp = "^\\+?[0-9 ]{6,20}$", message = "must be a valid phone number") String phoneNumber,
        @NotNull CustomerStatus status) {
}
