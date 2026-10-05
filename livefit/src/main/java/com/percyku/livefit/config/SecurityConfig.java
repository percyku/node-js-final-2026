package com.percyku.livefit.config;

import com.percyku.livefit.security.CoachAccessDeniedHandler;
import com.percyku.livefit.security.JwtAuthenticationEntryPoint;
import com.percyku.livefit.security.JwtAuthenticationFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 權限規則逐條對應 Node 版各 route 掛載的 isAuth / isCoach 中介層。
 *
 * 注意兩處順序陷阱：
 * 1. POST /api/admin/coaches/courses 與 POST /api/admin/coaches/{userId} 路徑形狀相同，
 *    前者必須先宣告，否則升級教練那條規則會把建立課程一併放行。
 * 2. anyRequest() 用 permitAll，讓未定義的路徑能落到 NoHandlerFoundException 回 404「無此路由」
 *    （與 Node 版逐路由掛中介層的行為一致）。因此所有需要登入的端點都必須在下方明列。
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({JwtProperties.class, GoogleProperties.class,
        GithubProperties.class, OAuthProperties.class})
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final CoachAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          JwtAuthenticationEntryPoint authenticationEntryPoint,
                          CoachAccessDeniedHandler accessDeniedHandler) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        // ===== 教練後台：isAuth + isCoach =====
                        .requestMatchers(HttpMethod.GET, "/api/admin/coaches/courses").hasRole("COACH")
                        .requestMatchers(HttpMethod.POST, "/api/admin/coaches/courses").hasRole("COACH")
                        .requestMatchers(HttpMethod.GET, "/api/admin/coaches/courses/*").hasRole("COACH")
                        .requestMatchers(HttpMethod.PUT, "/api/admin/coaches/courses/*").hasRole("COACH")
                        .requestMatchers(HttpMethod.GET, "/api/admin/coaches/revenue").hasRole("COACH")
                        .requestMatchers(HttpMethod.GET, "/api/admin/coaches").hasRole("COACH")
                        .requestMatchers(HttpMethod.PUT, "/api/admin/coaches").hasRole("COACH")

                        // ===== 會員中心：isAuth =====
                        .requestMatchers(HttpMethod.GET, "/api/users/profile").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/users/profile").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/users/password").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/users/credit-package").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/users/courses").authenticated()

                        // ===== 購買方案 / 課程報名：isAuth =====
                        .requestMatchers(HttpMethod.POST, "/api/credit-package/*").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/courses/*").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/courses/*").authenticated()

                        // 其餘（healthcheck、技能與方案管理、註冊登入、公開瀏覽、
                        // POST /api/admin/coaches/{userId} 升級教練）皆免登入
                        .anyRequest().permitAll())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** 對應 Node 版 app.use(cors())：預設全開，無白名單 */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /** cost 10 與 Node 版 bcryptjs.hash(password, 10) 相容，既有使用者密碼可直接驗證 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}
