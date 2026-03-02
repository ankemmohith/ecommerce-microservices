package com.hoangtien2k3.orderservice.service.impl;

import com.hoangtien2k3.orderservice.dto.order.OrderDto;
import com.hoangtien2k3.orderservice.dto.order.OrderStatusHistoryDto;
import com.hoangtien2k3.orderservice.entity.Order;
import com.hoangtien2k3.orderservice.entity.OrderStatus;
import com.hoangtien2k3.orderservice.entity.OrderStatusHistory;
import com.hoangtien2k3.orderservice.exception.wrapper.CartNotFoundException;
import com.hoangtien2k3.orderservice.exception.wrapper.InvalidOrderStateException;
import com.hoangtien2k3.orderservice.exception.wrapper.OrderNotFoundException;
import com.hoangtien2k3.orderservice.helper.OrderMappingHelper;
import com.hoangtien2k3.orderservice.repository.OrderRepository;
import com.hoangtien2k3.orderservice.repository.OrderStatusHistoryRepository;
import com.hoangtien2k3.orderservice.service.CallAPI;
import com.hoangtien2k3.orderservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private final OrderRepository orderRepository;

    @Autowired
    private final ModelMapper modelMapper;

    @Autowired
    private final CallAPI callAPI;

    @Autowired
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Override
    public Mono<List<OrderDto>> findAll() {
        log.info("OrderDto List, service; fetch all orders");
        return Mono.fromSupplier(() -> orderRepository.findAll()
                        .stream()
                        .map(OrderMappingHelper::map)
                        .toList())
                .flatMap(listOrderDtos -> Flux.fromIterable(listOrderDtos)
                        .flatMap(orderDto ->
                                callAPI.receiverProductDto(orderDto.getProductId())
                                        .map(productDto -> {
                                            orderDto.setProductDto(productDto);
                                            return orderDto;
                                        })
                                        .onErrorResume(throwable -> {
                                            log.error("Error fetching product info: {}", throwable.getMessage());
                                            return Mono.just(orderDto);
                                        })
                        ).collectList()
                );
    }

    @Override
    public Mono<Page<OrderDto>> findAll(int page, int size, String sortBy, String sortOrder) {
        log.info("OrderDto List, service; fetch all carts with paging and sorting");
        Sort sort = Sort.by(Sort.Direction.fromString(sortOrder), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        return Mono.fromSupplier(() -> orderRepository.findAll(pageable)
                        .map(OrderMappingHelper::map)
                )
                .flatMap(orderDtos -> Flux.fromIterable(orderDtos)
                        .flatMap(productDtos ->
                                callAPI.receiverProductDto(productDtos.getProductId())
                                        .map(productDto -> {
                                            productDtos.setProductDto(productDto);
                                            return productDtos;
                                        })
                                        .onErrorResume(throwable -> {
                                            log.error("Error fetching product info: {}", throwable.getMessage());
                                            return Mono.just(productDtos);
                                        })
                        )
                        .collectList()
                        .map(resultList -> new PageImpl<>(resultList, pageable, resultList.size()))
                );
    }

    @Override
    public Mono<OrderDto> findById(Integer orderId) {
        log.info("OrderDto, service; fetch order by id");
        return Mono.fromSupplier(() -> orderRepository.findById(orderId)
                        .map(OrderMappingHelper::map)
                        .orElseThrow(() -> new OrderNotFoundException(String.format("Order with id: %d not found", orderId)))
                )
                .flatMap(orderDto ->
                        callAPI.receiverProductDto(orderDto.getProductId())
                                .map(productDto -> {
                                    orderDto.setProductDto(productDto);
                                    return orderDto;
                                })
                                .onErrorResume(throwable -> {
                                    log.error("Error fetching product info: {}", throwable.getMessage());
                                    return Mono.just(orderDto);
                                })
                );
    }

    // check orderId in exist in database.
    @Override
    public Boolean existsByOrderId(Integer orderId) {
         return orderRepository.findById(orderId).isPresent();
    }

    @Override
    public Mono<OrderDto> save(final OrderDto orderDto) {
        log.info("OrderDto, service; save order");
        return Mono.fromSupplier(() -> OrderMappingHelper.map(orderRepository.save(OrderMappingHelper.map(orderDto))))
                .onErrorResume(throwable -> {
                    log.error("Error saving order: {}", throwable.getMessage());
                    return Mono.error(throwable);
                });
    }

    @Override
    public Mono<OrderDto> update(final OrderDto orderDto) {
        log.info("OrderDto, service; update order");
        return Mono.fromSupplier(() -> orderRepository.save(OrderMappingHelper.map(orderDto)))
                .map(OrderMappingHelper::map);
    }

    @Override
    public Mono<OrderDto> update(final Integer orderId, final OrderDto orderDto) {
        log.info("OrderDto, service; update order with orderId");
        return findById(orderId).flatMap(existingOrderDto -> {
                    modelMapper.map(orderDto, existingOrderDto);
                    return Mono.fromSupplier(() -> orderRepository.save(OrderMappingHelper.map(existingOrderDto)))
                            .map(OrderMappingHelper::map);
                })
                .switchIfEmpty(Mono.error(new CartNotFoundException("Cart with id " + orderId + " not found")));
    }

    @Override
    public Mono<Void> deleteById(final Integer orderId) {
        log.info("Void, service; delete order by id");
        return Mono.fromRunnable(() -> orderRepository.deleteById(orderId));
    }

    @Override
    @Transactional
    public Mono<OrderDto> cancelOrder(Integer orderId, String reason) {
        log.info("OrderDto, service; cancel order with orderId={}", orderId);
        return Mono.fromSupplier(() -> {
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

            OrderStatus current = order.getStatus();
            if (!current.canTransitionTo(OrderStatus.CANCELLED)) {
                throw new InvalidOrderStateException(
                        String.format("Cannot cancel order %d: invalid transition from %s to CANCELLED", orderId, current));
            }

            OrderStatusHistory history = OrderStatusHistory.builder()
                    .orderId(orderId)
                    .fromStatus(current)
                    .toStatus(OrderStatus.CANCELLED)
                    .changedAt(Instant.now())
                    .changedBy("USER")
                    .reason(reason != null ? reason : "Customer requested cancellation")
                    .build();
            orderStatusHistoryRepository.save(history);

            order.setStatus(OrderStatus.CANCELLED);
            return OrderMappingHelper.map(orderRepository.save(order));
        });
    }

    @Override
    public Mono<List<OrderStatusHistoryDto>> getStatusHistory(Integer orderId) {
        log.info("OrderStatusHistoryDto List, service; fetch status history for orderId={}", orderId);
        return Mono.fromSupplier(() -> {
            if (!orderRepository.existsById(orderId)) {
                throw new OrderNotFoundException("Order not found: " + orderId);
            }
            return orderStatusHistoryRepository.findByOrderIdOrderByChangedAtAsc(orderId)
                    .stream()
                    .map(h -> OrderStatusHistoryDto.builder()
                            .id(h.getId())
                            .orderId(h.getOrderId())
                            .fromStatus(h.getFromStatus())
                            .toStatus(h.getToStatus())
                            .changedAt(h.getChangedAt())
                            .changedBy(h.getChangedBy())
                            .reason(h.getReason())
                            .build())
                    .toList();
        });
    }

}
