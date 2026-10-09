package com.agrosense.frontend.security;

import com.agrosense.frontend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class DatabaseUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        return userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .map(user -> new AgroUserPrincipal(
                        user.getEmail(),
                        user.getPasswordHash(),
                        Boolean.TRUE.equals(user.getActive()),
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().toUpperCase(Locale.ROOT))),
                        user.getName(),
                        user.getLastName()))
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}
