package com.example.demo.api.controller;

import com.example.demo.api.dto.UserDTO;
import com.example.demo.service.UserService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.redisson.api.RMapCache;
import org.redisson.api.RStream;
import org.redisson.api.StreamMessageId;
import org.redisson.api.map.event.EntryExpiredListener;
import org.redisson.api.stream.StreamAddArgs;
import org.redisson.api.stream.StreamCreateGroupArgs;
import org.redisson.api.stream.StreamReadGroupArgs;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

@RestController
public class UserController {
    private final UserService userService;
    Logger logger = Logger.getLogger(getClass().getName());

    @Resource(name = "autoReverseIfNotConfirmedCache")
    private RMapCache<String, UserDTO> autoReverseIfNotConfirmedCache;

    @Resource(name = "streamCancelledTrx")
    private RStream<String, UserDTO> streamCancelledTrx;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/user/{id}")
    public ResponseEntity<UserDTO> getUser(@PathVariable int id) {
        SecureRandom secureRandom = new SecureRandom();
        int randomNumber = secureRandom.nextInt(1000000); // Example: generate a random integer up to 1000000
        String randomString = String.valueOf(randomNumber);

        Optional<UserDTO> user = userService.getUser(id);

        int variableTTL = 20;

        autoReverseIfNotConfirmedCache.fastPutAsync(randomString, user.get(), variableTTL, TimeUnit.SECONDS);
        logger.log(Level.INFO, "Key for redis: {0} ", randomString);

        return user.map(userDTO -> new ResponseEntity<>(userDTO, HttpStatus.OK)).orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @PostConstruct
    public void Productor() {
        autoReverseIfNotConfirmedCache.addListenerAsync((EntryExpiredListener<String, UserDTO>) event -> {
            try {
                StreamAddArgs<String, UserDTO> entry = StreamAddArgs.entry(event.getKey(), event.getValue());
                streamCancelledTrx.addAsync(entry);
                logger.log(Level.WARNING, "[PUT] stream_cancelled_trx " + event.getKey());

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @PostConstruct
    public void Consumer() {
        String groupName = "cancelled-trx-group";
        String consumerName = "consumer-dracardys";

        AtomicBoolean wasCreated = new AtomicBoolean(false);

        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

        executor.scheduleAtFixedRate(() -> {
            if (!wasCreated.get()) {
                streamCancelledTrx.createGroup(StreamCreateGroupArgs.name(groupName).id(StreamMessageId.ALL));
                streamCancelledTrx.createConsumer(groupName, consumerName);
                wasCreated.set(true);
                logger.log(Level.WARNING, "[CONFIG] Creado grupo y consumidor, una vez");
            }

            Map<StreamMessageId, Map<String, UserDTO>> read = streamCancelledTrx.readGroup(groupName, consumerName, StreamReadGroupArgs.neverDelivered()
                    .count(20)
                    .timeout(Duration.ofSeconds(5)));

            for (Map.Entry<StreamMessageId, Map<String, UserDTO>> streamMessageIdMapEntry : read.entrySet()) {
                logger.log(Level.WARNING, "[STREAM] " + streamMessageIdMapEntry.getValue());

            }
        }, 40, 1, TimeUnit.SECONDS);
    }

// LPOP core_queue_cadavi
// LRANGE core_queue_cadavi 0 -1
// XRANGE core_stream_cancelled_trx - +
// DEL core_stream_cancelled_trx
}