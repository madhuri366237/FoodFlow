package com.foodflow.service;

import com.foodflow.dto.cart.CartItemRequest;
import com.foodflow.dto.cart.CartResponse;

public interface CartService {

    CartResponse getCart(Long userId);

    CartResponse addItem(Long userId, CartItemRequest request);

    CartResponse updateQuantity(Long userId, Long cartItemId, int quantity);

    CartResponse removeItem(Long userId, Long cartItemId);

    void clear(Long userId);
}
