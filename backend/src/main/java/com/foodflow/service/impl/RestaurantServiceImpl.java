package com.foodflow.service.impl;

import com.foodflow.dto.common.PageResponse;
import com.foodflow.dto.restaurant.RestaurantRequest;
import com.foodflow.dto.restaurant.RestaurantResponse;
import com.foodflow.dto.restaurant.RestaurantSearchCriteria;
import com.foodflow.entity.Restaurant;
import com.foodflow.entity.User;
import com.foodflow.exception.BadRequestException;
import com.foodflow.exception.ConflictException;
import com.foodflow.exception.ResourceNotFoundException;
import com.foodflow.repository.OrderRepository;
import com.foodflow.repository.RestaurantRepository;
import com.foodflow.repository.RestaurantSpecifications;
import com.foodflow.repository.UserRepository;
import com.foodflow.service.RestaurantService;
import com.foodflow.util.PageableUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RestaurantServiceImpl implements RestaurantService {

    private static final Set<String> SORTABLE = Set.of("name", "rating", "createdAt");

    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final RestaurantAccess restaurantAccess;

    /*
     * All filtering, sorting and paging happens in PostgreSQL: only one page of rows
     * (10 by default, 50 max) ever leaves the database, however many restaurants exist.
     * Spring Data runs two queries: SELECT ... LIMIT/OFFSET for the page, and
     * SELECT count(*) with the same WHERE for totalElements/totalPages.
     */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<RestaurantResponse> search(RestaurantSearchCriteria criteria, Pageable pageable) {
        if (criteria.minPrice() != null && criteria.maxPrice() != null
                && criteria.minPrice().compareTo(criteria.maxPrice()) > 0) {
            throw new BadRequestException("minPrice must not be greater than maxPrice");
        }

        List<Specification<Restaurant>> filters = new ArrayList<>();
        filters.add(RestaurantSpecifications.isActive());
        if (criteria.hasKeyword()) {
            filters.add(RestaurantSpecifications.matchesKeyword(criteria.keyword()));
        }
        if (criteria.minRating() != null) {
            filters.add(RestaurantSpecifications.ratingAtLeast(criteria.minRating()));
        }
        if (criteria.open() != null) {
            filters.add(RestaurantSpecifications.isOpen(criteria.open()));
        }
        if (criteria.hasDishFilter()) {
            filters.add(RestaurantSpecifications.servesDish(
                    criteria.categoryId(), criteria.minPrice(), criteria.maxPrice()));
        }

        return PageResponse.from(restaurantRepository
                .findAll(Specification.allOf(filters), PageableUtils.sanitize(pageable, SORTABLE))
                .map(RestaurantResponse::from));
    }

    @Override
    @Transactional(readOnly = true)
    public RestaurantResponse getActiveRestaurant(Long id) {
        return restaurantRepository.findByIdAndActiveTrue(id)
                .map(RestaurantResponse::from)
                .orElseThrow(() -> ResourceNotFoundException.of("Restaurant", id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RestaurantResponse> getOwnerRestaurants(Long ownerId, Pageable pageable) {
        return PageResponse.from(restaurantRepository
                .findByOwnerId(ownerId, PageableUtils.sanitize(pageable, SORTABLE))
                .map(RestaurantResponse::from));
    }

    @Override
    @Transactional
    public RestaurantResponse create(Long ownerId, RestaurantRequest request) {
        // getReferenceById returns a proxy without a SELECT: we only need the FK value.
        User owner = userRepository.getReferenceById(ownerId);
        Restaurant restaurant = new Restaurant(owner, request.name().trim(), request.description(),
                request.address().trim(), request.phone(), request.imageUrl());
        return RestaurantResponse.from(restaurantRepository.save(restaurant));
    }

    /*
     * The loaded entity is "managed": Hibernate's dirty checking would issue the UPDATE at
     * commit even without a save() call. saveAndFlush forces the UPDATE now, so (1) a
     * @Version conflict surfaces inside this method as a 409, and (2) the response carries the
     * new updatedAt value set by @UpdateTimestamp.
     */
    @Override
    @Transactional
    public RestaurantResponse update(Long ownerId, Long restaurantId, RestaurantRequest request) {
        Restaurant restaurant = restaurantAccess.requireOwnedRestaurant(restaurantId, ownerId);
        restaurant.setName(request.name().trim());
        restaurant.setDescription(request.description());
        restaurant.setAddress(request.address().trim());
        restaurant.setPhone(request.phone());
        restaurant.setImageUrl(request.imageUrl());
        return RestaurantResponse.from(restaurantRepository.saveAndFlush(restaurant));
    }

    @Override
    @Transactional
    public RestaurantResponse setOpen(Long ownerId, Long restaurantId, boolean open) {
        Restaurant restaurant = restaurantAccess.requireOwnedRestaurant(restaurantId, ownerId);
        restaurant.setOpen(open);
        return RestaurantResponse.from(restaurantRepository.saveAndFlush(restaurant));
    }

    /*
     * Hard delete; the menu goes with it (ON DELETE CASCADE). A restaurant that already has
     * orders cannot be deleted: its orders are financial history and reference it with RESTRICT.
     * We check first for a clear message; the foreign key is the final guard.
     */
    @Override
    @Transactional
    public void delete(Long ownerId, Long restaurantId) {
        Restaurant restaurant = restaurantAccess.requireOwnedRestaurant(restaurantId, ownerId);
        if (orderRepository.existsByRestaurantId(restaurantId)) {
            throw new ConflictException("RESTAURANT_HAS_ORDERS",
                    "A restaurant with orders cannot be deleted. Close it instead.");
        }
        restaurantRepository.delete(restaurant);
        restaurantRepository.flush();
    }
}
