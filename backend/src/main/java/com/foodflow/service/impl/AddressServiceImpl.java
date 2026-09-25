package com.foodflow.service.impl;

import com.foodflow.dto.address.AddressRequest;
import com.foodflow.dto.address.AddressResponse;
import com.foodflow.entity.Address;
import com.foodflow.exception.BadRequestException;
import com.foodflow.exception.ResourceNotFoundException;
import com.foodflow.repository.AddressRepository;
import com.foodflow.repository.UserRepository;
import com.foodflow.service.AddressService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Addresses are private: every lookup is scoped by user (findByIdAndUserId), so another
 * user's address id is simply "not found" (404).
 */
@Service
@RequiredArgsConstructor
public class AddressServiceImpl implements AddressService {

    private static final int MAX_ADDRESSES = 10;

    private final AddressRepository addressRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<AddressResponse> getMyAddresses(Long userId) {
        return addressRepository.findByUserIdOrderByDefaultAddressDescIdAsc(userId).stream()
                .map(AddressResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public AddressResponse create(Long userId, AddressRequest request) {
        long existing = addressRepository.countByUserId(userId);
        if (existing >= MAX_ADDRESSES) {
            throw new BadRequestException("You can save at most " + MAX_ADDRESSES + " addresses");
        }
        boolean makeDefault = existing == 0 || Boolean.TRUE.equals(request.makeDefault());
        if (makeDefault) {
            // Clear the old default FIRST: the partial unique index allows only one default per user.
            addressRepository.clearDefaultForUser(userId);
        }
        Address address = new Address(userRepository.getReferenceById(userId), request.label().trim(),
                request.line1().trim(), request.line2(), request.city().trim(), request.state().trim(),
                request.postalCode(), makeDefault);
        return AddressResponse.from(addressRepository.save(address));
    }

    @Override
    @Transactional
    public AddressResponse update(Long userId, Long addressId, AddressRequest request) {
        Address address = findOwned(userId, addressId);
        address.setLabel(request.label().trim());
        address.setLine1(request.line1().trim());
        address.setLine2(request.line2());
        address.setCity(request.city().trim());
        address.setState(request.state().trim());
        address.setPostalCode(request.postalCode());
        // Existing orders are unaffected: they keep their own copy of the address text.
        return AddressResponse.from(addressRepository.saveAndFlush(address));
    }

    @Override
    @Transactional
    public AddressResponse makeDefault(Long userId, Long addressId) {
        findOwned(userId, addressId); // 404 before touching anything
        addressRepository.clearDefaultForUser(userId); // also clears the persistence context
        Address address = findOwned(userId, addressId);
        address.setDefaultAddress(true);
        return AddressResponse.from(addressRepository.saveAndFlush(address));
    }

    @Override
    @Transactional
    public void delete(Long userId, Long addressId) {
        // Orders that used it keep their snapshot; their address_id becomes NULL (ON DELETE SET NULL).
        addressRepository.delete(findOwned(userId, addressId));
    }

    private Address findOwned(Long userId, Long addressId) {
        return addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Address", addressId));
    }
}
