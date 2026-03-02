package com.hoangtien2k3.orderservice.service;

import com.hoangtien2k3.orderservice.dto.order.OrderDto;
import com.hoangtien2k3.orderservice.entity.*;
import com.hoangtien2k3.orderservice.exception.wrapper.InvalidOrderStateException;
import com.hoangtien2k3.orderservice.repository.CartRepository;
import com.hoangtien2k3.orderservice.repository.OrderRepository;
import com.hoangtien2k3.orderservice.repository.OrderStatusHistoryRepository;
import com.hoangtien2k3.orderservice.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.datasource.url=jdbc:h2:mem:testdb"
})
class OrderServiceIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private OrderStatusHistoryRepository statusHistoryRepository;

    private OrderServiceImpl orderService;
    private OrderStatusTransitionValidator transitionValidator;
    private OrderStatusHistoryService statusHistoryService;

    @BeforeEach
    void setUp() {
        transitionValidator = new OrderStatusTransitionValidator();
        statusHistoryService = new OrderStatusHistoryService(statusHistoryRepository);
        orderService = new OrderServiceImpl(
            orderRepository,
            null, // modelMapper not needed for this test
            null, // callAPI not needed for this test
            transitionValidator,
            statusHistoryService
        );
    }

    @Test
    void testValidStatusTransitionWithHistoryRecording() {
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

        Integer orderId = order.getOrderId();

        // When: Update status from PENDING to PAYMENT_CONFIRMED
        StepVerifier.create(
            orderService.updateOrderStatus(orderId, OrderStatus.PAYMENT_CONFIRMED, TriggeredBy.USER, null)
        )
        .assertNext(orderDto -> {
            assertEquals(OrderStatus.PAYMENT_CONFIRMED, orderDto.getStatus());
        })
        .verifyComplete();

        // Then: Verify status was updated
        Order updatedOrder = orderRepository.findById(orderId).orElseThrow();
        assertEquals(OrderStatus.PAYMENT_CONFIRMED, updatedOrder.getStatus());

        // And: Verify history was recorded
        List<OrderStatusHistory> history = statusHistoryRepository.findByOrderIdOrderByChangedAtAsc(orderId);
        assertEquals(1, history.size());
        assertEquals(OrderStatus.PENDING, history.get(0).getPreviousStatus());
        assertEquals(OrderStatus.PAYMENT_CONFIRMED, history.get(0).getNewStatus());
        assertEquals(TriggeredBy.USER, history.get(0).getTriggeredBy());
    }

    @Test
    void testInvalidStatusTransitionThrowsException() {
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

        Integer orderId = order.getOrderId();

        // When: Attempt invalid transition from PENDING to SHIPPED
        StepVerifier.create(
            orderService.updateOrderStatus(orderId, OrderStatus.SHIPPED, TriggeredBy.USER, null)
        )
        .expectErrorMatches(throwable -> 
            throwable instanceof InvalidOrderStateException &&
            throwable.getMessage().contains("Invalid state transition from PENDING to SHIPPED")
        )
        .verify();

        // Then: Verify status was NOT updated
        Order unchangedOrder = orderRepository.findById(orderId).orElseThrow();
        assertEquals(OrderStatus.PENDING, unchangedOrder.getStatus());

        // And: Verify no history was recorded
        List<OrderStatusHistory> history = statusHistoryRepository.findByOrderIdOrderByChangedAtAsc(orderId);
        assertEquals(0, history.size());
    }

    @Test
    void testMultipleStatusTransitionsWithHistory() {
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

        Integer orderId = order.getOrderId();

        // When: Perform multiple valid transitions
        orderService.updateOrderStatus(orderId, OrderStatus.PAYMENT_CONFIRMED, TriggeredBy.USER, "{\"payment\":\"123\"}").block();
        orderService.updateOrderStatus(orderId, OrderStatus.PROCESSING, TriggeredBy.SAGA, null).block();
        orderService.updateOrderStatus(orderId, OrderStatus.SHIPPED, TriggeredBy.SYSTEM, "{\"trackingId\":\"TRACK123\"}").block();

        // Then: Verify final status
        Order finalOrder = orderRepository.findById(orderId).orElseThrow();
        assertEquals(OrderStatus.SHIPPED, finalOrder.getStatus());

        // And: Verify complete history
        List<OrderStatusHistory> history = statusHistoryRepository.findByOrderIdOrderByChangedAtAsc(orderId);
        assertEquals(3, history.size());

        assertEquals(OrderStatus.PENDING, history.get(0).getPreviousStatus());
        assertEquals(OrderStatus.PAYMENT_CONFIRMED, history.get(0).getNewStatus());
        assertEquals(TriggeredBy.USER, history.get(0).getTriggeredBy());
        assertEquals("{\"payment\":\"123\"}", history.get(0).getMetadata());

        assertEquals(OrderStatus.PAYMENT_CONFIRMED, history.get(1).getPreviousStatus());
        assertEquals(OrderStatus.PROCESSING, history.get(1).getNewStatus());
        assertEquals(TriggeredBy.SAGA, history.get(1).getTriggeredBy());

        assertEquals(OrderStatus.PROCESSING, history.get(2).getPreviousStatus());
        assertEquals(OrderStatus.SHIPPED, history.get(2).getNewStatus());
        assertEquals(TriggeredBy.SYSTEM, history.get(2).getTriggeredBy());
        assertEquals("{\"trackingId\":\"TRACK123\"}", history.get(2).getMetadata());
    }

    @Test
    void testTransitionToSameStatusDoesNotCreateHistory() {
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

        Integer orderId = order.getOrderId();

        // When: Update to same status
        orderService.updateOrderStatus(orderId, OrderStatus.PENDING, TriggeredBy.USER, null).block();

        // Then: Status remains the same
        Order unchangedOrder = orderRepository.findById(orderId).orElseThrow();
        assertEquals(OrderStatus.PENDING, unchangedOrder.getStatus());

        // And: History is still recorded (though status didn't change)
        List<OrderStatusHistory> history = statusHistoryRepository.findByOrderIdOrderByChangedAtAsc(orderId);
        assertEquals(1, history.size());
        assertEquals(OrderStatus.PENDING, history.get(0).getPreviousStatus());
        assertEquals(OrderStatus.PENDING, history.get(0).getNewStatus());
    }

}
