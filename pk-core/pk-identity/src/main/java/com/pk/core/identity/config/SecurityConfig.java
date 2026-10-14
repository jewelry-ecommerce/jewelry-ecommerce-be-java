package com.pk.core.identity.config;

import com.pk.core.model.constant.admin.UrlAdminConstant;
import com.pk.core.model.constant.UrlConstant;
import com.pk.core.model.constant.UrlIdentConstant;
import com.pk.core.identity.security.handler.AccessDeniedHandlerImpl;
import com.pk.core.identity.security.handler.AuthEntryPoint;
import com.pk.core.identity.security.jwt.JwtPrincipalConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   CorsConfigurationSource cors,
                                                   JwtPrincipalConverter jwtConverter,
                                                   AuthEntryPoint authEntryPoint,
                                                   AccessDeniedHandlerImpl accessDeniedHandler) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(c -> c.configurationSource(cors))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(v1(UrlIdentConstant.Auth.BASE + "/**")).permitAll()
                        // Luồng SĐT + OTP theo spec FE (resolve-phone, đăng ký 3 bước, quên/đặt lại mật khẩu):
                        // công khai vì lúc gọi chưa có token. Chỉ POST - StorefrontAuthRest không có GET nào.
                        .requestMatchers(HttpMethod.POST, v1(UrlIdentConstant.StorefrontAuth.BASE + "/**")).permitAll()
                        // Yêu thích (/storefront/product/customer/wishlist): bắt buộc đăng nhập. PHẢI đứng TRƯỚC rule GET
                        // công khai của Product.BASE bên dưới (Spring Security khớp rule đầu tiên trùng).
                        .requestMatchers(v1(UrlConstant.Wishlist.BASE + "/**")).authenticated()
                        // Giỏ hàng: công khai cho khách vãng lai (header X-Guest-Cart-Id); gộp giỏ vào tài khoản thì bắt buộc
                        // đăng nhập - rule merge cụ thể đặt TRƯỚC rule chung.
                        .requestMatchers(HttpMethod.POST, v1(UrlConstant.Cart.BASE + "/merge")).authenticated()
                        .requestMatchers(v1(UrlConstant.Cart.BASE), v1(UrlConstant.Cart.BASE + "/**")).permitAll()
                        // Storefront đọc catalog: công khai, không cần đăng nhập (xem document/09-tong-hop-api-fe.md mục C)
                        .requestMatchers(HttpMethod.GET, v1(UrlConstant.Product.BASE + "/**")).permitAll()
                        // AI stylist/set-builder, badge: công khai theo spec FE (mục G/H). Checkout quote (mục D)
                        // spec ghi "Không cần" nhưng thiết kế ở đây dùng addressId đã lưu (giống OrderRest.create),
                        // không hỗ trợ khách vãng lai gửi địa chỉ rời rạc - CỐ Ý bắt buộc đăng nhập (rơi vào
                        // anyRequest().authenticated() mặc định bên dưới, không thêm permitAll ở đây), khác spec.
                        .requestMatchers(HttpMethod.POST, v1(UrlConstant.Ai.BASE + "/**")).permitAll()
                        .requestMatchers(HttpMethod.GET, v1(UrlConstant.Badge.BASE)).permitAll()
                        .requestMatchers(HttpMethod.GET, v1(UrlConstant.PreOrder.CURRENT)).permitAll()
                        // CMS storefront (trang đã PUBLISHED theo slug) và render banner theo vị trí: công khai, chỉ GET.
                        // Khách gửi yêu cầu (báo khi có hàng / hỗ trợ đơn / nhận tin): công khai, chỉ POST.
                        .requestMatchers(HttpMethod.POST, v1(UrlConstant.CustomerRequest.BASE + "/**")).permitAll()
                        .requestMatchers(HttpMethod.GET, v1(UrlConstant.Cms.PAGES + "/**")).permitAll()
                        .requestMatchers(HttpMethod.GET, v1(UrlConstant.Banner.PLACEMENTS_BY_CODE + "/**")).permitAll()
                        // Khách vãng lai: đặt hàng, báo giá, tra cứu đơn (mã đơn + SĐT) không cần đăng nhập.
                        .requestMatchers(HttpMethod.POST, v1(UrlConstant.Order.GUEST), v1(UrlConstant.Checkout.QUOTE_GUEST)).permitAll()
                        .requestMatchers(HttpMethod.GET, v1(UrlConstant.Order.GUEST + "/*")).permitAll()
                        // File ảnh/video đã upload: công khai, chỉ GET.
                        .requestMatchers(HttpMethod.GET, v1(UrlConstant.Storefront.FILES + "/**")).permitAll()
                        // Webhook cổng thanh toán: không có Bearer token, tự xác thực bằng chữ ký (mục F)
                        .requestMatchers(HttpMethod.POST, v1(UrlConstant.Payment.BASE + "/**")).permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        // Admin (Rest ở pk-api gói api.rest.admin, hằng route UrlAdminConstant đặt ở
                        // pk-model, gói constant.admin - cùng chỗ UrlConstant/UrlIdentConstant - xem
                        // RULE-CODE.md mục "Admin tách riêng: constant đặt ở pk-model", 2026-09-29,
                        // sửa lần 3 trong ngày): route login công khai (chưa có token lúc gọi), MỌI
                        // route admin còn lại bắt buộc role ADMIN. pk-identity vốn đã phụ thuộc pk-model
                        // nên gọi thẳng UrlAdminConstant được, không cần constant trung gian. Đặt SAU
                        // rule /actuator/** cụ thể hơn nhưng TRƯỚC anyRequest() mặc định, đúng thứ tự
                        // matcher cụ thể -> rộng dần của Spring Security.
                        .requestMatchers(HttpMethod.POST, v1(UrlAdminConstant.Auth.LOGIN)).permitAll()
                        // Phân quyền khu quản trị theo vai trò: ADMIN toàn quyền; CATALOG_MANAGER chỉ nhóm
                        // danh mục/nội dung; ORDER_MANAGER chỉ nhóm đơn hàng/kho/khách. Quản lý tài khoản, vai trò
                        // và cấu hình đặt trước chỉ ADMIN. Rule cụ thể đặt TRƯỚC rule chung "/admin/**".
                        .requestMatchers(v1(UrlAdminConstant.Common.BASE + "/auth/**"))
                        .hasAnyRole("ADMIN", "CATALOG_MANAGER", "ORDER_MANAGER")
                        .requestMatchers(v1(UrlAdminConstant.Common.BASE + "/catalog/**"),
                                v1(UrlAdminConstant.Common.BASE + "/promotions/**"),
                                v1(UrlAdminConstant.Common.BASE + "/vouchers/**"),
                                v1(UrlAdminConstant.Common.BASE + "/cms/**"),
                                v1(UrlAdminConstant.Common.BASE + "/ie/**"),
                                v1(UrlAdminConstant.Common.BASE + "/banner/**"),
                                v1(UrlAdminConstant.Common.BASE + "/badge-templates/**"),
                                v1(UrlAdminConstant.Common.BASE + "/badge-flow/**"))
                        .hasAnyRole("ADMIN", "CATALOG_MANAGER")
                        .requestMatchers(v1(UrlAdminConstant.Common.BASE + "/orders/**"),
                                v1(UrlAdminConstant.Common.BASE + "/inventory/**"),
                                v1(UrlAdminConstant.Common.BASE + "/customers/**"),
                                v1(UrlAdminConstant.Common.BASE + "/customer-request/**"))
                        .hasAnyRole("ADMIN", "ORDER_MANAGER")
                        .requestMatchers(v1(UrlAdminConstant.Common.BASE + "/**")).hasRole("ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter))
                        .authenticationEntryPoint(authEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }

    /** Gắn tiền tố /api/v1: Rest gắn bằng @RequestMapping(UrlConstant.Common.API + UrlConstant.Common.VERSION), còn các hằng route là đường dẫn tương đối. */
    private static String v1(String relativePath) {
        return UrlConstant.Common.API + UrlConstant.Common.VERSION + relativePath;
    }
}
