package com.hoangtien2k3.orderservice.service.impl;

import com.hoangtien2k3.orderservice.dto.order.OrderDto;
import com.hoangtien2k3.orderservice.entity.Order;
import com.hoangtien2k3.orderservice.entity.OrderStatus;
import com.hoangtien2k3.orderservice.entity.OrderStatusHistory;
import com.hoangtien2k3.orderservice.exception.wrapper.CartNotFoundException;
import com.hoangtien2k3.orderservice.exception.wrapper.InvalidStateTransitionException;
import com.hoangtien2k3.orderservice.exception.wrapper.OrderNotFoundException;
import com.hoangtien2k3.orderservice.helper.OrderMappingHelper;
import com.hoangtien2k3.orderservice.repository.OrderRepository;
import com.hoangtien2k3.orderservice.repository.OrderStatusHistoryRepository;
import com.hoangtien2k3.orderservice.service.CallAPI;
import com.hoangtien2k3.orderservice.service.OrderService;
import com.hoangtien2k3.orderservice.service.SagaOrchestrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
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

    @Autowired
    private final SagaOrchestrationService sagaOrchestrationService;

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
        return Mono.fromSupplier(() -> {
                    // Set default status to PENDING for new orders
                    if (orderDto.getStatus() == null) {
                        orderDto.setStatus(OrderStatus.PENDING);
                    }
                    Order savedOrder = orderRepository.save(OrderMappingHelper.map(orderDto));
                    // Record initial status history
                    orderStatusHistoryRepository.save(OrderStatusHistory.builder()
                            .orderId(savedOrder.getOrderId())
                            .toStatus(OrderStatus.PENDING)
                            .changedAt(LocalDateTime.now())
                            .changedBy("SYSTEM")
                            .reason("Order created")
                            .build());
                    return savedOrder;
                })
                .map(OrderMappingHelper::map)
                .flatMap(savedDto -> {
                    // Start saga asynchronously
                    try {
                        sagaOrchestrationService.startSaga(savedDto.getOrderId());
                    } catch (Exception e) {
                        log.error("Failed to start saga for order {}: {}", savedDto.getOrderId(), e.getMessage());
                    }
                    return Mono.just(savedDto);
                })
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
    public Mono<OrderDto> cancelOrder(Integer orderId) {
        log.info("OrderDto, service; cancel order {}", orderId);
        return Mono.fromSupplier(() -> {
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new OrderNotFoundException(
                            String.format("Order with id: %d not found", orderId)));

            OrderStatus currentStatus = order.getStatus();
            if (currentStatus != null && !currentStatus.canTransitionTo(OrderStatus.CANCELLED)) {
                throw new InvalidStateTransitionException(
                        String.format("Cannot cancel order %d in status %s", orderId, currentStatus));
            }

            OrderStatus fromStatus = order.getStatus();
            order.setStatus(OrderStatus.CANCELLED);
            orderRepository.save(order);

            orderStatusHistoryRepository.save(OrderStatusHistory.builder()
                    .orderId(orderId)
                    .fromStatus(fromStatus)
                    .toStatus(OrderStatus.CANCELLED)
                    .changedAt(LocalDateTime.now())
                    .changedBy("USER")
                    .reason("User requested cancellation")
                    .build());

            return OrderMappingHelper.map(order);
        });
    }

    @Override
    public Mono<OrderDto> transitionStatus(Integer orderId, OrderStatus newStatus) {
        log.info("OrderDto, service; transition order {} to status {}", orderId, newStatus);
        return Mono.fromSupplier(() -> {
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new OrderNotFoundException(
                            String.format("Order with id: %d not found", orderId)));

            OrderStatus currentStatus = order.getStatus();
            if (currentStatus != null && !currentStatus.canTransitionTo(newStatus)) {
                throw new InvalidStateTransitionException(
                        String.format("Invalid transition from %s to %s for order %d",
                                currentStatus, newStatus, orderId));
            }

            order.setStatus(newStatus);
            orderRepository.save(order);

            orderStatusHistoryRepository.save(OrderStatusHistory.builder()
                    .orderId(orderId)
                    .fromStatus(currentStatus)
                    .toStatus(newStatus)
                    .changedAt(LocalDateTime.now())
                    .changedBy("API")
                    .reason("Manual status transition")
                    .build());

            return OrderMappingHelper.map(order);
        });
    }
}
