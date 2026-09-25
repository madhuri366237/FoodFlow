package com.foodflow.service.impl;

import com.foodflow.dto.cart.CartItemRequest;
import com.foodflow.dto.cart.CartResponse;
import com.foodflow.entity.Cart;
import com.foodflow.entity.Category;
import com.foodflow.entity.MenuItem;
import com.foodflow.entity.Restaurant;
import com.foodflow.entity.Role;
import com.foodflow.entity.User;
import com.foodflow.exception.ConflictException;
import com.foodflow.repository.CartRepository;
import com.foodflow.repository.MenuItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The cart rules in isolation: repositories are mocks, no database, no Spring. */
@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

    private static final long USER_ID = 7L;

    @Mock private CartRepository cartRepository;
    @Mock private MenuItemRepository menuItemRepository;

    @InjectMocks
    private CartServiceImpl cartService;

    private Cart cart;
    private MenuItem biryani;
    private MenuItem pizza;

    @BeforeEach
    void setUp() throws Exception {
        User owner = new User("Owner", "o@example.com", "h", null, Role.RESTAURANT_OWNER);
        Category category = new Category("Test", null, null);
        biryani = dish(restaurant(owner, 1L, "Biryani Blues"), category, 11L, "Chicken Biryani", "250.00");
        pizza = dish(restaurant(owner, 2L, "Pizza Point"), category, 22L, "Veg Pizza", "199.00");

        Constructor<Cart> constructor = Cart.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        cart = constructor.newInstance();

        // lenient: some tests fail before the cart is loaded, which is exactly what they check.
        org.mockito.Mockito.lenient().when(cartRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(cart));
    }

    private static Restaurant restaurant(User owner, long id, String name) {
        Restaurant restaurant = new Restaurant(owner, name, null, "Road", "9000000000", null);
        ReflectionTestUtils.setField(restaurant, "id", id);
        return restaurant;
    }

    private static MenuItem dish(Restaurant restaurant, Category category, long id, String name, String price) {
        MenuItem item = new MenuItem(restaurant, category, name, null, new BigDecimal(price), null);
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }

    @Test
    void addItemPricesFromTheMenuItemEntity() {
        when(menuItemRepository.findWithRestaurantById(11L)).thenReturn(Optional.of(biryani));

        CartResponse response = cartService.addItem(USER_ID, new CartItemRequest(11L, 3));

        assertThat(response.subtotal()).isEqualByComparingTo("750.00");
        assertThat(response.items().getFirst().unitPrice()).isEqualByComparingTo("250.00");
        assertThat(response.restaurantName()).isEqualTo("Biryani Blues");
        verify(cartRepository).createIfAbsent(USER_ID); // cart created lazily on first write
    }

    @Test
    void addingFromAnotherRestaurantThrowsAndLeavesCartUntouched() {
        when(menuItemRepository.findWithRestaurantById(11L)).thenReturn(Optional.of(biryani));
        when(menuItemRepository.findWithRestaurantById(22L)).thenReturn(Optional.of(pizza));
        cartService.addItem(USER_ID, new CartItemRequest(11L, 1));

        assertThatThrownBy(() -> cartService.addItem(USER_ID, new CartItemRequest(22L, 1)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Biryani Blues")
                .extracting("errorCode").isEqualTo("CART_RESTAURANT_MISMATCH");

        assertThat(cart.getItems()).hasSize(1);
        assertThat(cart.getRestaurant().getName()).isEqualTo("Biryani Blues");
    }

    @Test
    void addingTheSameDishAgainIncreasesTheLineQuantity() {
        when(menuItemRepository.findWithRestaurantById(11L)).thenReturn(Optional.of(biryani));
        cartService.addItem(USER_ID, new CartItemRequest(11L, 2));

        CartResponse response = cartService.addItem(USER_ID, new CartItemRequest(11L, 3));

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().quantity()).isEqualTo(5);
        assertThat(response.subtotal()).isEqualByComparingTo("1250.00");
    }

    @Test
    void cumulativeQuantityAboveTwentyIsRejected() {
        when(menuItemRepository.findWithRestaurantById(11L)).thenReturn(Optional.of(biryani));
        cartService.addItem(USER_ID, new CartItemRequest(11L, 15));

        assertThatThrownBy(() -> cartService.addItem(USER_ID, new CartItemRequest(11L, 6)))
                .isInstanceOf(com.foodflow.exception.BadRequestException.class);
        assertThat(cart.getItems().getFirst().getQuantity()).isEqualTo(15);
    }

    @Test
    void unavailableDishIsRejectedBeforeTheCartIsTouched() {
        biryani.setAvailable(false);
        when(menuItemRepository.findWithRestaurantById(11L)).thenReturn(Optional.of(biryani));

        assertThatThrownBy(() -> cartService.addItem(USER_ID, new CartItemRequest(11L, 1)))
                .extracting("errorCode").isEqualTo("ITEM_UNAVAILABLE");
        verify(cartRepository, org.mockito.Mockito.never()).createIfAbsent(USER_ID);
    }

    @Test
    void closedRestaurantIsRejected() {
        biryani.getRestaurant().setOpen(false);
        when(menuItemRepository.findWithRestaurantById(11L)).thenReturn(Optional.of(biryani));

        assertThatThrownBy(() -> cartService.addItem(USER_ID, new CartItemRequest(11L, 1)))
                .extracting("errorCode").isEqualTo("RESTAURANT_CLOSED");
    }

    @Test
    void updatingALineThatIsNotInMyCartIsNotFound() {
        assertThatThrownBy(() -> cartService.updateQuantity(USER_ID, 12345L, 2))
                .isInstanceOf(com.foodflow.exception.ResourceNotFoundException.class);
    }

    @Test
    void priceComesFromTheMenuEvenAfterItChanges() {
        when(menuItemRepository.findWithRestaurantById(11L)).thenReturn(Optional.of(biryani));
        cartService.addItem(USER_ID, new CartItemRequest(11L, 2));
        biryani.setPrice(new BigDecimal("300.00"));
        when(cartRepository.findWithItemsByUserId(USER_ID)).thenReturn(Optional.of(cart));

        assertThat(cartService.getCart(USER_ID).subtotal()).isEqualByComparingTo("600.00");
    }

    @Test
    void clearingResetsTheRestaurant() {
        when(menuItemRepository.findWithRestaurantById(11L)).thenReturn(Optional.of(biryani));
        cartService.addItem(USER_ID, new CartItemRequest(11L, 1));

        cartService.clear(USER_ID);

        assertThat(cart.isEmpty()).isTrue();
        assertThat(cart.getRestaurant()).isNull();
    }
}
