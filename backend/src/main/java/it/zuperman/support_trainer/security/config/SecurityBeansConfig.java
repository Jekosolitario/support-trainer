package it.zuperman.support_trainer.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import it.zuperman.support_trainer.security.password.BcryptLengthAwarePasswordEncoder;

@Configuration
public class SecurityBeansConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BcryptLengthAwarePasswordEncoder(new BCryptPasswordEncoder());
    }
}
