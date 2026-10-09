package com.agrosense.frontend.service;

import com.agrosense.frontend.backend.BackendClient;
import com.agrosense.frontend.dto.RegistrationForm;
import com.agrosense.frontend.dto.Views.ProfileView;
import com.agrosense.frontend.entity.User;
import com.agrosense.frontend.exception.BusinessRuleException;
import com.agrosense.frontend.exception.NotFoundException;
import com.agrosense.frontend.repository.AlertRepository;
import com.agrosense.frontend.repository.EstateRepository;
import com.agrosense.frontend.repository.IrrigationRepository;
import com.agrosense.frontend.repository.SensorReadingRepository;
import com.agrosense.frontend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AccountService {

    public static final String DEFAULT_ROLE = "farmer";

    /** Deliberately vague, so the form does not confirm which e-mail addresses are registered. */
    private static final String REGISTRATION_REJECTED =
            "No pudimos crear la cuenta con esos datos. Si ya tienes una, inicia sesión.";

    private final UserRepository userRepository;
    private final EstateRepository estateRepository;
    private final SensorReadingRepository readingRepository;
    private final AlertRepository alertRepository;
    private final IrrigationRepository irrigationRepository;
    private final PasswordEncoder passwordEncoder;
    private final BackendClient backendClient;

    @Value("${agrosense.demo.email:}")
    private String demoEmail;

    @Transactional
    public void register(RegistrationForm form) {
        String email = form.getEmail().trim().toLowerCase(Locale.ROOT);
        if (userRepository.findByEmail(email).isPresent()) {
            throw new BusinessRuleException(REGISTRATION_REJECTED);
        }
        try {
            userRepository.saveAndFlush(User.builder()
                    .name(form.getName().trim())
                    .lastName(form.getLastName().trim())
                    .email(email)
                    .passwordHash(passwordEncoder.encode(form.getPassword()))
                    .role(DEFAULT_ROLE)
                    .build());
        } catch (DataIntegrityViolationException exception) {
            // Two simultaneous sign-ups with the same e-mail: the unique constraint rejects the second.
            throw new BusinessRuleException(REGISTRATION_REJECTED);
        }
    }

    /** Asks the backend, and the database while the backend is not answering. */
    @Transactional(readOnly = true)
    public boolean isEmailAvailable(String email) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        return backendClient.attempt(() -> backendClient.isEmailAvailable(normalized))
                .orElseGet(() -> !userRepository.existsByEmail(normalized));
    }

    @Transactional(readOnly = true)
    public ProfileView findProfile(String email) {
        User user = findUser(email);
        return new ProfileView(user.getName(), user.getLastName(), user.getEmail(), user.getCreatedAt());
    }

    /** Permanently removes the account and everything it owns, after re-checking the password. */
    @Transactional
    public void deleteAccount(String email, String rawPassword) {
        User user = findUser(email);
        if (!demoEmail.isBlank() && demoEmail.equalsIgnoreCase(email)) {
            throw new BusinessRuleException("La cuenta de demostración no se puede eliminar.");
        }
        if (rawPassword == null || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new BusinessRuleException("La contraseña no es correcta.");
        }
        // Rows that reference crops and sensors have no cascade, so they are removed first.
        readingRepository.deleteByOwner(email);
        alertRepository.deleteByOwner(email);
        irrigationRepository.deleteByOwner(email);
        irrigationRepository.clearActivatedBy(user.getIdUser());
        estateRepository.deleteAll(estateRepository.findByUserEmailOrderByName(email));
        userRepository.delete(findUser(email));
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email).orElseThrow(() -> new NotFoundException("User not found"));
    }
}
