package com.psms.service;

import com.psms.repository.UserRepository;
import com.psms.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Load {@link com.psms.entity.User} từ DB theo email — dùng bởi Spring Security
 * trong quá trình authenticate và bởi {@link JwtAuthenticationFilter}
 * khi set SecurityContext từ JWT claim.
 *
 * <p>Dùng {@code findWithRolesByEmail} để fetch roles trong cùng 1 query (tránh LazyInitializationException).
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findWithRolesByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Không tìm thấy tài khoản với email: " + email));
    }
}

