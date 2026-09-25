package com.foodflow.service.impl;

import com.foodflow.dto.user.UserResponse;
import com.foodflow.exception.ResourceNotFoundException;
import com.foodflow.repository.UserRepository;
import com.foodflow.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    /*
     * readOnly = true: tells Hibernate no changes will be written, so it skips dirty-checking
     * at the end of the transaction, and documents the method's intent.
     */
    @Override
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(Long userId) {
        return userRepository.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> ResourceNotFoundException.of("User", userId));
    }
}
