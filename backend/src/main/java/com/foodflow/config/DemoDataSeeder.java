package com.foodflow.config;

import com.foodflow.dto.address.AddressRequest;
import com.foodflow.dto.cart.CartItemRequest;
import com.foodflow.dto.menu.MenuItemRequest;
import com.foodflow.dto.order.CreateOrderRequest;
import com.foodflow.dto.payment.PaymentRequest;
import com.foodflow.dto.restaurant.RestaurantRequest;
import com.foodflow.dto.review.ReviewRequest;
import com.foodflow.entity.Coupon;
import com.foodflow.entity.DiscountType;
import com.foodflow.entity.OrderStatus;
import com.foodflow.entity.PaymentMethod;
import com.foodflow.entity.Role;
import com.foodflow.entity.User;
import com.foodflow.repository.CategoryRepository;
import com.foodflow.repository.CouponRepository;
import com.foodflow.repository.UserRepository;
import com.foodflow.security.UserPrincipal;
import com.foodflow.service.AddressService;
import com.foodflow.service.CartService;
import com.foodflow.service.MenuItemService;
import com.foodflow.service.OrderService;
import com.foodflow.service.PaymentService;
import com.foodflow.service.RestaurantService;
import com.foodflow.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DEVELOPMENT / DEMO DATA ONLY. Enabled with SEED_DEMO_DATA=true (docker-compose sets it);
 * never enable in production.
 *
 * <p>All demo accounts use the password {@value #DEMO_PASSWORD} and an @demo.foodflow.dev
 * address. No real people: the names are invented.
 *
 * <p>Orders, payments and reviews are created THROUGH THE REAL SERVICES (cart -> checkout ->
 * payment -> state machine -> review), not with raw INSERTs. The demo data therefore obeys every
 * business rule (price snapshots, coupon limits, rating averages), and seeding doubles as a smoke
 * test of the whole flow.
 *
 * <p>Idempotent: does nothing if the demo admin already exists, so restarts don't duplicate data.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed-demo-data", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    static final String DEMO_PASSWORD = "Demo@1234";
    private static final String DOMAIN = "@demo.foodflow.dev";

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final CouponRepository couponRepository;
    private final PasswordEncoder passwordEncoder;
    private final RestaurantService restaurantService;
    private final MenuItemService menuItemService;
    private final AddressService addressService;
    private final CartService cartService;
    private final OrderService orderService;
    private final PaymentService paymentService;
    private final ReviewService reviewService;
    private final JdbcTemplate jdbc;

    private record Restaurant(Long id, User owner, Map<String, Long> dishes) {
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmail("admin" + DOMAIN)) {
            log.info("Demo data already present; skipping seeding");
            return;
        }
        log.warn("Seeding DEMO data (development only). All demo accounts use password {}", DEMO_PASSWORD);

        user("Demo Admin", "admin", Role.ADMIN);
        User owner1 = user("Rohan Mehta", "owner1", Role.RESTAURANT_OWNER);
        User owner2 = user("Kavya Iyer", "owner2", Role.RESTAURANT_OWNER);
        List<User> customers = List.of(
                user("Aarav Sharma", "customer1", Role.CUSTOMER),
                user("Diya Patel", "customer2", Role.CUSTOMER),
                user("Kabir Singh", "customer3", Role.CUSTOMER),
                user("Meera Nair", "customer4", Role.CUSTOMER),
                user("Ishaan Gupta", "customer5", Role.CUSTOMER));
        List<Long> addresses = new ArrayList<>();
        String[][] places = {{"14 Residency Road", "560025"}, {"22 Indiranagar 100ft Road", "560038"},
                {"7 Koramangala 5th Block", "560095"}, {"101 Whitefield Main Road", "560066"}, {"3 Jayanagar 4th Block", "560011"}};
        for (int i = 0; i < customers.size(); i++) {
            addresses.add(addressService.create(customers.get(i).getId(), new AddressRequest("Home", places[i][0], null,
                    "Bengaluru", "Karnataka", places[i][1], true)).id());
        }

        Restaurant biryani = restaurant(owner1, "Biryani House", "Slow-cooked Hyderabadi and Lucknowi biryanis",
                "12 MG Road, Bengaluru", "9800000001", new Object[][]{
                        {"Biryani", "Chicken Dum Biryani", "Hyderabadi style, with raita", "279"},
                        {"Biryani", "Mutton Biryani", "Tender mutton, saffron rice", "349"},
                        {"Biryani", "Veg Biryani", "Seasonal vegetables and paneer", "219"},
                        {"Starters", "Chicken 65", "Spicy fried chicken", "199"},
                        {"Desserts", "Double Ka Meetha", "Bread pudding with dry fruits", "99"}});
        Restaurant pizza = restaurant(owner1, "Pizza Planet", "Wood-fired pizzas and garlic bread",
                "5 Brigade Road, Bengaluru", "9800000002", new Object[][]{
                        {"Pizza", "Margherita", "Tomato, mozzarella, basil", "249"},
                        {"Pizza", "Farmhouse", "Onion, capsicum, mushroom, corn", "329"},
                        {"Pizza", "Paneer Tikka Pizza", "Tandoori paneer and onion", "359"},
                        {"Starters", "Garlic Bread", "With cheese dip", "129"},
                        {"Beverages", "Cold Coffee", "Thick and creamy", "119"}});
        Restaurant burgers = restaurant(owner1, "Burger Barn", "Smash burgers and loaded fries",
                "40 Church Street, Bengaluru", "9800000003", new Object[][]{
                        {"Burgers", "Classic Veg Burger", "Crispy patty, lettuce, mayo", "149"},
                        {"Burgers", "Chicken Smash Burger", "Double patty, cheddar", "229"},
                        {"Starters", "Peri Peri Fries", "", "119"},
                        {"Beverages", "Chocolate Shake", "", "139"}});
        Restaurant dosa = restaurant(owner2, "Dosa Darbar", "Crispy dosas and filter coffee",
                "9 Jayanagar 9th Block, Bengaluru", "9800000004", new Object[][]{
                        {"South Indian", "Masala Dosa", "With sambar and chutneys", "99"},
                        {"South Indian", "Ghee Roast Dosa", "Mangalorean style", "129"},
                        {"South Indian", "Idli Vada Combo", "2 idlis, 1 vada", "89"},
                        {"Beverages", "Filter Coffee", "", "39"}});
        Restaurant wok = restaurant(owner2, "Wok Express", "Indo-Chinese favourites",
                "77 Koramangala 80ft Road, Bengaluru", "9800000005", new Object[][]{
                        {"Chinese", "Veg Hakka Noodles", "", "169"},
                        {"Chinese", "Chilli Chicken", "Dry, with spring onion", "219"},
                        {"Chinese", "Veg Manchurian", "Gravy", "179"},
                        {"Rolls", "Chicken Spring Roll", "", "129"}});

        coupon("WELCOME50", "50% off your order, up to Rs 100", DiscountType.PERCENTAGE, "50", "199", "100", 500, 90);
        coupon("FLAT100", "Flat Rs 100 off orders above Rs 499", DiscountType.FIXED_AMOUNT, "100", "499", null, 200, 60);
        coupon("SAVE10", "10% off, up to Rs 75", DiscountType.PERCENTAGE, "10", "0", "75", null, 120);

        // {customer, restaurant, dishes (name x qty), payment, coupon, final status, rating, days ago}
        record Plan(int customer, Restaurant restaurant, Object[] lines, PaymentMethod method, String coupon,
                    OrderStatus status, Integer rating, String review, int daysAgo) {
        }
        List<Plan> plans = List.of(
                new Plan(0, biryani, new Object[]{"Chicken Dum Biryani", 2, "Chicken 65", 1}, PaymentMethod.UPI, "WELCOME50", OrderStatus.DELIVERED, 5, "Best biryani in town!", 6),
                new Plan(1, biryani, new Object[]{"Mutton Biryani", 1, "Double Ka Meetha", 2}, PaymentMethod.CARD, null, OrderStatus.DELIVERED, 4, "Great taste, slightly late.", 5),
                new Plan(2, pizza, new Object[]{"Farmhouse", 1, "Garlic Bread", 1}, PaymentMethod.CASH_ON_DELIVERY, null, OrderStatus.DELIVERED, 4, "Crust was perfect.", 5),
                new Plan(3, dosa, new Object[]{"Masala Dosa", 2, "Filter Coffee", 2}, PaymentMethod.UPI, null, OrderStatus.DELIVERED, 5, "Just like home.", 4),
                // 2 x 219 + 169 = 607: meets FLAT100's 499 minimum.
                new Plan(4, wok, new Object[]{"Chilli Chicken", 2, "Veg Hakka Noodles", 1}, PaymentMethod.CARD, "FLAT100", OrderStatus.DELIVERED, 3, "Good, but too oily.", 3),
                new Plan(0, burgers, new Object[]{"Chicken Smash Burger", 2, "Peri Peri Fries", 1}, PaymentMethod.CASH_ON_DELIVERY, null, OrderStatus.DELIVERED, 4, null, 2),
                new Plan(1, pizza, new Object[]{"Paneer Tikka Pizza", 1}, PaymentMethod.UPI, "SAVE10", OrderStatus.CANCELLED, null, null, 2),
                new Plan(2, biryani, new Object[]{"Veg Biryani", 2}, PaymentMethod.CARD, null, OrderStatus.OUT_FOR_DELIVERY, null, null, 0),
                new Plan(3, wok, new Object[]{"Veg Manchurian", 1, "Chicken Spring Roll", 2}, PaymentMethod.CASH_ON_DELIVERY, null, OrderStatus.PREPARING, null, null, 0),
                new Plan(4, dosa, new Object[]{"Ghee Roast Dosa", 1, "Idli Vada Combo", 1}, PaymentMethod.UPI, null, OrderStatus.PLACED, null, null, 0),
                new Plan(0, pizza, new Object[]{"Margherita", 2}, PaymentMethod.CASH_ON_DELIVERY, null, OrderStatus.CONFIRMED, null, null, 0));

        OrderStatus[] path = {OrderStatus.CONFIRMED, OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP,
                OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED};
        for (Plan plan : plans) {
            User customer = customers.get(plan.customer());
            for (int i = 0; i < plan.lines().length; i += 2) {
                cartService.addItem(customer.getId(), new CartItemRequest(
                        plan.restaurant().dishes().get((String) plan.lines()[i]), (Integer) plan.lines()[i + 1]));
            }
            Long orderId = orderService.placeOrder(customer.getId(),
                    new CreateOrderRequest(addresses.get(plan.customer()), plan.method(), plan.coupon())).id();
            if (plan.method().isOnline() && plan.status() != OrderStatus.PLACED) {
                paymentService.pay(customer.getId(), new PaymentRequest(orderId, "tok_visa"));
            }
            if (plan.status() == OrderStatus.CANCELLED) {
                orderService.cancelByCustomer(orderId, customer.getId(), "Ordered by mistake");
            } else {
                UserPrincipal owner = UserPrincipal.from(plan.restaurant().owner());
                for (OrderStatus next : path) {
                    if (orderService.getOrder(orderId, owner).status() == plan.status()) break;
                    orderService.changeStatus(orderId, owner, next, null);
                }
            }
            if (plan.rating() != null) {
                reviewService.create(customer.getId(), plan.restaurant().id(),
                        new ReviewRequest(orderId, plan.rating(), plan.review()));
            }
            // Spread orders over the past week so the dashboards' 7-day charts have shape.
            if (plan.daysAgo() > 0) {
                jdbc.update("update orders set created_at = created_at - make_interval(days => ?) where id = ?",
                        plan.daysAgo(), orderId);
            }
        }
        log.warn("Demo data seeded: 1 admin, 2 owners, 5 customers, 5 restaurants, 22 dishes, 3 coupons, {} orders",
                plans.size());
    }

    private User user(String name, String login, Role role) {
        return userRepository.save(new User(name, login + DOMAIN, passwordEncoder.encode(DEMO_PASSWORD), "9876500000", role));
    }

    private Restaurant restaurant(User owner, String name, String description, String address, String phone,
                                  Object[][] menu) {
        Long id = restaurantService.create(owner.getId(),
                new RestaurantRequest(name, description, address, phone, null)).id();
        Map<String, Long> dishes = new LinkedHashMap<>();
        for (Object[] dish : menu) {
            Long categoryId = categoryRepository.findByNameIgnoreCase((String) dish[0]).orElseThrow().getId();
            dishes.put((String) dish[1], menuItemService.create(owner.getId(), id, new MenuItemRequest(categoryId,
                    (String) dish[1], ((String) dish[2]).isBlank() ? null : (String) dish[2],
                    new BigDecimal((String) dish[3]), null, true)).id());
        }
        return new Restaurant(id, owner, dishes);
    }

    private void coupon(String code, String description, DiscountType type, String value, String minimum,
                        String maximum, Integer usageLimit, int validDays) {
        Coupon coupon = new Coupon(code, type, new BigDecimal(value), Instant.now().plus(Duration.ofDays(validDays)));
        coupon.setDescription(description);
        coupon.setMinimumOrderAmount(new BigDecimal(minimum));
        coupon.setMaximumDiscount(maximum == null ? null : new BigDecimal(maximum));
        coupon.setUsageLimit(usageLimit);
        if (!couponRepository.existsByCode(code)) {
            couponRepository.save(coupon);
        }
    }
}
