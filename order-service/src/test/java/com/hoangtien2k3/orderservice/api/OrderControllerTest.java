package com.hoangtien2k3.orderservice.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hoangtien2k3.orderservice.dto.order.OrderDto;
import com.hoangtien2k3.orderservice.entity.OrderStatus;
import com.hoangtien2k3.orderservice.entity.OrderStatusHistory;
import com.hoangtien2k3.orderservice.entity.TriggeredBy;
import com.hoangtien2k3.orderservice.exception.wrapper.InvalidOrderStateException;
import com.hoangtien2k3.orderservice.exception.wrapper.OrderNotFoundException;
import com.hoangtien2k3.orderservice.service.OrderService;
import com.hoangtien2k3.orderservice.service.OrderStatusHistoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@AutoConfigureMockMvc
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @MockBean
    private OrderStatusHistoryService orderStatusHistoryService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(authorities = {"USER"})
    void testGetOrderStatusHistory() throws Exception {
        // Given
        Integer orderId = 1;
        List<OrderStatusHistory> history = Arrays.asList(
            OrderStatusHistory.builder()
                .id(1L)
                .orderId(orderId)
                .previousStatus(null)
                .newStatus(OrderStatus.PENDING)
                .changedAt(LocalDateTime.now().minusHours(2))
                .triggeredBy(TriggeredBy.SYSTEM)
                .build(),
            OrderStatusHistory.builder()
                .id(2L)
                .orderId(orderId)
                .previousStatus(OrderStatus.PENDING)
                .newStatus(OrderStatus.PAYMENT_CONFIRMED)
                .changedAt(LocalDateTime.now().minusHours(1))
                .triggeredBy(TriggeredBy.USER)
                .build()
        );

        when(orderStatusHistoryService.getOrderStatusHistory(orderId)).thenReturn(history);

        // When & Then
        mockMvc.perform(get("/api/orders/{orderId}/status-history", orderId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].orderId").value(orderId))
            .andExpect(jsonPath("$[0].newStatus").value("PENDING"))
            .andExpect(jsonPath("$[1].newStatus").value("PAYMENT_CONFIRMED"));
    }

    @Test
    @WithMockUser(authorities = {"ADMIN"})
    void testGetOrderStatusHistoryAsAdmin() throws Exception {
        // Given
        Integer orderId = 1;
        List<OrderStatusHistory> history = Arrays.asList(
            OrderStatusHistory.builder()
                .id(1L)
                .orderId(orderId)
                .previousStatus(OrderStatus.PENDING)
                .newStatus(OrderStatus.CANCELLED)
                .changedAt(LocalDateTime.now())
                .triggeredBy(TriggeredBy.USER)
                .build()
        );

        when(orderStatusHistoryService.getOrderStatusHistory(orderId)).thenReturn(history);

        // When & Then
        mockMvc.perform(get("/api/orders/{orderId}/status-history", orderId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @WithMockUser(authorities = {"USER"})
    void testGetOrderStatusHistoryForNonExistentOrder() throws Exception {
        // Given
        Integer orderId = 999;
        when(orderStatusHistoryService.getOrderStatusHistory(orderId)).thenReturn(List.of());

        // When & Then
        mockMvc.perform(get("/api/orders/{orderId}/status-history", orderId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(0));
    }

}
