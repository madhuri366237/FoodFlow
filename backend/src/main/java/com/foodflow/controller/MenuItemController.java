package com.foodflow.controller;

import com.foodflow.dto.menu.AvailabilityRequest;
import com.foodflow.dto.menu.MenuItemRequest;
import com.foodflow.dto.menu.MenuItemResponse;
import com.foodflow.security.UserPrincipal;
import com.foodflow.service.MenuItemService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Changes to an existing dish. Class-level {@code @PreAuthorize} applies to every method:
 * all of them are owner-only, and each one checks ownership in the service.
 */
@Tag(name = "Menu")
@RestController
@RequestMapping("/api/menu-items")
@RequiredArgsConstructor
@PreAuthorize("hasRole('RESTAURANT_OWNER')")
public class MenuItemController {

    private final MenuItemService menuItemService;

    @Operation(summary = "Update your own dish (owner)")
    @PutMapping("/{id}")
    public MenuItemResponse update(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id,
                                   @Valid @RequestBody MenuItemRequest request) {
        return menuItemService.update(me.getId(), id, request);
    }

    @Operation(summary = "Mark your dish available or sold out (owner)")
    @PatchMapping("/{id}/availability")
    public MenuItemResponse setAvailability(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id,
                                            @Valid @RequestBody AvailabilityRequest request) {
        return menuItemService.setAvailability(me.getId(), id, request.available());
    }

    @Operation(summary = "Delete your own dish; past orders keep their copy (owner)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id) {
        menuItemService.delete(me.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
