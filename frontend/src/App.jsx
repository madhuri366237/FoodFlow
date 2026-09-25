import { AuthProvider } from './context/AuthContext.jsx';
import { CartProvider } from './context/CartContext.jsx';
import AppRoutes from './routes/AppRoutes.jsx';

// Order matters: the cart needs to know who is logged in, so CartProvider sits inside AuthProvider.
export default function App() {
  return (
    <AuthProvider>
      <CartProvider>
        <AppRoutes />
      </CartProvider>
    </AuthProvider>
  );
}
