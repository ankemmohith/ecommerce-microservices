package com.hoangtien2k3.orderservice.repository;

import com.hoangtien2k3.orderservice.entity.OrderStatus;
import com.hoangtien2k3.orderservice.entity.OrderStatusHistory;
import com.hoangtien2k3.orderservice.entity.TriggeredBy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.datasource.url=jdbc:h2:mem:testdb"
})
class OrderStatusHistoryRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private OrderStatusHistoryRepository repository;

    @Test
    void testSaveAndFindOrderStatusHistory() {
        // Given
        OrderStatusHistory history = OrderStatusHistory.builder()
                .orderId(1)
                .previousStatus(OrderStatus.PENDING)
                .newStatus(OrderStatus.PAYMENT_CONFIRMED)
                .changedAt(LocalDateTime.now())
                .triggeredBy(TriggeredBy.USER)
                .metadata("{\"userId\": 123}")
                .build();

        // When
        OrderStatusHistory saved = repository.save(history);
        entityManager.flush();

        // Then
        assertNotNull(saved.getId());
        assertEquals(1, saved.getOrderId());
        assertEquals(OrderStatus.PENDING, saved.getPreviousStatus());
        assertEquals(OrderStatus.PAYMENT_CONFIRMED, saved.getNewStatus());
        assertEquals(TriggeredBy.USER, saved.getTriggeredBy());
    }

    @Test
    void testFindByOrderIdOrderByChangedAtAsc() {
        // Given
        Integer orderId = 1;
        LocalDateTime now = LocalDateTime.now();

        OrderStatusHistory history1 = OrderStatusHistory.builder()
                .orderId(orderId)
                .previousStatus(null)
                .newStatus(OrderStatus.PENDING)
                .changedAt(now.minusHours(2))
                .triggeredBy(TriggeredBy.SYSTEM)
                .metadata(null)
                .build();

        OrderStatusHistory history2 = OrderStatusHistory.builder()
                .orderId(orderId)
                .previousStatus(OrderStatus.PENDING)
                .newStatus(OrderStatus.PAYMENT_CONFIRMED)
                .changedAt(now.minusHours(1))
                .triggeredBy(TriggeredBy.USER)
                .metadata("{\"paymentId\": \"PAY123\"}")
                .build();

        OrderStatusHistory history3 = OrderStatusHistory.builder()
                .orderId(orderId)
                .previousStatus(OrderStatus.PAYMENT_CONFIRMED)
                .newStatus(OrderStatus.PROCESSING)
                .changedAt(now)
                .triggeredBy(TriggeredBy.SAGA)
                .metadata(null)
                .build();

        entityManager.persist(history1);
        entityManager.persist(history2);
        entityManager.persist(history3);
        entityManager.flush();

        // When
        List<OrderStatusHistory> histories = repository.findByOrderIdOrderByChangedAtAsc(orderId);

        // Then
        assertEquals(3, histories.size());
        assertEquals(OrderStatus.PENDING, histories.get(0).getNewStatus());
        assertEquals(OrderStatus.PAYMENT_CONFIRMED, histories.get(1).getNewStatus());
        assertEquals(OrderStatus.PROCESSING, histories.get(2).getNewStatus());
    }

    @Test
    void testFindByOrderIdWithNoHistory() {
        // When
        List<OrderStatusHistory> histories = repository.findByOrderIdOrderByChangedAtAsc(999);

        // Then
        assertTrue(histories.isEmpty());
    }

    @Test
    void testMultipleOrdersHistory() {
        // Given
        LocalDateTime now = LocalDateTime.now();

        OrderStatusHistory order1History = OrderStatusHistory.builder()
                .orderId(1)
                .previousStatus(OrderStatus.PENDING)
                .newStatus(OrderStatus.PAYMENT_CONFIRMED)
                .changedAt(now)
                .triggeredBy(TriggeredBy.USER)
                .build();

        OrderStatusHistory order2History = OrderStatusHistory.builder()
                .orderId(2)
                .previousStatus(OrderStatus.PENDING)
                .newStatus(OrderStatus.CANCELLED)
                .changedAt(now)
                .triggeredBy(TriggeredBy.USER)
                .build();

        entityManager.persist(order1History);
        entityManager.persist(order2History);
        entityManager.flush();

        // When
        List<OrderStatusHistory> order1Histories = repository.findByOrderIdOrderByChangedAtAsc(1);
        List<OrderStatusHistory> order2Histories = repository.findByOrderIdOrderByChangedAtAsc(2);

        // Then
        assertEquals(1, order1Histories.size());
        assertEquals(1, order2Histories.size());
        assertEquals(OrderStatus.PAYMENT_CONFIRMED, order1Histories.get(0).getNewStatus());
        assertEquals(OrderStatus.CANCELLED, order2Histories.get(0).getNewStatus());
    }

}
