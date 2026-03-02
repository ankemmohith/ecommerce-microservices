package com.hoangtien2k3.orderservice.repository;

import com.hoangtien2k3.orderservice.entity.Cart;
import com.hoangtien2k3.orderservice.entity.Order;
import com.hoangtien2k3.orderservice.entity.OrderStatus;
import com.hoangtien2k3.orderservice.entity.OrderStatusHistory;
import com.hoangtien2k3.orderservice.entity.OrderStatusTrigger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class OrderStatusHistoryRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Test
    void findByOrderOrderIdOrderByChangedAtAsc_returnsChronologicalHistory() {
        Cart cart = entityManager.persist(Cart.builder().userId(11L).build());
        Order order = entityManager.persist(Order.builder()
                .orderDate(null)
                .orderDesc("desc")
                .orderFee(10.0)
                .productId(99)
                .status(OrderStatus.PENDING)
                .cart(cart)
                .build());

        OrderStatusHistory first = OrderStatusHistory.builder()
                .order(order)
                .previousStatus(null)
                .newStatus(OrderStatus.PENDING)
                .changedAt(Instant.parse("2024-01-01T00:00:00Z"))
                .triggeredBy(OrderStatusTrigger.SYSTEM)
                .metadata(null)
                .build();
        OrderStatusHistory second = OrderStatusHistory.builder()
                .order(order)
                .previousStatus(OrderStatus.PENDING)
                .newStatus(OrderStatus.PAYMENT_CONFIRMED)
                .changedAt(Instant.parse("2024-01-02T00:00:00Z"))
                .triggeredBy(OrderStatusTrigger.USER)
                .metadata("{\"source\":\"test\"}")
                .build();

        orderStatusHistoryRepository.saveAll(List.of(first, second));
        entityManager.flush();

        List<OrderStatusHistory> history =
                orderStatusHistoryRepository.findByOrderOrderIdOrderByChangedAtAsc(order.getOrderId());

        assertThat(history).hasSize(2);
        assertThat(history.get(0).getNewStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(history.get(1).getNewStatus()).isEqualTo(OrderStatus.PAYMENT_CONFIRMED);
    }
}
