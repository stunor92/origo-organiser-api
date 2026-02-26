package no.stunor.origo.organiser.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

/**
 * Spring Security configuration for JWT-based authentication with Supabase.
 *
 * Supports optional authentication:
 * - Invalid tokens on public endpoints are treated as NO token (anonymous access)
 * - Invalid tokens on protected endpoints result in 401 Unauthorized
 *
 */
@Configuration
@EnableWebSecurity
open class SecurityConfig(
    private val jwtAuthenticationManagerResolver: JwtAuthenticationManagerResolver
) {

    @Bean
    open fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { authorize ->
                authorize
                    .requestMatchers("/actuator/**").permitAll()
                    .requestMatchers("/api-docs/**", "/documentation.html", "/swagger-ui/**").permitAll()
                    .anyRequest().authenticated()
            }
            .oauth2ResourceServer { oauth2 ->
                // Use custom authentication manager resolver for optional JWT authentication
                // This treats invalid tokens on public endpoints as anonymous (same as no token)
                oauth2.authenticationManagerResolver(jwtAuthenticationManagerResolver)
            }
            // Enable anonymous authentication
            .anonymous { }

        return http.build()
    }
}

