package com.shortx.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterRequest {

    @NotBlank(message = "email is required")
    @Email(message = "email must be a valid email address")
    @Size(max = 255)
    private String email;

    // Minimum 8, not the more common 6 - 8 is the current general
    // baseline recommendation (NIST SP 800-63B favors length over forced
    // complexity rules) without being onerous for a portfolio-scale app.
    @NotBlank(message = "password is required")
    @Size(min = 8, max = 100, message = "password must be at least 8 characters")
    private String password;

    @NotBlank(message = "fullName is required")
    @Size(max = 255)
    private String fullName;
}
