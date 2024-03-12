package com.example.demo.service;

import com.example.demo.api.dto.UserDTO;
import com.example.demo.persistence.entity.UserEntity;
import com.example.demo.persistence.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;

    @Autowired
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<UserDTO> getUser(Integer id) {
        Optional<UserEntity> userFromDB = userRepository.getUserEntityById(id);

        return userFromDB.map(this::userEntityMapper);
    }

    private UserDTO userEntityMapper(UserEntity userEntity) {
        return new UserDTO(userEntity.getName(), userEntity.getAge(), userEntity.getEmail());
    }
}
