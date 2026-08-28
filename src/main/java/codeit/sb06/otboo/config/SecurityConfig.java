package codeit.sb06.otboo.config;

import codeit.sb06.otboo.security.handler.Http403ForbiddenAccessDeniedHandler;
import codeit.sb06.otboo.security.handler.CustomAuthenticationEntryPoint;
import codeit.sb06.otboo.security.jwt.RedisJwtRegistry;
import codeit.sb06.otboo.user.service.CustomOAuth2UserService;
import codeit.sb06.otboo.user.service.CustomOidcUserService;
import codeit.sb06.otboo.security.handler.LoginFailureHandler;
import codeit.sb06.otboo.security.handler.OAuth2FailureHandler;
import codeit.sb06.otboo.security.handler.SpaCsrfTokenRequestHandler;
import codeit.sb06.otboo.security.user.TemporaryPasswordAuthenticationProvider;
import codeit.sb06.otboo.security.jwt.JwtAuthenticationFilter;
import codeit.sb06.otboo.security.jwt.JwtLoginSuccessHandler;
import codeit.sb06.otboo.security.jwt.JwtLogoutHandler;
import codeit.sb06.otboo.security.jwt.JwtRegistry;
import codeit.sb06.otboo.security.jwt.JwtTokenProvider;
import codeit.sb06.otboo.user.entity.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtLoginSuccessHandler jwtLoginSuccessHandler,
        ObjectMapper objectMapper, JwtAuthenticationFilter jwtAuthenticationFilter,
        JwtLogoutHandler jwtLogoutHandler, LoginFailureHandler loginFailureHandler,
        TemporaryPasswordAuthenticationProvider temporaryPasswordAuthenticationProvider,
        CustomAuthenticationEntryPoint customAuthenticationEntryPoint,
        CustomOAuth2UserService customOAuth2UserService,
        CustomOidcUserService customOidcUserService,
        JwtLoginSuccessHandler jwtLoginSuccessHandlerForOauth2,
        OAuth2FailureHandler oauth2FailureHandler) throws Exception {

        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
            )
            .formLogin(login -> login
                .loginProcessingUrl("/api/auth/sign-in")
                .successHandler(jwtLoginSuccessHandler)
                .failureHandler(loginFailureHandler)
            )
            .oauth2Login(oauth2 -> oauth2
                .redirectionEndpoint(redirection -> redirection.baseUri("/login/oauth2/code/*"))
                .userInfoEndpoint(userInfo -> userInfo
                    .userService(customOAuth2UserService)
                    .oidcUserService(customOidcUserService))
                .successHandler(jwtLoginSuccessHandlerForOauth2)
                .failureHandler(oauth2FailureHandler))
            .logout(logout -> logout
                .logoutUrl("/api/auth/sign-out")
                .addLogoutHandler(jwtLogoutHandler)
                .logoutSuccessHandler(
                    new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
            )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/clothes/attribute-defs").hasRole("USER")
                        .requestMatchers("/api/clothes/attribute-defs/**").hasRole("ADMIN")
                        .requestMatchers("/api/clothes/**").hasRole("USER")
                        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()

                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/assets/**",
                                "/vite.svg",
                                "/logo_symbol.svg",
                                "/error"
                        ).permitAll()

                        .requestMatchers("/ws", "/ws/**").permitAll()

                        .requestMatchers("/actuator/health").permitAll()

                        .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(customAuthenticationEntryPoint)
                .accessDeniedHandler(new Http403ForbiddenAccessDeniedHandler(objectMapper))
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authenticationProvider(temporaryPasswordAuthenticationProvider)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();

    }

    @Bean
    public JwtRegistry jwtRegistry(
        JwtTokenProvider jwtTokenProvider,
        ApplicationEventPublisher eventPublisher,
        StringRedisTemplate redisTemplate
    ) {
        return new RedisJwtRegistry(redisTemplate, jwtTokenProvider, 1);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {return new BCryptPasswordEncoder();}

    @Bean
    static MethodSecurityExpressionHandler methodSecurityExpressionHandler(
        RoleHierarchy roleHierarchy
    ) {
        DefaultMethodSecurityExpressionHandler expressionHandler = new DefaultMethodSecurityExpressionHandler();
        expressionHandler.setRoleHierarchy(roleHierarchy);
        return expressionHandler;
    }

    @Bean
    public RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
            .role(Role.ADMIN.name())
            .implies(Role.USER.name())
            .build();
    }
}
