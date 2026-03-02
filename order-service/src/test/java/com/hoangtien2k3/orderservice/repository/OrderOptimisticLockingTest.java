package com.hoangtien2k3.orderservice.repository;

import com.hoangtien2k3.orderservice.entity.Cart;
import com.hoangtien2k3.orderservice.entity.Order;
import com.hoangtien2k3.orderservice.entity.OrderStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
class OrderOptimisticLockingTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void optimisticLockingPreventsConcurrentUpdates() {
        Cart cart = entityManager.persist(Cart.builder().userId(22L).build());
        Order order = entityManager.persist(Order.builder()
                .orderDesc("order")
                .orderFee(12.0)
                .productId(5)
                .status(OrderStatus.PENDING)
                .cart(cart)
                .build());
        entityManager.flush();

        EntityManager firstManager = entityManagerFactory.createEntityManager();
        EntityManager secondManager = entityManagerFactory.createEntityManager();
        try {
            firstManager.getTransaction().begin();
            secondManager.getTransaction().begin();

            Order firstOrder = firstManager.find(Order.class, order.getOrderId());
            Order secondOrder = secondManager.find(Order.class, order.getOrderId());

            firstOrder.setStatus(OrderStatus.PAYMENT_CONFIRMED);
            firstManager.flush();
            firstManager.getTransaction().commit();

            secondOrder.setStatus(OrderStatus.CANCELLED);
            assertThrows(OptimisticLockException.class, () -> {
                secondManager.flush();
                secondManager.getTransaction().commit();
            });
        } finally {
            if (firstManager.getTransaction().isActive()) {
                firstManager.getTransaction().rollback();
            }
            if (secondManager.getTransaction().isActive()) {
                secondManager.getTransaction().rollback();
            }
            firstManager.close();
            secondManager.close();
        }
    }
}
