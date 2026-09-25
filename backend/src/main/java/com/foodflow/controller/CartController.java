package com.foodflow.controller;

import com.foodflow.dto.cart.CartItemRequest;
import com.foodflow.dto.cart.CartResponse;
import com.foodflow.dto.cart.UpdateCartItemRequest;
import com.foodflow.security.UserPrincipal;
import com.foodflow.service.CartService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * "/api/cart" has no id in the URL: every customer has exactly one cart, identified by the
 * token. There is no way to even ask for someone else's cart.
 */
@Tag(name = "Cart")
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
public class CartController {

    private final CartService cartService;

    @Operation(summary = "Current cart, priced from the live menu")
    @GetMapping
    public CartResponse get(@AuthenticationPrincipal UserPrincipal me) {
        return cartService.getCart(me.getId());
    }

    @Operation(summary = "Add a dish; 409 CART_RESTAURANT_MISMATCH if the cart holds another restaurant")
    @PostMapping("/items")
    public CartResponse addItem(@AuthenticationPrincipal UserPrincipal me,
                                @Valid @RequestBody CartItemRequest request) {
        return cartService.addItem(me.getId(), request);
    }

    @Operation(summary = "Set a cart line's quantity (1-20)")
    @PutMapping("/items/{id}")
    public CartResponse updateQuantity(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id,
                                       @Valid @RequestBody UpdateCartItemRequest request) {
        return cartService.updateQuantity(me.getId(), id, request.quantity());
    }

    @Operation(summary = "Remove a cart line")
    @DeleteMapping("/items/{id}")
    public CartResponse removeItem(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id) {
        return cartService.removeItem(me.getId(), id);
    }

    @Operation(summary = "Empty the cart")
    @DeleteMapping
    public ResponseEntity<Void> clear(@AuthenticationPrincipal UserPrincipal me) {
        cartService.clear(me.getId());
        return ResponseEntity.noContent().build();
    }
}
