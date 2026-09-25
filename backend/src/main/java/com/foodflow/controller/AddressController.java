package com.foodflow.controller;

import com.foodflow.dto.address.AddressRequest;
import com.foodflow.dto.address.AddressResponse;
import com.foodflow.security.UserPrincipal;
import com.foodflow.service.AddressService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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

import java.util.List;

/** The customer's saved delivery addresses. */
@Tag(name = "Addresses")
@RestController
@RequestMapping("/api/addresses")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
public class AddressController {

    private final AddressService addressService;

    @Operation(summary = "My saved addresses (default first)")
    @GetMapping
    public List<AddressResponse> list(@AuthenticationPrincipal UserPrincipal me) {
        return addressService.getMyAddresses(me.getId());
    }

    @Operation(summary = "Save an address (the first one becomes the default)")
    @PostMapping
    public ResponseEntity<AddressResponse> create(@AuthenticationPrincipal UserPrincipal me,
                                                  @Valid @RequestBody AddressRequest request) {
        AddressResponse created = addressService.create(me.getId(), request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(created.id()).toUri()).body(created);
    }

    @Operation(summary = "Update one of my addresses")
    @PutMapping("/{id}")
    public AddressResponse update(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id,
                                  @Valid @RequestBody AddressRequest request) {
        return addressService.update(me.getId(), id, request);
    }

    @Operation(summary = "Make one of my addresses the default")
    @PatchMapping("/{id}/default")
    public AddressResponse makeDefault(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id) {
        return addressService.makeDefault(me.getId(), id);
    }

    @Operation(summary = "Delete one of my addresses")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id) {
        addressService.delete(me.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
