package com.pk.core.test.common;

import org.springframework.aop.Advisor;
import org.springframework.aop.ClassFilter;
import org.springframework.aop.support.ComposablePointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;
import org.springframework.data.repository.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.interceptor.NameMatchTransactionAttributeSource;
import org.springframework.transaction.interceptor.RuleBasedTransactionAttribute;
import org.springframework.transaction.interceptor.TransactionInterceptor;

/**
 * Chỉ dùng trong test: nhiều test gọi thẳng repo (vd. {@code users.create(...)}, {@code placements.create(...)}) nhưng
 * repo Mirage không tự có giao dịch - SpringConnectionProvider ném "It seems not to be existing a transaction" nếu gọi
 * ngoài giao dịch. Bọc mỗi lệnh repo trong 1 giao dịch ngắn (REQUIRED: trong service đã có giao dịch thì tham gia tiếp).
 * Code production KHÔNG đổi: service phải tự khai báo @Transactional như trước.
 */
@TestConfiguration
public class RepoTxTestConfig {

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    Advisor repositoryTransactionAdvisor(PlatformTransactionManager transactionManager) {
        NameMatchTransactionAttributeSource attributes = new NameMatchTransactionAttributeSource();
        attributes.addTransactionalMethod("*", new RuleBasedTransactionAttribute());
        TransactionInterceptor interceptor = new TransactionInterceptor(transactionManager, attributes);
        ComposablePointcut pointcut = new ComposablePointcut(
                (ClassFilter) clazz -> Repository.class.isAssignableFrom(clazz));
        return new DefaultPointcutAdvisor(pointcut, interceptor);
    }
}
