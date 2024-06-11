package com.example.demo.api.controller;

import com.example.demo.api.dto.UserDTO;
import com.example.demo.service.UserService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
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

        int variableTTL = 15;

        autoReverseIfNotConfirmedCache.fastPutAsync(randomString, user.get(), variableTTL, TimeUnit.SECONDS);
        logger.log(Level.INFO, "Key for redis: {0} ", randomString);

        return user.map(userDTO -> new ResponseEntity<>(userDTO, HttpStatus.OK))
                .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
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

    @PreDestroy
    public void hola() {
        logger.log(Level.WARNING, "[DESTROY] Eliminación cliente Redis...");
    }

    @PostConstruct
    public void Consumer1() {
        String groupName = "cancelled_trx_group";
        String consumerName = "consumer_1";

        AtomicBoolean wasCreated = new AtomicBoolean(false);

        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

        executor.scheduleAtFixedRate(() -> {
            if (!wasCreated.get()) {
                streamCancelledTrx.createGroup(StreamCreateGroupArgs
                        .name(groupName)
                        .id(StreamMessageId.ALL));
                streamCancelledTrx.createConsumer(groupName, consumerName);
                wasCreated.set(true);
                logger.log(Level.WARNING, "[CONFIG] Group and consumer created");
            }

            Map<StreamMessageId, Map<String, UserDTO>> read = streamCancelledTrx
                    .readGroup(groupName, consumerName, StreamReadGroupArgs.neverDelivered()
                            .count(100)
                            .timeout(Duration.ofSeconds(1)));

            for (Map.Entry<StreamMessageId, Map<String, UserDTO>> streamMessageIdMapEntry : read.entrySet()) {
                logger.log(Level.WARNING, "[STREAM] " + streamMessageIdMapEntry.getValue());
                streamCancelledTrx.ack(groupName, streamMessageIdMapEntry.getKey());
            }

        }, 40, 1, TimeUnit.SECONDS);
    }
    /* TAREAS
    0.- El procesamiento de los eventos debe ser asíncrono
    1.- El consumidor y el grupo se debe crear correctamente utilizando inicialización (se genera un error cuando se hace la inicialización en el constructor)
    2.- Delegar a spring boot el manejo de hilos de una tarea programada
    3.- Validar que el stream no crezca de forma incontrolada para evitar matar el cluster de producción de Redis.
    4.- Hacer que los registros procesados sean borrados cuando se envía la señal ack
    5.- Manejo de excepciones en tiempo de ejecución
    6.- La aplicación no tiene shutdown, generar un apagado elegante: https://quarkus.io/guides/lifecycle
    7.- Agregar Resilience4j para realizar reintentos cuando falle la red al intentar hacer put al SQS
    7.1.- Agregar patron @CircuitBreaker para evitar fallas en cascada, está disponible en Spring
    8.- Validar si es posible utilizar Quarkus frente a Spring para inicios de la aplicación más rápidos.
    9.- Agregar reinicio inmediato para evitar perdidas de las claves expiradas debido a que el listener está caído.
    10.- Observación cuando hay mucha demanda tener un pool de 4 hilos funciona bien, pero cuando son menos de 100 registros se demora mucho el procesamiento
    11.- Hacer Put en batch hacia SQS para minimizar costos en AWS
    12.- Las instancias de Hefesto consumen los eventos de stream de redis para hacer put a SQS.
    13.- Agregar un feature flag con AWS AppConfig para apagar la nueva funcionalidad y regresar a estado anterior de la aplicación en caso de errores.
    14.- Validar que solo haya un listener cuando el servicio se vuelva a levantar después de una falla crítica para evitar tener varios listeners registrados.
    15.- Utilizar UUID v8 o un timestamp para identificar de forma única las instancias de Hefesto
    16.- Verificar si utilizar protobuf como CODEC mejora el performance para serializar y deserializar los datos para nuestro caso de uso.
    17.- Agregar monitoreo con Datadog para tener más información sobre la causa raíz de los problemas y en consecuencia poder mitigarlos.
    18.- Utilizar CDK para crear los recursos necesarios en la nube.
    19.- Validar que recolector de basura es adecuado para nuestro caso de uso.
    20.- Validar sistemas push-base y pull-base
    21.- Agregar un README.md adecuado.
    22.- Validar el mejor recolector de basura para nuestro caso de uso.
    23.- Eliminar la conexión del cliente redis cuando la aplicación muere @PreDestroy.
    */

// LPOP core_queue_cadavi
// LRANGE core_queue_cadavi 0 -1
// XRANGE core_stream_cancelled_trx - +
// DEL core_stream_cancelled_trx
}