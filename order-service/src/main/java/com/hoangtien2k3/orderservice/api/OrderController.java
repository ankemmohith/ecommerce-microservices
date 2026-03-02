package com.hoangtien2k3.orderservice.api;

import com.hoangtien2k3.orderservice.dto.order.OrderDto;
import com.hoangtien2k3.orderservice.dto.order.OrderStatusHistoryDto;
import com.hoangtien2k3.orderservice.dto.saga.OrderSagaDto;
import com.hoangtien2k3.orderservice.service.OrderSagaService;
import com.hoangtien2k3.orderservice.service.OrderService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final OrderSagaService orderSagaService;

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

    @PostMapping("/{orderId}/cancel")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('USER')")
    public Mono<ResponseEntity<OrderDto>> cancelOrder(
            @PathVariable("orderId") final String orderId,
            @RequestParam(required = false) String reason) {
        log.info("*** OrderDto, resource; cancel order with orderId={} *", orderId);
        return orderService.cancelOrder(Integer.parseInt(orderId), reason)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/{orderId}/status-history")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('USER')")
    public Mono<ResponseEntity<List<OrderStatusHistoryDto>>> getStatusHistory(
            @PathVariable("orderId") final String orderId) {
        log.info("*** OrderStatusHistoryDto List, resource; fetch status history for orderId={} *", orderId);
        return orderService.getStatusHistory(Integer.parseInt(orderId))
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.ok(Collections.emptyList()));
    }

    @GetMapping("/saga/{sagaId}")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('USER')")
    public ResponseEntity<OrderSagaDto> getSaga(@PathVariable("sagaId") final String sagaId) {
        log.info("*** OrderSagaDto, resource; fetch saga by sagaId={} *", sagaId);
        return ResponseEntity.ok(orderSagaService.getSaga(sagaId));
    }

    @PostMapping("/{orderId}/retry")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('USER')")
    public ResponseEntity<OrderSagaDto> retrySaga(@PathVariable("orderId") final String orderId) {
        log.info("*** OrderSagaDto, resource; retry saga for orderId={} *", orderId);
        return ResponseEntity.ok(orderSagaService.retrySaga(Integer.parseInt(orderId)));
    }

}
