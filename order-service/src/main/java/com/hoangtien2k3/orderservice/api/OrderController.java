package com.hoangtien2k3.orderservice.api;

import com.hoangtien2k3.orderservice.dto.order.OrderDto;
import com.hoangtien2k3.orderservice.entity.OrderStatusHistory;
import com.hoangtien2k3.orderservice.service.OrderService;
import com.hoangtien2k3.orderservice.service.OrderStatusHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Collections;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/orders")
@Tag(name = "OrderController", description = "Operations related to orders")
public class OrderController {

    private final OrderService orderService;
    private final OrderStatusHistoryService orderStatusHistoryService;

    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('USER')")
    public Mono<ResponseEntity<List<OrderDto>>> findAll() {
        log.info("*** OrderDto List, controller; fetch all orders *");
        return orderService.findAll()
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.ok(Collections.emptyList()));
    }

    @GetMapping("/all")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('USER')")
    public Mono<ResponseEntity<Page<OrderDto>>> findAll(@RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "10") int size,
                                                        @RequestParam(defaultValue = "orderId") String sortBy,
                                                        @RequestParam(defaultValue = "asc") String sortOrder) {
        return orderService.findAll(page, size, sortBy, sortOrder)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.noContent().build());
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('USER')")
    public ResponseEntity<Mono<OrderDto>> findById(@PathVariable("orderId")
                                                   @NotBlank(message = "Input must not be blank")
                                                   @Valid final String orderId) {
        log.info("*** OrderDto, resource; fetch order by id *");
        return ResponseEntity.ok(orderService.findById(Integer.parseInt(orderId)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USER')")
    public Mono<ResponseEntity<OrderDto>> save(@RequestBody
                                               @NotNull(message = "Input must not be NULL")
                                               @Valid final OrderDto orderDto) {
        log.info("*** OrderDto, resource; save order *");
        return orderService.save(orderDto)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
    }

    @PutMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public Mono<ResponseEntity<OrderDto>> update(@RequestBody
                                                 @NotNull(message = "Input must not be NULL")
                                                 @Valid final OrderDto orderDto) {
        log.info("*** OrderDto, resource; update order *");
        return orderService.update(orderDto)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PutMapping("/{orderId}")
    @PreAuthorize("hasAuthority('USER')")
    public Mono<ResponseEntity<OrderDto>> update(@PathVariable("orderId")
                                                 @NotBlank(message = "Input must not be blank")
                                                 @Valid final String orderId,
                                                 @RequestBody
                                                 @NotNull(message = "Input must not be NULL")
                                                 @Valid final OrderDto orderDto) {
        log.info("*** OrderDto, resource; update order with orderId *");
        return orderService.update(Integer.parseInt(orderId), orderDto)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{orderId}")
    @PreAuthorize("hasAuthority('USER') or hasAuthority('ADMIN')")
    public Mono<ResponseEntity<Boolean>> deleteById(@PathVariable("orderId") final String orderId) {
        log.info("*** Boolean, resource; delete order by id *");
        this.orderService.deleteById(Integer.parseInt(orderId));
        return orderService.deleteById(Integer.parseInt(orderId))
                .thenReturn(ResponseEntity.ok(true))
                .defaultIfEmpty(ResponseEntity.status(HttpStatus.NOT_FOUND).body(false));
    }


    @GetMapping("/existOrderId")
    public Boolean existsByOrderId(Integer orderId) {
        return orderService.existsByOrderId(orderId);
    }

    @GetMapping("/{orderId}/status-history")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('USER')")
    public ResponseEntity<List<OrderStatusHistory>> getOrderStatusHistory(@PathVariable("orderId")
                                                                          @NotBlank(message = "Input must not be blank")
                                                                          @Valid final String orderId) {
        log.info("*** OrderStatusHistory List, resource; fetch status history for order {} *", orderId);
        List<OrderStatusHistory> history = orderStatusHistoryService.getOrderStatusHistory(Integer.parseInt(orderId));
        return ResponseEntity.ok(history);
    }

}
