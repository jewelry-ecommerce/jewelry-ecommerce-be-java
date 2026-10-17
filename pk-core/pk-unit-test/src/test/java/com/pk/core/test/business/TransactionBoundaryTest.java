package com.pk.core.test.business;

import com.pk.core.business.service.ProductService;
import com.pk.core.business.service.impl.ProductServiceImpl;
import com.pk.core.model.dto.request.AdminCreateProductRequestDto;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Hồi quy lỗi "It seems not to be existing a transaction" khi import CSV tạo sản phẩm mới trên PostgreSQL.
 * Nguyên nhân: {@code ProductService.create(req)} là default method gọi {@code create(req, null)} trên target nên
 * bỏ qua proxy @Transactional. Test IT trên H2 KHÔNG bắt được vì RepoTxTestConfig tự bọc mọi lệnh repo trong giao dịch,
 * nên khoá ranh giới giao dịch bằng reflection: mọi overload create phải được ProductServiceImpl khai báo @Transactional.
 */
class TransactionBoundaryTest {

    @Test
    void productCreateOverloadsAreTransactionalOnImplementation() throws Exception {
        Method one = ProductServiceImpl.class.getMethod("create", AdminCreateProductRequestDto.class);
        Method two = ProductServiceImpl.class.getMethod("create", AdminCreateProductRequestDto.class, Long.class);
        // overload 1 tham số phải do chính Impl khai báo (không dùng default method của interface)
        assertSame(ProductServiceImpl.class, one.getDeclaringClass());
        assertNotNull(one.getAnnotation(Transactional.class), "create(req) phải có @Transactional");
        assertNotNull(two.getAnnotation(Transactional.class), "create(req, actorId) phải có @Transactional");
        // interface vẫn giữ chữ ký cũ
        assertNotNull(ProductService.class.getMethod("create", AdminCreateProductRequestDto.class));
    }
}
