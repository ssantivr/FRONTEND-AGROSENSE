package com.agrosense.frontend.security;

import com.agrosense.frontend.backend.BackendClient;
import com.agrosense.frontend.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.Locale;
import java.util.Optional;

/**
 * Signs in through the backend API, so its limit on failed attempts also protects this form. While the
 * backend is not configured or not answering it returns {@code null} and the database provider decides.
 *
 * <p>A lock (429) arrives as a {@code LockedException}, which stops the sign-in at once; wrong credentials
 * (401) are checked again against the same database by the next provider and fail there too.
 */
@RequiredArgsConstructor
public class BackendAuthenticationProvider implements AuthenticationProvider {

    private final BackendClient backendClient;
    private final UserDetailsService userDetailsService;

    @Override
    public Authentication authenticate(Authentication authentication) {
        String email = authentication.getName().trim().toLowerCase(Locale.ROOT);
        String password = authentication.getCredentials() == null ? "" : authentication.getCredentials().toString();
        Optional<Boolean> accepted;
        try {
            accepted = backendClient.attempt(() -> backendClient.signIn(email, password));
        } catch (BusinessRuleException exception) {
            // The backend refused the request itself, for example an e-mail that is not one.
            throw new BadCredentialsException("Rejected by the backend API");
        }
        if (accepted.isEmpty()) {
            return null;
        }
        UserDetails user = userDetailsService.loadUserByUsername(email);
        if (!user.isEnabled()) {
            throw new DisabledException("Account disabled");
        }
        return UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
