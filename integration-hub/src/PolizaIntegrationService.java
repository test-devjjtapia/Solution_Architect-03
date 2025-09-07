package com.aseguradora.es.integrationhub.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Servicio principal del Hub de Integración.
 * Orquesta las llamadas entre Kafka, las APIs REST y el Mainframe.
 */
@Service
public class PolizaIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(PolizaIntegrationService.class);

    @Autowired
    private RestTemplate mainframeRestTemplate;

    @Value("${mainframe.api.url}")
    private String mainframeApiUrl;

    /**
     * Escucha eventos de creación de pólizas desde la nueva solución SaaS.
     *
     * @param polizaEvent Evento en formato JSON que representa la nueva póliza.
     */
    @KafkaListener(topics = "saas.polizas.creadas", groupId = "integration-hub")
    public void handleNuevaPoliza(String polizaEvent) {
        log.info("Evento de nueva póliza recibido: {}", polizaEvent);

        try {
            // Aquí iría la lógica de transformación del evento al formato que espera la API del Mainframe.
            String mainframeRequest = transformarEventoAPeticionMainframe(polizaEvent);

            // Llamada a la API REST expuesta por z/OS Connect en el Mainframe.
            ResponseEntity<String> response = mainframeRestTemplate.postForEntity(mainframeApiUrl + "/polizas", mainframeRequest, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Póliza creada exitosamente en el Mainframe. Respuesta: {}", response.getBody());
            } else {
                // Lógica de error: reintentos, envío a un Dead Letter Queue (DLQ) de Kafka, etc.
                log.error("Error al crear la póliza en el Mainframe. Código: {}, Respuesta: {}", response.getStatusCode(), response.getBody());
            }
        } catch (Exception e) {
            // Captura de excepciones de red o de transformación.
            log.error("Excepción al procesar evento de nueva póliza", e);
        }
    }

    /**
     * Obtiene los detalles de una póliza desde el Mainframe.
     * El resultado de esta llamada se cachea para mejorar el rendimiento.
     *
     * @param id El identificador de la póliza.
     * @return Un objeto que representa los detalles de la póliza.
     */
    @Cacheable(value = "polizas", key = "#id")
    public String getPolizaById(String id) {
        log.info("Consultando póliza con ID: {} en el Mainframe (sin caché)", id);
        String url = mainframeApiUrl + "/polizas/" + id;
        ResponseEntity<String> response = mainframeRestTemplate.getForEntity(url, String.class);
        return response.getBody();
    }

    private String transformarEventoAPeticionMainframe(String evento) {
        // En un caso real, se usaría una librería como Jackson para mapear JSON a objetos Java
        // y construir la petición que el servicio del Mainframe espera.
        return evento; // Simulación
    }
}
