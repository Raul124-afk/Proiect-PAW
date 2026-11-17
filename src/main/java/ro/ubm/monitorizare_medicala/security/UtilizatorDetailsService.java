package ro.ubm.monitorizare_medicala.security;

import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ro.ubm.monitorizare_medicala.repository.UserRepository;

// Spring folosește același serviciu pentru login web și login API.
@Service
public class UtilizatorDetailsService implements UserDetailsService {
    private final UserRepository users;

    public UtilizatorDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        var user =
                users.findByUsername(username)
                        .orElseThrow(() -> new UsernameNotFoundException("Utilizator inexistent"));
        return User.withUsername(user.getUsername())
                .password(user.getPassword())
                .authorities(user.getRole())
                .build();
    }
}
