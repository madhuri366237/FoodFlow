package com.foodflow.service;

import com.foodflow.dto.common.PageResponse;
import com.foodflow.dto.restaurant.RestaurantRequest;
import com.foodflow.dto.restaurant.RestaurantResponse;
import com.foodflow.dto.restaurant.RestaurantSearchCriteria;
import org.springframework.data.domain.Pageable;

public interface RestaurantService {

    /** Public search over active restaurants. */
    PageResponse<RestaurantResponse> search(RestaurantSearchCriteria criteria, Pageable pageable);

    /** Public details of an active restaurant. */
    RestaurantResponse getActiveRestaurant(Long id);

    /** The caller's own restaurants, including inactive ones. */
    PageResponse<RestaurantResponse> getOwnerRestaurants(Long ownerId, Pageable pageable);

    RestaurantResponse create(Long ownerId, RestaurantRequest request);

    RestaurantResponse update(Long ownerId, Long restaurantId, RestaurantRequest request);

    RestaurantResponse setOpen(Long ownerId, Long restaurantId, boolean open);

    void delete(Long ownerId, Long restaurantId);
}
