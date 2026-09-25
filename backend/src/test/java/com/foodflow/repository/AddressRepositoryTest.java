package com.foodflow.repository;

import com.foodflow.entity.Address;
import com.foodflow.entity.Role;
import com.foodflow.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AddressRepositoryTest extends RepositoryTestBase {

    @Autowired
    private AddressRepository addressRepository;

    private Address address(User user, String label, boolean isDefault) {
        return new Address(user, label, "12 MG Road", null, "Bengaluru", "Karnataka", "560001", isDefault);
    }

    @Test
    void secondDefaultAddressForSameUserIsRejected() {
        User user = persistUser("a@example.com", Role.CUSTOMER);
        addressRepository.saveAndFlush(address(user, "Home", true));

        assertThatThrownBy(() -> addressRepository.saveAndFlush(address(user, "Work", true)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_addresses_one_default_per_user");
    }

    @Test
    void differentUsersMayEachHaveADefaultAndManyNonDefaults() {
        User first = persistUser("first@example.com", Role.CUSTOMER);
        User second = persistUser("second@example.com", Role.CUSTOMER);

        addressRepository.saveAndFlush(address(first, "Home", true));
        addressRepository.saveAndFlush(address(first, "Work", false));
        addressRepository.saveAndFlush(address(first, "Gym", false));
        addressRepository.saveAndFlush(address(second, "Home", true));

        assertThat(addressRepository.countByUserId(first.getId())).isEqualTo(3);
    }

    @Test
    void switchingDefaultWorksWhenOldDefaultIsClearedFirst() {
        User user = persistUser("switch@example.com", Role.CUSTOMER);
        Address home = addressRepository.saveAndFlush(address(user, "Home", true));
        Address work = addressRepository.saveAndFlush(address(user, "Work", false));

        int cleared = addressRepository.clearDefaultForUser(user.getId());
        Address reloadedWork = addressRepository.findById(work.getId()).orElseThrow();
        reloadedWork.setDefaultAddress(true);
        addressRepository.flush();

        assertThat(cleared).isEqualTo(1);
        List<Address> addresses = addressRepository.findByUserIdOrderByDefaultAddressDescIdAsc(user.getId());
        assertThat(addresses).extracting(Address::getId).containsExactly(work.getId(), home.getId());
        assertThat(addresses.get(0).isDefaultAddress()).isTrue();
        assertThat(addresses.get(1).isDefaultAddress()).isFalse();
    }

    @Test
    void userCannotLoadAnotherUsersAddress() {
        User owner = persistUser("owner@example.com", Role.CUSTOMER);
        User intruder = persistUser("intruder@example.com", Role.CUSTOMER);
        Address saved = addressRepository.saveAndFlush(address(owner, "Home", true));

        assertThat(addressRepository.findByIdAndUserId(saved.getId(), owner.getId())).isPresent();
        assertThat(addressRepository.findByIdAndUserId(saved.getId(), intruder.getId())).isEmpty();
    }

    @Test
    void deletingUserCascadesToAddressesInDatabase() {
        User user = persistUser("gone@example.com", Role.CUSTOMER);
        addressRepository.saveAndFlush(address(user, "Home", true));
        flushAndClear();

        jdbc.update("delete from users where id = ?", user.getId());

        assertThat(addressRepository.countByUserId(user.getId())).isZero();
    }
}
