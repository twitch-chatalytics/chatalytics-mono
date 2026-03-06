package space.forloop.chatalytics.api.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import space.forloop.chatalytics.api.services.wrapper.UserService;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private static final String CLERK_JWKS_URI = "https://supreme-hagfish-0.clerk.accounts.dev/.well-known/jwks.json";
    private static final String CLERK_ISSUER = "https://supreme-hagfish-0.clerk.accounts.dev";
    private final UserService userService;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())  // Use the corsConfigurationSource bean
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(getAuthorizationManagerRequestMatcherRegistryCustomizer())
                .oauth2ResourceServer(getAuth2ResourceServerConfigurerCustomizer())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Allow all origins
        configuration.setAllowedOriginPatterns(List.of("*"));

        // Allow all common methods
        configuration.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"
        ));

        // Allow all common headers
        configuration.setAllowedHeaders(Arrays.asList(
                "*"
        ));

        // Expose response headers that might be needed by the client
        configuration.setExposedHeaders(Arrays.asList(
                "*"
        ));

        // Max age for CORS preflight cache
        configuration.setMaxAge(3600L);

        // Allow credentials
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    JwtDecoder jwtDecoder() {
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withJwkSetUri(CLERK_JWKS_URI).build();
        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(CLERK_ISSUER);
        jwtDecoder.setJwtValidator(issuerValidator);
        return jwtDecoder;
    }

    @Bean
    Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        return new JwtToAuthenticationConverter(userService);
    }

    private Customizer<OAuth2ResourceServerConfigurer<HttpSecurity>> getAuth2ResourceServerConfigurerCustomizer() {
        return oauth2 -> oauth2.jwt(jwt -> jwt.decoder(jwtDecoder()));
    }

    private Customizer<AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry> getAuthorizationManagerRequestMatcherRegistryCustomizer() {
        return auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/public/**").permitAll()
                .anyRequest().authenticated();
    }
}