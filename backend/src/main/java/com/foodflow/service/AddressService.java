package com.foodflow.service;

import com.foodflow.dto.address.AddressRequest;
import com.foodflow.dto.address.AddressResponse;

import java.util.List;

public interface AddressService {

    List<AddressResponse> getMyAddresses(Long userId);

    AddressResponse create(Long userId, AddressRequest request);

    AddressResponse update(Long userId, Long addressId, AddressRequest request);

    AddressResponse makeDefault(Long userId, Long addressId);

    void delete(Long userId, Long addressId);
}
