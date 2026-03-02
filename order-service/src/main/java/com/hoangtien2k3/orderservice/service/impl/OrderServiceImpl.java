package com.hoangtien2k3.orderservice.service.impl;

import com.hoangtien2k3.orderservice.dto.order.OrderDto;
import com.hoangtien2k3.orderservice.dto.order.OrderStatusHistoryDto;
import com.hoangtien2k3.orderservice.entity.Order;
import com.hoangtien2k3.orderservice.entity.OrderStatus;
import com.hoangtien2k3.orderservice.entity.OrderStatusTrigger;
import com.hoangtien2k3.orderservice.exception.wrapper.CartNotFoundException;
import com.hoangtien2k3.orderservice.exception.wrapper.OrderNotFoundException;
import com.hoangtien2k3.orderservice.helper.OrderMappingHelper;
import com.hoangtien2k3.orderservice.repository.OrderRepository;
import com.hoangtien2k3.orderservice.repository.OrderStatusHistoryRepository;
import com.hoangtien2k3.orderservice.service.CallAPI;
import com.hoangtien2k3.orderservice.service.OrderService;
import com.hoangtien2k3.orderservice.service.OrderStatusHistoryService;
import com.hoangtien2k3.orderservice.service.OrderStatusTransitionValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;

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
    private final OrderStatusHistoryService orderStatusHistoryService;

    @Autowired
    private final OrderStatusTransitionValidator orderStatusTransitionValidator;

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
        return Mono.fromSupplier(() -> {
                    Order order = OrderMappingHelper.map(orderDto);
                    if (order.getStatus() == null) {
                        order.setStatus(OrderStatus.PENDING);
                    }
                    Order savedOrder = orderRepository.save(order);
                    orderStatusHistoryService.saveHistory(savedOrder, null, savedOrder.getStatus(),
                            OrderStatusTrigger.SYSTEM, null);
                    return OrderMappingHelper.map(savedOrder);
                })
                .onErrorResume(throwable -> {
                    log.error("Error saving order: {}", throwable.getMessage());
                    return Mono.error(throwable);
                });
    }

    @Override
    public Mono<OrderDto> update(final OrderDto orderDto) {
        log.info("OrderDto, service; update order");
        return Mono.fromSupplier(() -> updateExistingOrder(orderDto.getOrderId(), orderDto, false));
    }

    @Override
    public Mono<OrderDto> update(final Integer orderId, final OrderDto orderDto) {
        log.info("OrderDto, service; update order with orderId");
        return Mono.fromSupplier(() -> updateExistingOrder(orderId, orderDto, true));
    }

    @Override
    public Mono<Void> deleteById(final Integer orderId) {
        log.info("Void, service; delete order by id");
        return Mono.fromRunnable(() -> orderRepository.deleteById(orderId));
    }

    @Override
    public Mono<List<OrderStatusHistoryDto>> getStatusHistory(Integer orderId) {
        return Mono.fromSupplier(() -> {
            orderRepository.findById(orderId)
                    .orElseThrow(() -> new OrderNotFoundException(String.format("Order with id: %d not found", orderId)));
            return orderStatusHistoryRepository.findByOrderOrderIdOrderByChangedAtAsc(orderId)
                    .stream()
                    .map(history -> OrderStatusHistoryDto.builder()
                            .id(history.getId())
                            .orderId(history.getOrderId())
                            .previousStatus(history.getPreviousStatus())
                            .newStatus(history.getNewStatus())
                            .changedAt(history.getChangedAt())
                            .triggeredBy(history.getTriggeredBy())
                            .metadata(history.getMetadata())
                            .build())
                    .toList();
        });
    }

    private OrderDto updateExistingOrder(Integer orderId, OrderDto orderDto, boolean requireExisting) {
        Order existingOrder = null;
        if (orderId != null) {
            existingOrder = orderRepository.findById(orderId).orElse(null);
        }
        if (existingOrder == null) {
            if (requireExisting) {
                throw new CartNotFoundException("Cart with id " + orderId + " not found");
            }
            Order order = OrderMappingHelper.map(orderDto);
            OrderStatus newStatus = order.getStatus() == null ? OrderStatus.PENDING : order.getStatus();
            orderStatusTransitionValidator.validateTransition(null, newStatus);
            order.setStatus(newStatus);
            Order saved = orderRepository.save(order);
            orderStatusHistoryService.saveHistory(saved, null, saved.getStatus(),
                    OrderStatusTrigger.SYSTEM, null);
            return OrderMappingHelper.map(saved);
        }
        OrderDto existingOrderDto = OrderMappingHelper.map(existingOrder);
        OrderStatus previousStatus = existingOrderDto.getStatus();
        OrderStatus nextStatus = orderDto.getStatus() != null ? orderDto.getStatus() : previousStatus;
        if (nextStatus == null) {
            nextStatus = OrderStatus.PENDING;
        }
        orderStatusTransitionValidator.validateTransition(previousStatus, nextStatus);
        modelMapper.map(orderDto, existingOrderDto);
        existingOrderDto.setStatus(nextStatus);
        Order savedOrder = orderRepository.save(OrderMappingHelper.map(existingOrderDto));
        if (!Objects.equals(previousStatus, nextStatus)) {
            orderStatusHistoryService.saveHistory(savedOrder, previousStatus, nextStatus,
                    OrderStatusTrigger.USER, null);
        }
        return OrderMappingHelper.map(savedOrder);
    }

}
