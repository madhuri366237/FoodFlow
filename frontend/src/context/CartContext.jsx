import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import * as cartService from '../services/cartService.js';
import { getErrorCode } from '../utils/errors.js';
import { useAuth } from './AuthContext.jsx';

const CartContext = createContext(null);

/**
 * One shared copy of the customer's cart, so the navbar badge, the menu page and the cart page
 * always agree. The server remains the source of truth: every action sends a request and then
 * replaces local state with the cart the server returns (never computed locally).
 */
export function CartProvider({ children }) {
  const { user, hasRole } = useAuth();
  const isCustomer = hasRole('CUSTOMER');
  const [cart, setCart] = useState(null);

  const refresh = useCallback(async () => {
    if (!isCustomer) {
      setCart(null);
      return;
    }
    setCart(await cartService.getCart());
  }, [isCustomer]);

  // Load when a customer logs in; forget the cart on logout or when the user changes.
  useEffect(() => {
    refresh().catch(() => setCart(null));
  }, [refresh, user?.id]);

  /**
   * Adds a dish. If the cart belongs to another restaurant, the backend answers
   * 409 CART_RESTAURANT_MISMATCH; we ask the user whether to start a new cart
   * (clear + add) instead of silently mixing or silently dropping items.
   */
  const addItem = useCallback(async (menuItemId, quantity = 1) => {
    try {
      const updated = await cartService.addItem(menuItemId, quantity);
      setCart(updated);
      return true;
    } catch (error) {
      if (getErrorCode(error) === 'CART_RESTAURANT_MISMATCH') {
        const replace = window.confirm(`${error.response.data.message}\n\nClear the cart and add this item?`);
        if (!replace) return false;
        await cartService.clearCart();
        setCart(await cartService.addItem(menuItemId, quantity));
        return true;
      }
      throw error;
    }
  }, []);

  const updateQuantity = useCallback(async (cartItemId, quantity) => {
    setCart(await cartService.updateQuantity(cartItemId, quantity));
  }, []);

  const removeItem = useCallback(async (cartItemId) => {
    setCart(await cartService.removeItem(cartItemId));
  }, []);

  const clear = useCallback(async () => {
    await cartService.clearCart();
    setCart(await cartService.getCart());
  }, []);

  const value = useMemo(
    () => ({
      cart,
      itemCount: cart?.totalQuantity ?? 0,
      refresh,
      addItem,
      updateQuantity,
      removeItem,
      clear,
    }),
    [cart, refresh, addItem, updateQuantity, removeItem, clear],
  );

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
}

export function useCart() {
  const context = useContext(CartContext);
  if (!context) {
    throw new Error('useCart must be used inside <CartProvider>');
  }
  return context;
}
