package com.tradingsystem;

import com.tradingsystem.config.KafkaConnectionDebugger;
import com.tradingsystem.mapper.AccountDataMapper;
import com.tradingsystem.model.Account;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@Import(TestSecurityConfig.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:runtimeconfigtests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.kafka.bootstrap-servers=localhost:1",
        "JWT_SECRET=test-secret-key-test-secret-key-test-secret-key"
})
@Sql(
        scripts = {"/schema-mybatis-test.sql", "/data-mybatis-test.sql"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
class MybatisRuntimeConfigurationTest {

    @Autowired
    private AccountDataMapper accountDataMapper;

    @MockitoBean
    private KafkaConnectionDebugger kafkaConnectionDebugger;

    @Test
    void accountMapperStatements_areAvailableInBootApplicationContext() {
        Account account = accountDataMapper.findAccountById(1001L);

        assertNotNull(account);
        assertEquals(1001L, account.getAccountId());
        assertEquals("ACC-1001", account.getAccountReference());
        assertEquals(new BigDecimal("100000.00"), account.getCashBalance());
    }
}

