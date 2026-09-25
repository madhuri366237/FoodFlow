package com.foodflow.dto.address;

import com.foodflow.entity.Address;

public record AddressResponse(
        Long id,
        String label,
        String line1,
        String line2,
        String city,
        String state,
        String postalCode,
        boolean isDefault) {

    public static AddressResponse from(Address address) {
        return new AddressResponse(address.getId(), address.getLabel(), address.getLine1(), address.getLine2(),
                address.getCity(), address.getState(), address.getPostalCode(), address.isDefaultAddress());
    }
}
