package com.pk.core.test.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pk.core.model.entity.RoleEntity;
import com.pk.core.business.repository.RoleRepo;
import com.pk.core.model.entity.UserEntity;
import com.pk.core.business.repository.UserRepo;
import com.pk.core.service.PkServiceApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Nền cho integration test: khởi động toàn bộ ứng dụng (H2) và gọi API qua MockMvc,
 * đi qua đầy đủ Security filter chain nên kiểm tra được 401/403 thật.
 * DB dùng chung giữa các test => mỗi test dùng SĐT/email/tên duy nhất (uniquePhone(), uniqueEmail()).
 */
@SpringBootTest(classes = PkServiceApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(RepoTxTestConfig.class)
public abstract class IntegrationTestBase {

    protected static final String PASSWORD = "Password1!";

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected UserRepo users;
    @Autowired protected RoleRepo roles;
    @Autowired protected PasswordEncoder encoder;

    private static final AtomicLong PHONE_SEQ = new AtomicLong(900_000_000L);

    /** SĐT chuẩn hoá duy nhất trong cả lần chạy test (0900000001, 0900000002, ...). */
    protected String uniquePhone() {
        return String.format("0%09d", PHONE_SEQ.incrementAndGet());
    }

    protected String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@test.local";
    }

    protected ResultActions postJson(String path, Object body) throws Exception {
        return mvc.perform(jsonRequest(post(path), body));
    }

    protected MockHttpServletRequestBuilder jsonRequest(MockHttpServletRequestBuilder builder, Object body) throws Exception {
        return builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    }

    protected JsonNode body(ResultActions result) throws Exception {
        JsonNode root = json.readTree(result.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        // Response thanh cong duoc boc trong {"data": ...} (BaseRes) - loi thi khong boc nen tra nguyen root.
        return root.has("data") ? root.get("data") : root;
    }

    /** Đăng ký khách hàng mới qua API; trả về body TokenResponseDto. */
    protected JsonNode registerCustomer(String phone) throws Exception {
        return body(postJson("/api/v1/auth/register",
                Map.of("phone", phone, "password", PASSWORD, "fullName", "Test User")));
    }

    /** Tạo trực tiếp một admin trong DB (không có API đăng ký admin) rồi đăng nhập lấy access token. */
    protected String adminAccessToken() throws Exception {
        String phone = uniquePhone();
        UserEntity admin = new UserEntity(phone, encoder.encode(PASSWORD), "Admin Test");
        users.create(admin);
        RoleEntity adminRole = roles.findByName(RoleEntity.ADMIN);
        if (adminRole == null) {
            adminRole = roles.create(new RoleEntity(RoleEntity.ADMIN));
        }
        users.addRole(admin.getId(), adminRole.getId());
        return body(postJson("/api/v1/auth/login", Map.of("phone", phone, "password", PASSWORD))).get("accessToken").asText();
    }

    protected String customerAccessToken() throws Exception {
        return registerCustomer(uniquePhone()).get("accessToken").asText();
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }
}
