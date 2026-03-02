package com.hoangtien2k3.orderservice.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hoangtien2k3.orderservice.OrderServiceApplication;
import com.hoangtien2k3.orderservice.dto.order.CartDto;
import com.hoangtien2k3.orderservice.dto.order.OrderDto;
import com.hoangtien2k3.orderservice.entity.OrderStatus;
import com.hoangtien2k3.orderservice.exception.ApiExceptionHandler;
import com.hoangtien2k3.orderservice.exception.wrapper.InvalidOrderStateException;
import com.hoangtien2k3.orderservice.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@WebMvcTest(controllers = OrderController.class)
@ContextConfiguration(classes = OrderServiceApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler.class)
class OrderControllerInvalidTransitionTest {

    @MockBean
    private OrderService orderService;

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @WithMockUser(authorities = "USER")
    void updateOrder_whenInvalidTransition_returnsConflict() throws Exception {
        OrderDto orderDto = OrderDto.builder()
                .orderId(1)
                .orderDesc("order")
                .orderFee(20.0)
                .productId(2)
                .status(OrderStatus.SHIPPED)
                .cartDto(CartDto.builder().cartId(10).userId(4L).build())
                .build();

        when(orderService.update(eq(1), any(OrderDto.class)))
                .thenReturn(Mono.error(new InvalidOrderStateException(
                        "Invalid order status transition from PENDING to SHIPPED")));

        mockMvc.perform(put("/api/orders/{orderId}", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderDto)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.httpStatus").value("CONFLICT"));
    }
}
