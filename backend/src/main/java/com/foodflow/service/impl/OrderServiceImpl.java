package com.foodflow.service.impl;

import com.foodflow.dto.common.PageResponse;
import com.foodflow.dto.order.CreateOrderRequest;
import com.foodflow.dto.order.OrderItemResponse;
import com.foodflow.dto.order.OrderResponse;
import com.foodflow.dto.order.OrderStatusHistoryResponse;
import com.foodflow.dto.order.OrderSummaryResponse;
import com.foodflow.entity.Address;
import com.foodflow.entity.Cart;
import com.foodflow.entity.CartItem;
import com.foodflow.entity.MenuItem;
import com.foodflow.entity.Order;
import com.foodflow.entity.OrderPaymentStatus;
import com.foodflow.entity.OrderStatus;
import com.foodflow.entity.PaymentMethod;
import com.foodflow.entity.Restaurant;
import com.foodflow.entity.Role;
import com.foodflow.exception.BadRequestException;
import com.foodflow.exception.ConflictException;
import com.foodflow.exception.ResourceNotFoundException;
import com.foodflow.repository.AddressRepository;
import com.foodflow.repository.CartRepository;
import com.foodflow.repository.OrderRepository;
import com.foodflow.repository.OrderSpecifications;
import com.foodflow.repository.ReviewRepository;
import com.foodflow.repository.UserRepository;
import com.foodflow.security.UserPrincipal;
import com.foodflow.entity.Coupon;
import com.foodflow.service.CouponService;
import com.foodflow.service.OrderService;
import com.foodflow.service.PaymentService;
import com.foodflow.service.payment.RefundRequestedEvent;
import org.springframework.context.ApplicationEventPublisher;
import com.foodflow.util.PageableUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final Set<String> SORTABLE = Set.of("createdAt", "totalAmount", "status");

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final AddressRepository addressRepository;
    private final UserRepository userRepository;
    private final OrderAccess orderAccess;
    private final PaymentService paymentService;
    private final CouponService couponService;
    private final ReviewRepository reviewRepository;
    private final ApplicationEventPublisher events;

    /*
     * Checkout. Every step below runs in ONE database transaction:
     *
     *   1. lock the cart (SELECT ... FOR UPDATE)     5. snapshot name + current price per line
     *   2. validate the cart is not empty            6. INSERT order, order_items, status history
     *   3. validate the address belongs to caller       (+ the expected cash payment, if COD)
     *   4. validate restaurant + every dish          7. empty the cart
     *                                                8. COMMIT
     *
     * Online payment (CARD/UPI) is NOT part of this transaction: it happens afterwards via
     * POST /api/payments, because a gateway call must never run inside a DB transaction.
     * Until it succeeds, the order is PLACED + payment PENDING and the restaurant can't confirm it.
     *
     * Why one transaction: all of these must happen together or not at all. If step 6 or 7
     * fails (constraint violation, crash, lost connection), PostgreSQL rolls back EVERYTHING:
     * no half-written order without lines, no order whose cart still exists (so the customer
     * could order the same food twice), and no emptied cart without an order.
     *
     * Any RuntimeException thrown here (including our ApiExceptions) triggers the rollback;
     * Spring's @Transactional rolls back on unchecked exceptions by default.
     */
    @Override
    @Transactional
    public OrderResponse placeOrder(Long customerId, CreateOrderRequest request) {
        // 1-2. Locking the cart also makes a double-click safe: the second request waits,
        //      then finds the cart already emptied by the first and fails with CART_EMPTY.
        Cart cart = cartRepository.findByUserIdForUpdate(customerId)
                .filter(c -> !c.isEmpty())
                .orElseThrow(() -> new BadRequestException("Your cart is empty"));

        // 3. Private data: someone else's address id is "not found".
        Address address = addressRepository.findByIdAndUserId(request.addressId(), customerId)
                .orElseThrow(() -> ResourceNotFoundException.of("Address", request.addressId()));

        // 4. Re-validate everything; the cart may be hours old.
        Restaurant restaurant = cart.getRestaurant();
        if (!restaurant.isActive()) {
            throw new ConflictException("RESTAURANT_UNAVAILABLE", restaurant.getName() + " is no longer available");
        }
        if (!restaurant.isOpen()) {
            throw new ConflictException("RESTAURANT_CLOSED", restaurant.getName() + " is not accepting orders right now");
        }
        List<String> unavailable = cart.getItems().stream()
                .map(CartItem::getMenuItem)
                .filter(menuItem -> !menuItem.isAvailable() || !menuItem.getRestaurant().getId().equals(restaurant.getId()))
                .map(MenuItem::getName)
                .toList();
        if (!unavailable.isEmpty()) {
            throw new ConflictException("ITEM_UNAVAILABLE",
                    "These items are no longer available: " + String.join(", ", unavailable)
                            + ". Remove them from your cart to continue.");
        }

        // 5-6. Build the order. addItem() copies the dish name and its CURRENT price.
        Order order = new Order(userRepository.getReferenceById(customerId), restaurant, address,
                address.toSingleLine(), request.paymentMethod());
        for (CartItem line : cart.getItems()) {
            order.addItem(line.getMenuItem(), line.getQuantity());
        }

        // Coupon: validated against the order's OWN subtotal, and one use taken atomically.
        // If anything later in this method fails, the used_count increment rolls back too.
        if (request.hasCoupon()) {
            Coupon coupon = couponService.redeem(request.couponCode(), order.getSubtotal());
            order.applyCoupon(coupon, coupon.calculateDiscount(order.getSubtotal()));
        }

        Order saved = orderRepository.save(order);
        if (request.paymentMethod() == PaymentMethod.CASH_ON_DELIVERY) {
            paymentService.createCashOnDeliveryPayment(saved); // joins THIS transaction
        }

        // 7. Emptying the cart is part of the same transaction.
        cart.clear();

        orderRepository.flush(); // write now, so constraint violations surface inside this method
        return toResponse(saved, Role.CUSTOMER);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> getMyOrders(Long customerId, Pageable pageable) {
        return PageResponse.from(orderRepository
                .findByCustomerId(customerId, PageableUtils.sanitize(pageable, SORTABLE))
                .map(OrderSummaryResponse::from));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> getOwnerOrders(Long ownerId, Long restaurantId, OrderStatus status,
                                                             Pageable pageable) {
        List<Specification<Order>> filters = new ArrayList<>();
        filters.add(OrderSpecifications.restaurantOwnedBy(ownerId)); // always: only MY restaurants
        if (restaurantId != null) {
            filters.add(OrderSpecifications.restaurantIs(restaurantId));
        }
        if (status != null) {
            filters.add(OrderSpecifications.statusIs(status));
        }
        return PageResponse.from(orderRepository
                .findAll(Specification.allOf(filters), PageableUtils.sanitize(pageable, SORTABLE))
                .map(OrderSummaryResponse::from));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> getAllOrders(Long restaurantId, OrderStatus status, Pageable pageable) {
        List<Specification<Order>> filters = new ArrayList<>();
        if (restaurantId != null) {
            filters.add(OrderSpecifications.restaurantIs(restaurantId));
        }
        if (status != null) {
            filters.add(OrderSpecifications.statusIs(status));
        }
        return PageResponse.from(orderRepository
                .findAll(Specification.allOf(filters), PageableUtils.sanitize(pageable, SORTABLE))
                .map(OrderSummaryResponse::from));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long orderId, UserPrincipal caller) {
        return toResponse(orderAccess.requireVisible(orderId, caller), caller.getRole());
    }

    /*
     * @Version on Order makes this safe under concurrency: if the owner confirms while the
     * customer cancels, both read version N; the first commit writes N+1, and the second
     * UPDATE ... WHERE version = N matches no row and fails with 409 CONCURRENT_MODIFICATION.
     * The order can never end up both CONFIRMED and CANCELLED.
     */
    @Override
    @Transactional
    public OrderResponse changeStatus(Long orderId, UserPrincipal caller, OrderStatus next, String note) {
        Order order = orderAccess.requireVisible(orderId, caller);
        order.changeStatus(next, userRepository.getReferenceById(caller.getId()), note);
        applyPaymentConsequences(order);
        orderRepository.flush();
        return toResponse(order, caller.getRole());
    }

    /*
     * What a status change means for the money:
     *   DELIVERED + cash       -> the rider collected it: payment SUCCESS, order PAID (same tx)
     *   CANCELLED + cash       -> the expected cash payment is cancelled (same tx)
     *   CANCELLED + paid online -> publish RefundRequestedEvent. The refund runs AFTER this
     *                              transaction commits (it calls the gateway, and must not
     *                              happen at all if this cancellation rolls back).
     */
    private void applyPaymentConsequences(Order order) {
        // A cancelled order gives its coupon use back, so the customer (or someone else) can use it.
        if (order.getStatus() == OrderStatus.CANCELLED && order.getCoupon() != null) {
            couponService.release(order.getCoupon().getId());
        }
        boolean cash = order.getPaymentMethod() == PaymentMethod.CASH_ON_DELIVERY;
        if (order.getStatus() == OrderStatus.DELIVERED && cash) {
            paymentService.recordCashCollected(order);
        } else if (order.getStatus() == OrderStatus.CANCELLED) {
            if (cash) {
                paymentService.cancelPendingCashPayment(order);
            } else if (order.getPaymentStatus() == OrderPaymentStatus.PAID) {
                events.publishEvent(new RefundRequestedEvent(order.getId()));
            }
        }
    }

    @Override
    @Transactional
    public OrderResponse cancelByCustomer(Long orderId, Long customerId, String reason) {
        Order order = orderRepository.findDetailedById(orderId)
                .filter(o -> o.getCustomer().getId().equals(customerId))
                .orElseThrow(() -> ResourceNotFoundException.of("Order", orderId));
        if (order.getStatus() != OrderStatus.PLACED) {
            throw new ConflictException("ORDER_NOT_CANCELLABLE",
                    "Orders can only be cancelled before the restaurant confirms them (current status: "
                            + order.getStatus() + ")");
        }
        order.changeStatus(OrderStatus.CANCELLED, userRepository.getReferenceById(customerId),
                reason == null || reason.isBlank() ? "Cancelled by customer" : reason.trim());
        applyPaymentConsequences(order);
        orderRepository.flush();
        return toResponse(order, Role.CUSTOMER);
    }

    /**
     * Which buttons the caller should see: customers may only cancel, and only while PLACED;
     * the restaurant can't confirm an online order that hasn't been paid yet.
     */
    private static Set<OrderStatus> allowedFor(Order order, Role role) {
        if (role == Role.CUSTOMER) {
            return order.getStatus() == OrderStatus.PLACED
                    ? EnumSet.of(OrderStatus.CANCELLED) : EnumSet.noneOf(OrderStatus.class);
        }
        Set<OrderStatus> allowed = EnumSet.noneOf(OrderStatus.class);
        allowed.addAll(order.getStatus().allowedNext());
        if (!order.isPaymentSettledForConfirmation()) {
            allowed.remove(OrderStatus.CONFIRMED);
        }
        return allowed;
    }

    private OrderResponse toResponse(Order order, Role callerRole) {
        return new OrderResponse(
                order.getId(),
                order.getStatus(),
                order.getRestaurant().getId(),
                order.getRestaurant().getName(),
                order.getCustomer().getId(),
                order.getCustomer().getName(),
                order.getCustomer().getPhone(),
                order.getDeliveryAddress(),
                order.getItems().stream().map(OrderItemResponse::from).toList(),
                order.getSubtotal(),
                order.getDiscountAmount(),
                order.getCouponCode(),
                order.getTotalAmount(),
                order.getPaymentMethod(),
                order.getPaymentStatus(),
                order.getCancellationReason(),
                order.getStatusHistory().stream().map(OrderStatusHistoryResponse::from).toList(),
                allowedFor(order, callerRole),
                order.getStatus() == OrderStatus.DELIVERED && reviewRepository.existsByOrderId(order.getId()),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }
}
