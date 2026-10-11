package com.agrosense.frontend.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegistrationForm {

    /** Lengths are counted on the text as typed, so a space is one more character. Same limits as the backend API. */
    public static final int MIN_NAME_LENGTH = 2;
    public static final int MAX_NAME_LENGTH = 18;
    public static final int MIN_PASSWORD_LENGTH = 8;
    public static final int MAX_PASSWORD_LENGTH = 32;

    @NotBlank(message = "Ingresa tu nombre.")
    @Size(min = MIN_NAME_LENGTH, max = MAX_NAME_LENGTH, message = "El nombre debe tener entre 2 y 18 caracteres.")
    private String name;

    @NotBlank(message = "Ingresa tu apellido.")
    @Size(min = MIN_NAME_LENGTH, max = MAX_NAME_LENGTH, message = "El apellido debe tener entre 2 y 18 caracteres.")
    private String lastName;

    @NotBlank(message = "Ingresa tu correo electrónico.")
    @Email(message = "Ingresa un correo electrónico válido.")
    @Size(min = 6, max = 160, message = "El correo debe tener entre 6 y 160 caracteres.")
    private String email;

    @NotNull(message = "Ingresa una contraseña.")
    @Size(min = MIN_PASSWORD_LENGTH, max = MAX_PASSWORD_LENGTH,
            message = "La contraseña debe tener entre 8 y 32 caracteres.")
    private String password;

    private String confirmPassword;

    /** Surrounding spaces are a common paste artefact, so they are dropped before validation. */
    public void setEmail(String email) {
        this.email = email == null ? null : email.trim();
    }

    @AssertTrue(message = "Las contraseñas no coinciden.")
    public boolean isPasswordConfirmed() {
        return password == null || password.equals(confirmPassword);
    }
}
