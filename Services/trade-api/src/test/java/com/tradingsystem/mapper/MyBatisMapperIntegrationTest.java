package com.tradingsystem.mapper;

import com.tradingsystem.dto.response.OrderHistoryEntry;
import com.tradingsystem.enums.OrderSide;
import com.tradingsystem.enums.OrderStatus;
import com.tradingsystem.enums.OrderType;
import com.tradingsystem.model.Account;
import com.tradingsystem.model.Order;
import com.tradingsystem.model.Position;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MybatisTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:mappertests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "mybatis.mapper-locations=classpath*:mapper/*.xml",
        "mybatis.type-handlers-package=com.tradingsystem.mapper.typehandler"
})
@Sql(scripts = {"/schema-mybatis-test.sql", "/data-mybatis-test.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class MyBatisMapperIntegrationTest {

    @Autowired
    private AccountDataMapper accountDataMapper;

    @Autowired
    private OrderDataMapper orderDataMapper;

    @Test
    void order_row_inserted_and_retrievable() {
        Order order = newOrder("idem-insert-1");

        int affected = orderDataMapper.insertOrder(order);
        assertEquals(1, affected);

        List<OrderHistoryEntry> history = orderDataMapper.findOrderHistory(
                1001L,
                "NEW",
                OffsetDateTime.now(ZoneOffset.UTC).minusDays(1),
                OffsetDateTime.now(ZoneOffset.UTC).plusDays(1)
        );

        assertEquals(1, history.size());
        assertEquals(order.getId().toString(), history.getFirst().getOrderId());
        assertEquals("AAPL", history.getFirst().getSymbol());
        assertEquals(OrderStatus.NEW, history.getFirst().getStatus());
    }

    @Test
    void account_and_positions_read_correctly() {
        Account account = accountDataMapper.findAccountById(1001L);
        assertNotNull(account);
        assertEquals(1001L, account.getAccountId());
        assertEquals(new BigDecimal("100000.00"), account.getCashBalance());

        List<Position> positions = accountDataMapper.findPositionsByAccountId(1001L);
        assertEquals(1, positions.size());
        assertEquals("AAPL", positions.getFirst().getSymbol());
        assertEquals(15, positions.getFirst().getQuantity());
    }

    @Test
    void mapper_reports_affected_row_count() {
        Order order = newOrder("idem-row-count-1");
        int affected = orderDataMapper.insertOrder(order);
        assertTrue(affected > 0, "Mapper must return number of affected rows");
    }

    @Test
    void mapper_surfaces_constraint_violation() {
        Order first = newOrder("idem-unique-1");
        Order duplicate = newOrder("idem-unique-1");

        int firstInsert = orderDataMapper.insertOrder(first);
        assertEquals(1, firstInsert);

        assertThrows(DataIntegrityViolationException.class, () -> orderDataMapper.insertOrder(duplicate));
    }

    private static Order newOrder(String idempotencyKey) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setAccountId(1001L);
        order.setSymbol("AAPL");
        order.setSide(OrderSide.BUY);
        order.setOrderType(OrderType.LIMIT);
        order.setQuantity(5);
        order.setPrice(new BigDecimal("190.00"));
        order.setStatus(OrderStatus.NEW);
        order.setIdempotencyKey(idempotencyKey);
        order.setCreatedOn(LocalDateTime.now());
        return order;
    }
}


