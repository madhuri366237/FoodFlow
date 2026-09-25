package com.foodflow.service.impl;

import com.foodflow.dto.cart.CartItemRequest;
import com.foodflow.dto.cart.CartItemResponse;
import com.foodflow.dto.cart.CartResponse;
import com.foodflow.entity.Cart;
import com.foodflow.entity.CartItem;
import com.foodflow.entity.MenuItem;
import com.foodflow.entity.Restaurant;
import com.foodflow.exception.BadRequestException;
import com.foodflow.exception.ConflictException;
import com.foodflow.exception.ResourceNotFoundException;
import com.foodflow.repository.CartRepository;
import com.foodflow.repository.MenuItemRepository;
import com.foodflow.service.CartService;
import com.foodflow.util.MoneyUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Cart rules:
 * <ol>
 *   <li>One restaurant per cart. Adding a dish from another restaurant is rejected with
 *       409 CART_RESTAURANT_MISMATCH; the customer must clear the cart first.</li>
 *   <li>Prices are never stored or accepted from the client; they are read from menu_items
 *       every time the cart is displayed.</li>
 *   <li>Only available dishes of active, open restaurants can be added.</li>
 *   <li>Every write locks the customer's cart row, so concurrent requests can't break rule 1.</li>
 * </ol>
 *
 * <p>Cart lines are private data: a line id that isn't in the caller's own cart is reported
 * as 404, never 403, so the response doesn't reveal that the id exists.
 */
@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final MenuItemRepository menuItemRepository;

    /** Read-only: a customer without a cart row gets an empty cart; no row is created by a GET. */
    @Override
    @Transactional(readOnly = true)
    public CartResponse getCart(Long userId) {
        return cartRepository.findWithItemsByUserId(userId)
                .map(this::toResponse)
                .orElseGet(CartResponse::empty);
    }

    /*
     * @Transactional: the lock taken in lockCart() is held until this method returns and the
     * transaction commits. Any failure (e.g. the restaurant mismatch) rolls back everything,
     * including a cart row created moments earlier.
     */
    @Override
    @Transactional
    public CartResponse addItem(Long userId, CartItemRequest request) {
        MenuItem menuItem = menuItemRepository.findWithRestaurantById(request.menuItemId())
                .orElseThrow(() -> ResourceNotFoundException.of("Menu item", request.menuItemId()));
        Restaurant restaurant = menuItem.getRestaurant();

        // A deactivated restaurant's dishes are invisible to customers: treat them as not found.
        if (!restaurant.isActive()) {
            throw ResourceNotFoundException.of("Menu item", request.menuItemId());
        }
        if (!menuItem.isAvailable()) {
            throw new ConflictException("ITEM_UNAVAILABLE", "'" + menuItem.getName() + "' is currently unavailable");
        }
        if (!restaurant.isOpen()) {
            throw new ConflictException("RESTAURANT_CLOSED", restaurant.getName() + " is not accepting orders right now");
        }

        Cart cart = lockCart(userId);

        // THE rule: one restaurant per cart. Checked while holding the lock.
        if (!cart.isEmpty() && !cart.getRestaurant().getId().equals(restaurant.getId())) {
            throw new ConflictException("CART_RESTAURANT_MISMATCH",
                    "Your cart contains items from %s. Clear the cart before adding items from %s."
                            .formatted(cart.getRestaurant().getName(), restaurant.getName()));
        }

        Optional<CartItem> existing = cart.findItemForMenuItem(menuItem.getId());
        if (existing.isPresent()) {
            // Same dish again: increase the quantity of the existing line.
            int newQuantity = existing.get().getQuantity() + request.quantity();
            requireValidQuantity(newQuantity);
            existing.get().changeQuantity(newQuantity);
        } else {
            cart.addItem(menuItem, request.quantity());
        }

        cartRepository.flush(); // assign ids to new lines before building the response
        return toResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse updateQuantity(Long userId, Long cartItemId, int quantity) {
        requireValidQuantity(quantity);
        Cart cart = cartRepository.findByUserIdForUpdate(userId).orElseThrow(() -> cartItemNotFound(cartItemId));
        CartItem item = cart.findItem(cartItemId).orElseThrow(() -> cartItemNotFound(cartItemId));
        item.changeQuantity(quantity);
        cartRepository.flush();
        return toResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse removeItem(Long userId, Long cartItemId) {
        Cart cart = cartRepository.findByUserIdForUpdate(userId).orElseThrow(() -> cartItemNotFound(cartItemId));
        CartItem item = cart.findItem(cartItemId).orElseThrow(() -> cartItemNotFound(cartItemId));
        cart.removeItem(item); // orphanRemoval issues the DELETE
        cartRepository.flush();
        return toResponse(cart);
    }

    @Override
    @Transactional
    public void clear(Long userId) {
        cartRepository.findByUserIdForUpdate(userId).ifPresent(Cart::clear);
    }

    /** Creates the cart if missing (atomic upsert), then locks it: SELECT ... FOR UPDATE. */
    private Cart lockCart(Long userId) {
        cartRepository.createIfAbsent(userId);
        return cartRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new IllegalStateException("Cart missing right after creation for user " + userId));
    }

    private static void requireValidQuantity(int quantity) {
        if (quantity < 1 || quantity > CartItem.MAX_QUANTITY) {
            throw new BadRequestException("Quantity per item must be between 1 and " + CartItem.MAX_QUANTITY);
        }
    }

    private static ResourceNotFoundException cartItemNotFound(Long cartItemId) {
        return ResourceNotFoundException.of("Cart item", cartItemId);
    }

    /*
     * Pricing happens HERE, from the MenuItem entities just read from the database:
     *   lineTotal = current menu price x quantity    subtotal = sum of available lines
     * The client never sends a price, and anything it sends is ignored.
     */
    private CartResponse toResponse(Cart cart) {
        if (cart.isEmpty()) {
            return CartResponse.empty();
        }

        List<CartItemResponse> lines = cart.getItems().stream().map(item -> {
            MenuItem menuItem = item.getMenuItem();
            return new CartItemResponse(item.getId(), menuItem.getId(), menuItem.getName(), menuItem.getPrice(),
                    item.getQuantity(), MoneyUtils.lineTotal(menuItem.getPrice(), item.getQuantity()),
                    menuItem.isAvailable());
        }).toList();

        BigDecimal subtotal = lines.stream()
                .filter(CartItemResponse::available)
                .map(CartItemResponse::lineTotal)
                .reduce(MoneyUtils.ZERO, BigDecimal::add);
        int totalQuantity = lines.stream().mapToInt(CartItemResponse::quantity).sum();

        Restaurant restaurant = cart.getRestaurant();
        boolean checkoutReady = lines.stream().allMatch(CartItemResponse::available)
                && restaurant.isActive() && restaurant.isOpen();

        return new CartResponse(restaurant.getId(), restaurant.getName(), lines, totalQuantity,
                subtotal, checkoutReady);
    }
}
