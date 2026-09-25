package com.foodflow.controller;

import org.springdoc.core.annotations.ParameterObject;
import com.foodflow.dto.common.PageResponse;
import com.foodflow.dto.menu.MenuItemRequest;
import com.foodflow.dto.menu.MenuItemResponse;
import com.foodflow.dto.restaurant.OpenStatusRequest;
import com.foodflow.dto.restaurant.RestaurantRequest;
import com.foodflow.dto.restaurant.RestaurantResponse;
import com.foodflow.dto.restaurant.RestaurantSearchCriteria;
import com.foodflow.security.UserPrincipal;
import com.foodflow.service.MenuItemService;
import com.foodflow.service.RestaurantService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * Authorization happens in three layers:
 * <ol>
 *   <li>URL (SecurityConfig): GET /api/restaurants/** is public; any other method needs a valid token.</li>
 *   <li>Role ({@code @PreAuthorize}): only RESTAURANT_OWNER may write. A CUSTOMER gets 403.</li>
 *   <li>Ownership (service, RestaurantAccess): an owner may write only to THEIR restaurant.
 *       Another owner gets 403.</li>
 * </ol>
 * The owner id always comes from the verified token ({@code @AuthenticationPrincipal}),
 * never from the request body or URL, so it can't be spoofed.
 */
@Tag(name = "Restaurants")
@RestController
@RequestMapping("/api/restaurants")
@RequiredArgsConstructor
public class RestaurantController {

    private final RestaurantService restaurantService;
    private final MenuItemService menuItemService;

    /**
     * GET /api/restaurants?keyword=biryani&amp;minRating=4&amp;open=true&amp;page=0&amp;size=10&amp;sort=rating,desc
     * Query parameters bind to RestaurantSearchCriteria (filters) and Pageable (page/size/sort).
     */
    @Operation(summary = "Search active restaurants by keyword, category, rating, price and open status (paged, sortable)")
    @SecurityRequirements()
    @GetMapping
    public PageResponse<RestaurantResponse> search(
            @Valid @ParameterObject RestaurantSearchCriteria criteria,
            @ParameterObject @PageableDefault(size = 10, sort = "rating", direction = Sort.Direction.DESC) Pageable pageable) {
        return restaurantService.search(criteria, pageable);
    }

    @Operation(summary = "Public details of an active restaurant")
    @SecurityRequirements()
    @GetMapping("/{id}")
    public RestaurantResponse get(@PathVariable Long id) {
        return restaurantService.getActiveRestaurant(id);
    }

    /** Public menu: available dishes only. */
    @Operation(summary = "Available dishes of a restaurant")
    @SecurityRequirements()
    @GetMapping("/{id}/menu")
    public List<MenuItemResponse> menu(@PathVariable Long id) {
        return menuItemService.getPublicMenu(id);
    }

    /** 201 Created + Location header pointing at the new resource (REST convention). */
    @Operation(summary = "Create a restaurant (owner)")
    @PostMapping
    @PreAuthorize("hasRole('RESTAURANT_OWNER')")
    public ResponseEntity<RestaurantResponse> create(@AuthenticationPrincipal UserPrincipal me,
                                                     @Valid @RequestBody RestaurantRequest request) {
        RestaurantResponse created = restaurantService.create(me.getId(), request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    /** PUT = full replacement of the editable fields. */
    @Operation(summary = "Update your own restaurant (owner)")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('RESTAURANT_OWNER')")
    public RestaurantResponse update(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id,
                                     @Valid @RequestBody RestaurantRequest request) {
        return restaurantService.update(me.getId(), id, request);
    }

    /** PATCH = change one field without resending the whole restaurant. */
    @Operation(summary = "Open or close your restaurant for orders (owner)")
    @PatchMapping("/{id}/open-status")
    @PreAuthorize("hasRole('RESTAURANT_OWNER')")
    public RestaurantResponse setOpen(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id,
                                      @Valid @RequestBody OpenStatusRequest request) {
        return restaurantService.setOpen(me.getId(), id, request.open());
    }

    /** 204 No Content: success, nothing to return. */
    @Operation(summary = "Delete your own restaurant, if it has no orders (owner)")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('RESTAURANT_OWNER')")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id) {
        restaurantService.delete(me.getId(), id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Dishes are created under their restaurant: POST /api/restaurants/{id}/menu-items.
     * The URL states which restaurant the dish belongs to, so the body doesn't need a
     * restaurantId that could contradict it.
     */
    @Operation(summary = "Add a dish to your own restaurant (owner)")
    @PostMapping("/{id}/menu-items")
    @PreAuthorize("hasRole('RESTAURANT_OWNER')")
    public ResponseEntity<MenuItemResponse> createMenuItem(@AuthenticationPrincipal UserPrincipal me,
                                                           @PathVariable Long id,
                                                           @Valid @RequestBody MenuItemRequest request) {
        MenuItemResponse created = menuItemService.create(me.getId(), id, request);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath().path("/api/menu-items/{id}")
                .buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }
}
