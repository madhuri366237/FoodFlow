package com.foodflow.service.impl;

import com.foodflow.dto.order.CreateOrderRequest;
import com.foodflow.dto.order.OrderResponse;
import com.foodflow.entity.Address;
import com.foodflow.entity.Cart;
import com.foodflow.entity.Coupon;
import com.foodflow.entity.DiscountType;
import com.foodflow.entity.MenuItem;
import com.foodflow.entity.Order;
import com.foodflow.entity.OrderPaymentStatus;
import com.foodflow.entity.OrderStatus;
import com.foodflow.entity.PaymentMethod;
import com.foodflow.entity.Restaurant;
import com.foodflow.entity.Role;
import com.foodflow.entity.User;
import com.foodflow.exception.BadRequestException;
import com.foodflow.exception.ConflictException;
import com.foodflow.exception.InvalidCouponException;
import com.foodflow.exception.ResourceNotFoundException;
import com.foodflow.repository.AddressRepository;
import com.foodflow.repository.CartRepository;
import com.foodflow.repository.OrderRepository;
import com.foodflow.repository.ReviewRepository;
import com.foodflow.repository.UserRepository;
import com.foodflow.service.CouponService;
import com.foodflow.service.PaymentService;
import com.foodflow.service.payment.RefundRequestedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static com.foodflow.TestFixtures.address;
import static com.foodflow.TestFixtures.dish;
import static com.foodflow.TestFixtures.emptyCart;
import static com.foodflow.TestFixtures.restaurant;
import static com.foodflow.TestFixtures.user;
import static com.foodflow.TestFixtures.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Checkout and cancellation rules, tested in isolation: every collaborator is a Mockito mock,
 * so each test states exactly which situation it sets up and runs in milliseconds.
 * (The real-database versions, including rollback, are in OrderApiIntegrationTest and
 * OrderAtomicityIntegrationTest.)
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    private static final long CUSTOMER_ID = 1L;

    @Mock private OrderRepository orderRepository;
    @Mock private CartRepository cartRepository;
    @Mock private AddressRepository addressRepository;
    @Mock private UserRepository userRepository;
    @Mock private OrderAccess orderAccess;
    @Mock private PaymentService paymentService;
    @Mock private CouponService couponService;
    @Mock private ReviewRepository reviewRepository;
    @Mock private ApplicationEventPublisher events;

    @InjectMocks
    private OrderServiceImpl orderService;

    private User customer;
    private Restaurant restaurant;
    private MenuItem biryani;   // 250.00
    private MenuItem raita;     //  40.00
    private Address home;
    private Cart cart;

    @BeforeEach
    void setUp() {
        customer = user(CUSTOMER_ID, Role.CUSTOMER);
        restaurant = restaurant(10L, user(2L, Role.RESTAURANT_OWNER), "Biryani Blues");
        biryani = dish(100L, restaurant, "Chicken Biryani", "250.00");
        raita = dish(101L, restaurant, "Raita", "40.00");
        home = address(5L, customer);
        cart = emptyCart();
        cart.addItem(biryani, 2);
        cart.addItem(raita, 1);

        lenient().when(cartRepository.findByUserIdForUpdate(CUSTOMER_ID)).thenReturn(Optional.of(cart));
        lenient().when(addressRepository.findByIdAndUserId(5L, CUSTOMER_ID)).thenReturn(Optional.of(home));
        lenient().when(userRepository.getReferenceById(CUSTOMER_ID)).thenReturn(customer);
        lenient().when(orderRepository.save(any(Order.class))).thenAnswer(call -> withId(call.getArgument(0), 900L));
    }

    private static CreateOrderRequest request(PaymentMethod method, String coupon) {
        return new CreateOrderRequest(5L, method, coupon);
    }

    @Nested
    class PlaceOrder {

        @Test
        void snapshotsPricesComputesTotalAndEmptiesCart() {
            OrderResponse order = orderService.placeOrder(CUSTOMER_ID, request(PaymentMethod.CARD, null));

            assertThat(order.status()).isEqualTo(OrderStatus.PLACED);
            assertThat(order.items()).extracting("name", "unitPrice", "quantity", "lineTotal").containsExactly(
                    org.assertj.core.groups.Tuple.tuple("Chicken Biryani", new BigDecimal("250.00"), 2, new BigDecimal("500.00")),
                    org.assertj.core.groups.Tuple.tuple("Raita", new BigDecimal("40.00"), 1, new BigDecimal("40.00")));
            assertThat(order.totalAmount()).isEqualByComparingTo("540.00");
            assertThat(order.paymentStatus()).isEqualTo(OrderPaymentStatus.PENDING);
            assertThat(order.deliveryAddress()).isEqualTo("Home: 12 MG Road, Bengaluru, Karnataka 560001");
            assertThat(cart.isEmpty()).isTrue();
            verifyNoInteractions(paymentService); // online payment happens later, via /api/payments
        }

        @Test
        void priceChangesAfterOrderingDoNotTouchTheOrder() {
            ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
            orderService.placeOrder(CUSTOMER_ID, request(PaymentMethod.UPI, null));
            verify(orderRepository).save(saved.capture());

            biryani.setPrice(new BigDecimal("999.00"));

            assertThat(saved.getValue().getItems().getFirst().getUnitPrice()).isEqualByComparingTo("250.00");
            assertThat(saved.getValue().getTotalAmount()).isEqualByComparingTo("540.00");
        }

        @Test
        void cashOnDeliveryRecordsTheExpectedPayment() {
            orderService.placeOrder(CUSTOMER_ID, request(PaymentMethod.CASH_ON_DELIVERY, null));

            verify(paymentService).createCashOnDeliveryPayment(any(Order.class));
        }

        @Test
        void couponIsRedeemedAgainstTheOrdersOwnSubtotal() {
            Coupon save10 = withId(new Coupon("SAVE10", DiscountType.PERCENTAGE, BigDecimal.TEN, Instant.MAX), 7L);
            when(couponService.redeem("SAVE10", new BigDecimal("540.00"))).thenReturn(save10);

            OrderResponse order = orderService.placeOrder(CUSTOMER_ID, request(PaymentMethod.CARD, "SAVE10"));

            assertThat(order.discountAmount()).isEqualByComparingTo("54.00");
            assertThat(order.totalAmount()).isEqualByComparingTo("486.00");
            assertThat(order.couponCode()).isEqualTo("SAVE10");
        }

        @Test
        void invalidCouponAbortsBeforeAnythingIsSaved() {
            when(couponService.redeem(any(), any())).thenThrow(new InvalidCouponException("COUPON_EXPIRED", "expired"));

            assertThatThrownBy(() -> orderService.placeOrder(CUSTOMER_ID, request(PaymentMethod.CARD, "OLD")))
                    .isInstanceOf(InvalidCouponException.class);
            verify(orderRepository, never()).save(any());
            assertThat(cart.isEmpty()).isFalse();
        }

        @Test
        void emptyCartIsRejected() {
            when(cartRepository.findByUserIdForUpdate(CUSTOMER_ID)).thenReturn(Optional.of(emptyCart()));

            assertThatThrownBy(() -> orderService.placeOrder(CUSTOMER_ID, request(PaymentMethod.CARD, null)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Your cart is empty");
        }

        @Test
        void unavailableDishRejectsTheWholeOrder() {
            raita.setAvailable(false);

            assertThatThrownBy(() -> orderService.placeOrder(CUSTOMER_ID, request(PaymentMethod.CARD, null)))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("Raita")
                    .extracting("errorCode").isEqualTo("ITEM_UNAVAILABLE");
            verify(orderRepository, never()).save(any());
            assertThat(cart.getItems()).hasSize(2);
        }

        @Test
        void closedRestaurantRejectsOrders() {
            restaurant.setOpen(false);

            assertThatThrownBy(() -> orderService.placeOrder(CUSTOMER_ID, request(PaymentMethod.CARD, null)))
                    .extracting("errorCode").isEqualTo("RESTAURANT_CLOSED");
        }

        @Test
        void someoneElsesAddressIsNotFound() {
            when(addressRepository.findByIdAndUserId(5L, CUSTOMER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.placeOrder(CUSTOMER_ID, request(PaymentMethod.CARD, null)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class CancelByCustomer {

        private Order placed(PaymentMethod method) {
            Order order = withId(new Order(customer, restaurant, home, "Home", method), 900L);
            order.addItem(biryani, 1);
            when(orderRepository.findDetailedById(900L)).thenReturn(Optional.of(order));
            return order;
        }

        @Test
        void placedOrderCanBeCancelled() {
            placed(PaymentMethod.CARD);

            OrderResponse order = orderService.cancelByCustomer(900L, CUSTOMER_ID, "Changed my mind");

            assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(order.cancellationReason()).isEqualTo("Changed my mind");
        }

        @Test
        void confirmedOrderCannotBeCancelled() {
            Order order = placed(PaymentMethod.CASH_ON_DELIVERY);
            order.changeStatus(OrderStatus.CONFIRMED, null, null);

            assertThatThrownBy(() -> orderService.cancelByCustomer(900L, CUSTOMER_ID, null))
                    .extracting("errorCode").isEqualTo("ORDER_NOT_CANCELLABLE");
        }

        @Test
        void someoneElsesOrderIsNotFound() {
            placed(PaymentMethod.CARD);

            assertThatThrownBy(() -> orderService.cancelByCustomer(900L, 999L, null))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void cancellingAPaidOnlineOrderRequestsARefund() {
            placed(PaymentMethod.UPI).markPaid();

            orderService.cancelByCustomer(900L, CUSTOMER_ID, null);

            verify(events).publishEvent(new RefundRequestedEvent(900L));
        }

        @Test
        void cancellingACashOrderCancelsTheExpectedPaymentInstead() {
            placed(PaymentMethod.CASH_ON_DELIVERY);

            orderService.cancelByCustomer(900L, CUSTOMER_ID, null);

            verify(paymentService).cancelPendingCashPayment(any(Order.class));
            verifyNoInteractions(events);
        }

        @Test
        void cancellingReleasesTheCouponUse() {
            Order order = placed(PaymentMethod.CARD);
            order.applyCoupon(withId(new Coupon("SAVE10", DiscountType.PERCENTAGE, BigDecimal.TEN, Instant.MAX), 7L),
                    new BigDecimal("25.00"));

            orderService.cancelByCustomer(900L, CUSTOMER_ID, null);

            verify(couponService).release(7L);
        }
    }
}
