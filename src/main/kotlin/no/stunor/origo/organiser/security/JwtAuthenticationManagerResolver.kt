package no.stunor.origo.organiser.security

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.AuthenticationManagerResolver
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider
import org.springframework.stereotype.Component

/**
 * Custom authentication manager resolver that handles optional JWT authentication.
 *
 * Behavior:
 * - For public endpoints (permitAll): Invalid tokens are treated as NO token (anonymous access)
 * - For protected endpoints (authenticated): Invalid tokens result in 401 Unauthorized
 *
 * This allows clients to send expired/invalid tokens to public endpoints without getting 401 errors.
 */
@Component
class JwtAuthenticationManagerResolver(
    @param:Value($$"${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") private val jwkSetUri: String
) : AuthenticationManagerResolver<HttpServletRequest> {

    private val log = LoggerFactory.getLogger(this.javaClass)

    init {
        log.info("Initializing JwtAuthenticationManagerResolver with JWKS URI: $jwkSetUri")

        // Verify JWKS URI is properly configured
        if (jwkSetUri.contains($$"${")) {
            log.error("JWKS URI not properly resolved: $jwkSetUri - Check SUPABASE_PROJECT_REF environment variable")
        }
    }

    // Create JwtAuthenticationConverter internally to avoid circular dependency
    private val jwtAuthenticationConverter = JwtAuthenticationConverter().apply {
        setJwtGrantedAuthoritiesConverter { _ ->
            listOf(SimpleGrantedAuthority("ROLE_USER"))
        }
    }

    // Lazy initialization to avoid circular dependency
    private val jwtAuthenticationProvider: JwtAuthenticationProvider by lazy {
        log.info("Creating JwtDecoder with JWKS URI: $jwkSetUri")

        // Create decoder with explicit algorithm support for ES256 (Supabase) and RS256 (common)
        val decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
            .jwsAlgorithms { algorithms ->
                algorithms.clear()
                algorithms.add(SignatureAlgorithm.ES256)  // ECDSA with SHA-256 (Supabase uses this)
                algorithms.add(SignatureAlgorithm.RS256)  // RSA with SHA-256 (fallback)
                log.info("JwtDecoder configured to accept algorithms: ES256, RS256")
            }
            .build()

        log.info("JwtDecoder created successfully")

        JwtAuthenticationProvider(decoder).apply {
            setJwtAuthenticationConverter(jwtAuthenticationConverter)
        }
    }

    override fun resolve(request: HttpServletRequest): AuthenticationManager {
        val path = request.requestURI.removePrefix(request.contextPath)

        return AuthenticationManager { authentication ->
            try {
                // Try to validate JWT token
                val result = jwtAuthenticationProvider.authenticate(authentication)
                log.debug("JWT authentication successful for ${request.method} $path")
                result ?: throw Exception("JWT authentication returned null")
            } catch (e: Exception) {
                    // Protected endpoint with invalid token - reject with 401
                log.warn("Invalid JWT token on protected endpoint ${request.method} $path - rejecting")
                log.warn("JWT validation error: ${e.javaClass.simpleName}")
                log.warn("Error message: ${e.message}")
                log.debug("Full stack trace:", e)
                throw e
            }
        }
    }
}

