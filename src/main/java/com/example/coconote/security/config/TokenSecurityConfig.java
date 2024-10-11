package com.example.coconote.security.config;

import com.example.coconote.api.member.repository.MemberRepository;
import com.example.coconote.security.filter.JwtAuthenticationFilter;
import com.example.coconote.security.service.CustomOAuth2UserService;
import com.example.coconote.security.token.CustomAuthenticationSuccessHandler;
import com.example.coconote.security.token.JwtTokenProvider;
import com.example.coconote.security.util.HttpCookieOAuth2AuthorizationRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@RequiredArgsConstructor
public class TokenSecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final MemberRepository memberRepository;
    private final CustomOAuth2UserService customOAuth2UserService;

    @Value("${redirect.url}")  // application.yml 파일의 redirect.url 읽기
    private String frontUrl;

    @Bean
    public AuthorizationRequestRepository<OAuth2AuthorizationRequest> authorizationRequestRepository() {
        return new HttpCookieOAuth2AuthorizationRequestRepository();
    }

    @Bean
    // 스프링 시큐리티는 이 **SecurityFilterChain**을 통해 필터를 실행하고, 모든 요청에 대해 인증과 인가 규칙을 적용합니다.
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))  // Spring Security에서 CORS 설정 추가
                // 1. 비활성화 (필터 순서에서 첫 번째로 설정됨)
                .csrf(AbstractHttpConfigurer::disable)  // CSRF 비활성화
                .securityContext(context -> context.requireExplicitSave(false))  // 기본 보안 컨텍스트 관리 비활성화
                .httpBasic(AbstractHttpConfigurer::disable)  // HTTP Basic 비활성화
                .formLogin(AbstractHttpConfigurer::disable)  // Form Login 비활성화
                .logout(AbstractHttpConfigurer::disable)  // Logout 비활성화
                // 서버가 세션을 생성하고 관리하는 기능이 완전히 비활성화. 세션 상태를 Stateless 로 설정
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                        .sessionFixation().none()  // 세션 고정 공격 방지 및 세션 설정 제거
                )

                // 2. 요청 인가 처리 (리소스에 접근하기 전에 인증된 사용자만 접근 허용)
                .authorizeHttpRequests(authorizeRequests ->
                                authorizeRequests
//                                        .requestMatchers("/**").permitAll()
                                .requestMatchers("/" , "/login**" , "/error", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/swagger-resources/**", "/webjars/**").permitAll()
                                        .requestMatchers("/api/v1/ws-stomp/**").permitAll()  // WebSocket 엔드포인트 허용
                                        .anyRequest().authenticated()
                )

                // 3. OAuth2 로그인 필터 (OAuth2 기반 로그인 처리)
                .oauth2Login(oauth2Login ->
                                oauth2Login
//                                .loginPage("/login")
//                                .defaultSuccessUrl("/", false)  // 로그인 전 마지막에 접근했던 페이지로 이동
//                                .failureUrl("/login?error=true")  // 로그인 실패 시 이동할 URL 설정
                                        .authorizationEndpoint(authorizationEndpoint -> authorizationEndpoint
                                                .authorizationRequestRepository(authorizationRequestRepository())
                                        ) // 세션을 사용하지 않도록 null 설정, 쿠키 기반 OAuth2 상태 관리
                                        .userInfoEndpoint(userInfoEndpoint ->
                                                userInfoEndpoint .userService(customOAuth2UserService)  // CustomOAuth2UserService 등록
                                        )
                                        .successHandler(new CustomAuthenticationSuccessHandler(jwtTokenProvider, frontUrl))  // 직접 만든 SuccessHandler 설정, Bean 으로 등록한 SuccessHandler 사용
                )

                // 4. JWT 인증 필터 (모든 요청에서 JWT 토큰을 확인하고 인증 처리)
                // 스프링 시큐리티의 필터 체인에 **JwtAuthenticationFilter**를 추가하는 역할
                // JWT 인증 필터가 UsernamePasswordAuthenticationFilter 앞에 위치하도록 설정
                // UsernamePasswordAuthenticationFilter 는 **폼 기반 로그인(아이디/비밀번호)**을 처리하는 필터
                // 모든 요청에서 JWT 인증을 우선 처리한 후, 폼 기반 인증(Username/Password)이 필요하면 이를 처리합니다.
                // JWT 토큰이 유효하다면 폼 로그인을 거치지 않고 바로 인증을 완료할 수 있습니다.
                // JWT 토큰이 유효하다면, 스프링 시큐리티는 JWT 토큰을 기반으로 사용자를 인증하고, **UsernamePasswordAuthenticationFilter**를 거치지 않고 요청을 처리합니다.
                // JWT 인증 필터는 요청의 Authorization 헤더에 있는 JWT 토큰을 검증합니다.
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider, memberRepository), UsernamePasswordAuthenticationFilter.class
                );

        // HttpSecurity 객체를 사용해 보안 설정을 완료하고, 그 설정을 기반으로 SecurityFilterChain 객체를 생성하여 반환하는 역할
        // HttpSecurity 객체는 CSRF 설정, 인증 방식, 인가 규칙 등을 정의
        // build() 메서드는 HttpSecurity 객체에 설정된 모든 보안 규칙을 적용하고, 이를 통해 **필터 체인(SecurityFilterChain)**을 구성
        // http.build()는 설정된 **HttpSecurity**를 **SecurityFilterChain**으로 빌드한 후 반환합니다.
        return http.build();
    }

    // CORS 설정을 정의하는 메서드
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.addAllowedOrigin("http://localhost:*");
        configuration.addAllowedOrigin("http://localhost:8082");
        configuration.addAllowedOrigin("https://coconote.site");
        configuration.addAllowedMethod("*");  // 모든 HTTP 메서드를 허용
        configuration.addAllowedHeader("*");  // 모든 HTTP 헤더를 허용
        configuration.setAllowCredentials(true);  // 자격 증명 허용

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);  // CORS 규칙을 "/api/**" 경로에 적용
        return source;
    }
}
