package com.example.demo.api.controller;

import com.example.demo.api.dto.UserDTO;
import com.example.demo.service.UserService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.redisson.api.RLock;
import org.redisson.api.RMapCache;
import org.redisson.api.map.event.EntryExpiredListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

@RestController
public class UserController {
    private final UserService userService;
    Logger logger = Logger.getLogger(getClass().getName());

    @Resource(name = "autoReverseIfNotConfirmedCache")
    private RMapCache<String, UserDTO> autoReverseIfNotConfirmedCache;

    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/user/{id}")
    public ResponseEntity<UserDTO> getUser(@PathVariable int id) {
        SecureRandom secureRandom = new SecureRandom();
        int randomNumber = secureRandom.nextInt(1000000); // Example: generate a random integer up to 1000000
        String randomString = String.valueOf(randomNumber);

        Optional<UserDTO> user = userService.getUser(id);

        autoReverseIfNotConfirmedCache.putAsync(randomString, user.get(), 15, TimeUnit.SECONDS);
        logger.log(Level.INFO, "Key for redis: {0} ", randomString);

        return user.map(userDTO -> new ResponseEntity<>(userDTO, HttpStatus.OK)).orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @PostConstruct
    public void configure() {

        logger.log(Level.WARNING, "[LACPI] Adding autoreverse listener...");

        autoReverseIfNotConfirmedCache.addListener((EntryExpiredListener<String, UserDTO>) event -> {
            RLock lock = autoReverseIfNotConfirmedCache.getLock(event.getKey());
            boolean isLocked = lock.tryLock();

            if (isLocked) {
                logger.log(Level.WARNING, "[LACPI] handling expired event..." + event.getKey());
                // Perform actions requiring exclusive access to the event
                // (e.g., update cache, send notification)

                lock.unlock();
                // implementar un try..catch para manejar excepciones no controladas
            } else {
                logger.log(Level.WARNING, "[LACPI] No se pudo obtener el bloqueo del evento " + event.getKey());
            }
        });
    }
}
