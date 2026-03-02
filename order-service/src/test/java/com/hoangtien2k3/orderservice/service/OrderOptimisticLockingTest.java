package com.hoangtien2k3.orderservice.service;

import com.hoangtien2k3.orderservice.entity.Cart;
import com.hoangtien2k3.orderservice.entity.Order;
import com.hoangtien2k3.orderservice.entity.OrderStatus;
import com.hoangtien2k3.orderservice.entity.TriggeredBy;
import com.hoangtien2k3.orderservice.repository.CartRepository;
import com.hoangtien2k3.orderservice.repository.OrderRepository;
import com.hoangtien2k3.orderservice.repository.OrderStatusHistoryRepository;
import com.hoangtien2k3.orderservice.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.datasource.url=jdbc:h2:mem:testdb"
})
class OrderOptimisticLockingTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private OrderStatusHistoryRepository statusHistoryRepository;

    private OrderStatusTransitionValidator transitionValidator;
    private OrderStatusHistoryService statusHistoryService;

    @BeforeEach
    void setUp() {
        transitionValidator = new OrderStatusTransitionValidator();
        statusHistoryService = new OrderStatusHistoryService(statusHistoryRepository);
    }

    @Test
    void testOptimisticLockingPreventsConflictingUpdates() throws InterruptedException {
        // Given: Create a cart and an order
        Cart cart = Cart.builder()
                .userId(1)
                .build();
        cart = cartRepository.save(cart);

        Order order = Order.builder()
                .cart(cart)
                .orderDate(LocalDateTime.now())
                .orderDesc("Test Order")
                .orderFee(100.0)
                .productId(1)
                .status(OrderStatus.PENDING)
                .build();
        order = orderRepository.save(order);
        entityManager.flush();
        entityManager.clear();

        final Integer orderId = order.getOrderId();
        final CountDownLatch latch = new CountDownLatch(2);
        final AtomicInteger successCount = new AtomicInteger(0);
        final AtomicInteger failureCount = new AtomicInteger(0);

        // When: Two threads try to update the order status concurrently
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Runnable task1 = () -> {
            try {
                Order order1 = orderRepository.findById(orderId).orElseThrow();
                // Simulate some processing time
                Thread.sleep(50);
                order1.setStatus(OrderStatus.PAYMENT_CONFIRMED);
                orderRepository.saveAndFlush(order1);
                successCount.incrementAndGet();
            } catch (ObjectOptimisticLockingFailureException | jakarta.persistence.OptimisticLockException e) {
                failureCount.incrementAndGet();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
        };

        Runnable task2 = () -> {
            try {
                Order order2 = orderRepository.findById(orderId).orElseThrow();
                // Simulate some processing time
                Thread.sleep(50);
                order2.setStatus(OrderStatus.CANCELLED);
                orderRepository.saveAndFlush(order2);
                successCount.incrementAndGet();
            } catch (ObjectOptimisticLockingFailureException | jakarta.persistence.OptimisticLockException e) {
                failureCount.incrementAndGet();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
        };

        executor.submit(task1);
        executor.submit(task2);

        latch.await();
        executor.shutdown();

        // Then: Only one update should succeed
        assertEquals(1, successCount.get(), "Only one concurrent update should succeed");
        assertEquals(1, failureCount.get(), "One concurrent update should fail due to optimistic locking");

        // Verify the final state
        Order finalOrder = orderRepository.findById(orderId).orElseThrow();
        assertNotNull(finalOrder.getVersion());
        assertTrue(finalOrder.getVersion() > 0, "Version should be incremented");
    }

    @Test
    void testVersionIncrementOnUpdate() {
        // Given
        Cart cart = Cart.builder()
                .userId(1)
                .build();
        cart = cartRepository.save(cart);

        Order order = Order.builder()
                .cart(cart)
                .orderDate(LocalDateTime.now())
                .orderDesc("Test Order")
                .orderFee(100.0)
                .productId(1)
                .status(OrderStatus.PENDING)
                .build();
        order = orderRepository.save(order);
        entityManager.flush();

        Long initialVersion = order.getVersion();

        // When
        order.setStatus(OrderStatus.PAYMENT_CONFIRMED);
        order = orderRepository.save(order);
        entityManager.flush();

        // Then
        assertNotNull(order.getVersion());
        assertTrue(order.getVersion() > initialVersion, "Version should be incremented after update");
    }

    @Test
    void testStaleObjectUpdate() {
        // Given
        Cart cart = Cart.builder()
                .userId(1)
                .build();
        cart = cartRepository.save(cart);

        Order order = Order.builder()
                .cart(cart)
                .orderDate(LocalDateTime.now())
                .orderDesc("Test Order")
                .orderFee(100.0)
                .productId(1)
                .status(OrderStatus.PENDING)
                .build();
        order = orderRepository.save(order);
        entityManager.flush();
        entityManager.clear();

        // Get two references to the same order
        Order order1 = orderRepository.findById(order.getOrderId()).orElseThrow();
        Order order2 = orderRepository.findById(order.getOrderId()).orElseThrow();

        // When: Update first reference
        order1.setStatus(OrderStatus.PAYMENT_CONFIRMED);
        orderRepository.saveAndFlush(order1);
        entityManager.clear();

        // Then: Update second reference should fail
        order2.setStatus(OrderStatus.CANCELLED);
        assertThrows(
            ObjectOptimisticLockingFailureException.class,
            () -> orderRepository.saveAndFlush(order2),
            "Updating stale object should throw OptimisticLockingFailureException"
        );
    }

}
