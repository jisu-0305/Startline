package org.example.gogoma.domain.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.gogoma.controller.response.UserListResponse;
import org.example.gogoma.controller.response.UserResponse;
import org.example.gogoma.domain.user.entity.User;
import org.example.gogoma.domain.user.repository.UserCustomRepository;
import org.example.gogoma.domain.user.repository.UserRepository;
import org.example.gogoma.exception.type.DbException;
import org.example.gogoma.exception.ExceptionCode;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserCustomRepository userCustomRepository;

    @Override
    public UserResponse getUserById(int id) {

        User user = userCustomRepository.findById(id)
                .orElseThrow(() -> new DbException(ExceptionCode.USER_NOT_FOUND));

        return UserResponse.of(user.getEmail(), user.getName());
    }

    @Override
    public UserListResponse getAllUsers() {

        List<User> userList = userRepository.findAll();

        return UserListResponse.of(userList);
    }

    @Override
    public void deleteUserById(int id) {
        userRepository.deleteById(id);
    }
}
