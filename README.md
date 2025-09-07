# Propuesta de Arquitectura de Integración: SaaS con Mainframe IBM

**Cliente:** Aseguradora Española  
**Fecha de Presentación:** Q4 2022  
**Autor:** Javier J. Tapia

---

## 1. Resumen Ejecutivo

Esta propuesta diseña una arquitectura robusta, escalable y segura para integrar una moderna solución SaaS con el sistema *core* de la aseguradora, un Mainframe IBM z/OS. El principal desafío es conectar la "Nueva Arquitectura Magenta" (basada en eventos de Kafka y APIs REST) con un entorno transaccional legacy (COBOL, CICS, Db2) de forma eficiente y con bajo riesgo.

La solución se basa en la creación de un **Hub de Integración** que actúa como una capa de desacoplamiento (*Anti-Corruption Layer*). Este Hub orquesta la comunicación, transformando los eventos y peticiones del mundo moderno en transacciones que el Mainframe entiende, y viceversa. Este enfoque sigue el patrón **Strangler Fig**, permitiendo a la aseguradora modernizar progresivamente su *core* sin interrumpir las operaciones críticas de negocio.

**Tecnologías Clave Propuestas:**
- **Hub de Integración:** Microservicios en **Java 17 con Spring Boot**.
- **Conectividad Mainframe:** **IBM z/OS Connect EE** para exponer la lógica COBOL/CICS como APIs RESTful.
- **Plataforma de Eventos:** **Apache Kafka** (ya presente en la arquitectura Magenta).
- **API Gateway:** **Spring Cloud Gateway** para gestionar la seguridad, el enrutamiento y las métricas de las APIs.
- **Observabilidad:** Stack de **Prometheus, Grafana, Jaeger y ELK** (Elasticsearch, Logstash, Kibana).

---

## 2. Justificación de la Arquitectura

La elección de esta arquitectura se fundamenta en cuatro pilares estratégicos:

1.  **Bajo Riesgo y Continuidad del Negocio:** El Hub de Integración aísla el Mainframe. Cualquier cambio o fallo en la nueva solución SaaS o en los componentes de la arquitectura Magenta no impacta directamente en el sistema *core*. Las operaciones de seguros (pólizas, siniestros) continúan funcionando sin interrupción.

2.  **Desacoplamiento y Agilidad:** Al no conectar directamente el SaaS al Mainframe, evitamos el acoplamiento tecnológico y de datos. El Hub de Integración expone APIs limpias y modernas al resto de la organización, ocultando la complejidad del Mainframe. Esto permite que los equipos de desarrollo modernos trabajen en paralelo y con mayor velocidad.

3.  **Habilitador de la Modernización (Patrón Strangler Fig):** El Hub es el punto de control para redirigir el tráfico. Inicialmente, una petición para "consultar póliza" se dirigirá al Mainframe. En el futuro, cuando esa lógica se migre a un nuevo microservicio, solo se necesitará cambiar la regla de enrutamiento en el Hub. El resto de la organización no se ve afectada. Esto permite "estrangular" el Mainframe función por función.

4.  **Gobernanza y Seguridad Centralizada:** El API Gateway y el Hub de Integración son puntos de control perfectos para implementar políticas de seguridad (autenticación/autorización con OAuth2/JWT), control de acceso, *rate limiting* y para centralizar la observabilidad de todo el flujo de integración.

---

## 3. Componentes de la Arquitectura

![Diagrama de Arquitectura](documentacion/diagrama.puml)
*(Ver el diagrama en la carpeta `documentacion` para una representación visual)*

- **Mainframe (IBM z/OS):** Es el *System of Record* (SoR) para los datos críticos del negocio (pólizas, clientes, siniestros). La lógica de negocio reside en programas COBOL que se ejecutan bajo CICS.
- **Capa de Conectividad Mainframe (z/OS Connect EE):** Es la pieza clave en el lado del Mainframe. Permite a los desarrolladores de Mainframe, sin ser expertos en APIs, exponer programas CICS/COBOL como APIs RESTful seguras. Estas APIs serán consumidas *únicamente* por el Hub de Integración.
- **Hub de Integración (Spring Boot):** Es el corazón de la nueva arquitectura.
    - Escucha eventos de negocio en tópicos de Kafka (ej. `saas.cliente.creado`).
    - Transforma esos eventos en llamadas a las APIs REST expuestas por z/OS Connect.
    - Proporciona APIs REST a la arquitectura Magenta para consultas síncronas (ej. `GET /api/polizas/{id}`).
    - Implementa la lógica de reintentos, compensación (patrón Saga) y orquestación.
- **Plataforma de Eventos (Kafka):** Se utiliza para la comunicación asíncrona y basada en eventos. Desacopla temporalmente los sistemas. Si el Mainframe está en una ventana de mantenimiento, los eventos se encolan en Kafka y se procesarán cuando vuelva a estar disponible.
- **API Gateway (Spring Cloud Gateway):** Es la puerta de entrada única para todas las peticiones. Centraliza el enrutamiento hacia el Hub de Integración o hacia otros microservicios de la arquitectura Magenta.
- **Nueva Solución SaaS:** Interactúa con la aseguradora a través del API Gateway (para acciones síncronas) y publicando eventos en Kafka (para notificaciones asíncronas).

---

## 4. Respuestas a los Desafíos del Ejercicio

#### 4.1. ¿Cómo asegurar que las APIs (REST & SOAP) cumplan los NFRs (disponibilidad, rendimiento, seguridad)?

- **Seguridad:**
    - **Autenticación y Autorización:** Se implementará **OAuth 2.0** (con JWTs) en el API Gateway. Solo las aplicaciones cliente autorizadas (como el SaaS) podrán consumir las APIs. El Gateway validará el token en cada petición.
    - **Seguridad a Nivel de Red:** La comunicación entre el Hub y z/OS Connect se realizará en una red privada y segura (VPC/subred interna). Se usará mTLS (TLS mutuo) para asegurar que solo el Hub pueda llamar a las APIs del Mainframe.
    - **Protección contra Ataques:** El API Gateway proporcionará protección contra ataques comunes (inyección SQL, XSS) y aplicará *rate limiting* para prevenir abusos.

- **Disponibilidad:**
    - El Hub de Integración se desplegará en un clúster de **Kubernetes (o Red Hat OpenShift)** con múltiples réplicas (mínimo 3) para garantizar alta disponibilidad.
    - Kafka, por su naturaleza distribuida, ya proporciona alta disponibilidad para la comunicación por eventos.
    - Se usarán *Health Checks* en el Hub para que Kubernetes pueda reiniciar automáticamente las instancias que no respondan.

- **Rendimiento:**
    - **Caché:** Para datos que no cambian frecuentemente (ej. tipos de pólizas), el Hub de Integración implementará una caché (con **Redis** o **Caffeine**) para evitar llamadas innecesarias al Mainframe.
    - **Asincronía:** El uso de Kafka para operaciones que no requieren una respuesta inmediata mejora drásticamente el rendimiento percibido por el usuario.
    - **Monitorización de Rendimiento (APM):** Con **Jaeger** y **Prometheus**, mediremos los tiempos de respuesta de cada paso en la transacción (Gateway -> Hub -> z/OS Connect -> CICS) para identificar y optimizar cuellos de botella.

#### 4.2. ¿Cómo integrar la solución SaaS en una estrategia ALM de extremo a extremo (CI/CD, versionado)?

Se establecerán dos flujos de trabajo de CI/CD paralelos pero coordinados:

1.  **CI/CD para el Mundo Moderno (Hub de Integración, Gateway):**
    - **Repositorio:** **Git** (con GitFlow o Trunk-Based Development).
    - **Pipeline:** **Jenkins** o **GitLab CI**.
        - `Commit` -> `Compilación (Maven)` -> `Pruebas Unitarias (JUnit)` -> `Análisis de Código (SonarQube)` -> `Creación de Imagen Docker` -> `Despliegue en entorno de Desarrollo`.
    - **Versionado:** Versionado Semántico (SemVer) para las imágenes Docker y las APIs.
    - **Gestión de Cambios:** Los despliegues en producción se gestionarán a través de peticiones de cambio en **Jira** o **ServiceNow**, con aprobaciones automáticas si todos los pasos del pipeline son exitosos.

2.  **Interfaz con el ALM del Mainframe:**
    - El equipo de Mainframe continuará usando sus herramientas tradicionales (ej. **Endevor**, **ISPF**).
    - El **contrato de la API** expuesta por z/OS Connect es el punto de unión. Este contrato (un archivo OpenAPI/Swagger) se almacenará en un repositorio Git compartido.
    - Si el equipo Mainframe necesita cambiar la API, modificará el contrato en una rama de Git. Esto disparará una notificación al equipo del Hub para que se adapte al cambio. Se usarán **pruebas de contrato (Pact)** para asegurar que las integraciones no se rompan.

#### 4.3. ¿Cómo proporcionar observabilidad en toda la arquitectura?

Se implementarán los **Tres Pilares de la Observabilidad**:

1.  **Logs (Registros):**
    - El Hub de Integración generará logs estructurados (en formato JSON) para cada transacción, incluyendo un **ID de Correlación** único que se propagará desde el API Gateway hasta el Mainframe (si es posible).
    - Estos logs se enviarán a un **ELK Stack (Elasticsearch, Logstash, Kibana)**. Kibana permitirá a los equipos de soporte buscar y analizar flujos de transacciones completos.

2.  **Metrics (Métricas):**
    - El Hub de Integración expondrá métricas de negocio y técnicas a través de **Spring Boot Actuator** en un formato compatible con **Prometheus**.
    - Métricas clave: número de transacciones por minuto, tasa de error, latencia de las APIs de z/OS Connect, etc.
    - **Grafana** se usará para crear dashboards y visualizar estas métricas en tiempo real. Se configurarán **alertas (Alertmanager)** para notificar al equipo de guardia sobre anomalías (ej. latencia > 500ms).

3.  **Traces (Trazas Distribuidas):**
    - Se usará **OpenTelemetry** como estándar. El API Gateway inyectará un ID de traza en la primera petición.
    - Cada servicio (Gateway, Hub) añadirá su propio *span* a la traza, midiendo el tiempo que tarda en procesar la petición.
    - Las trazas se enviarán a **Jaeger**, que permitirá visualizar el flujo completo de una petición a través de los microservicios, identificando rápidamente qué componente es lento.

#### 4.4. ¿Qué enfoques usar para asegurar la cobertura de pruebas?

Se adoptará una estrategia de pirámide de pruebas:

- **Pruebas Unitarias (rápidas y baratas):** En el Hub, se probará la lógica de negocio de forma aislada usando **JUnit** y **Mockito**. Cobertura objetivo: >80%.
- **Pruebas de Integración:** Se probará la interacción del Hub con otros componentes, como Kafka y la base de datos de caché (Redis), usando **Testcontainers** para levantar instancias efímeras de estos servicios en Docker durante la prueba.
- **Pruebas de Contrato:** Se usará **Pact**. El Hub (consumidor) definirá las expectativas que tiene sobre la API del Mainframe (proveedor). El equipo de Mainframe verificará que su API (expuesta con z/OS Connect) cumple con ese contrato. Esto previene roturas por cambios inesperados.
- **Pruebas E2E (End-to-End):** Se ejecutarán flujos de negocio completos en un entorno de pruebas integrado. Por ejemplo, simular que el SaaS crea un cliente, verificar que el evento llega a Kafka y que el Hub llama correctamente a la API del Mainframe. Estas pruebas serán pocas pero muy valiosas.

#### 4.5. ¿Cómo manejar el desmantelamiento de componentes legacy?

Este es el objetivo final del patrón **Strangler Fig**:

1.  **Identificar una Función de Negocio:** Se elige una funcionalidad para migrar desde el Mainframe (ej. "cálculo de prima de seguro").
2.  **Desarrollar como Microservicio:** Se crea un nuevo microservicio en la arquitectura Magenta que implementa esa lógica.
3.  **Cambiar el Enrutamiento:** En el Hub de Integración, se modifica la lógica. Las llamadas que antes iban a la API de z/OS Connect para el cálculo de primas, ahora se dirigen al nuevo microservicio.
4.  **Verificar y Desmantelar:** Una vez que se confirma que el nuevo servicio funciona correctamente y que ya no hay llamadas a la función del Mainframe, el código COBOL correspondiente puede ser archivado y eliminado.
5.  **Repetir:** Se repite el proceso para la siguiente función de negocio, "estrangulando" gradualmente la funcionalidad del Mainframe hasta que solo quede el núcleo de persistencia de datos, que podría ser el último en migrarse.

---

## 5. Desafíos Adicionales y Mitigaciones

- **Desafío Cultural:** Resistencia a la colaboración entre los equipos de Mainframe y los equipos de desarrollo modernos.
    - **Mitigación:** Crear equipos mixtos (*squads*) con miembros de ambos mundos para fomentar la colaboración. Definir claramente las interfaces (APIs) como el lenguaje común.
- **Rendimiento del Mainframe:** Las nuevas cargas de trabajo de las APIs podrían impactar los MIPS del Mainframe.
    - **Mitigación:** Implementar caché agresiva en el Hub. Realizar pruebas de carga exhaustivas para entender el impacto y planificar la capacidad del Mainframe.
- **Consistencia de Datos:** Mantener la consistencia entre la base de datos Db2 del Mainframe y posibles nuevas bases de datos de microservicios.
    - **Mitigación:** Usar patrones como **Transactional Outbox** junto con Kafka para garantizar que los cambios de estado se publiquen de forma fiable.

---

## 6. Cronograma Estimado

Este proyecto se puede dividir en las siguientes fases:

- **Fase 1: Descubrimiento y Diseño (Semanas 1-4)**
    - Talleres con los equipos de Mainframe y negocio para entender los programas COBOL y la lógica a exponer.
    - Diseño detallado de las APIs en z/OS Connect y en el Hub.
    - Configuración de la infraestructura base (Kubernetes, Kafka, Jenkins).

- **Fase 2: Prueba de Concepto (PoC) (Semanas 5-8)**
    - Exponer una única función de negocio del Mainframe vía z/OS Connect.
    - Construir un esqueleto del Hub que la consuma.
    - Demostrar un flujo E2E simple para validar la arquitectura.

- **Fase 3: Implementación del MVP (Semanas 9-16)**
    - Desarrollar las funcionalidades clave de integración para el lanzamiento inicial del SaaS.
    - Implementar la estrategia de observabilidad y CI/CD.

- **Fase 4: Pruebas y Puesta en Producción (Semanas 17-20)**
    - Pruebas de carga, seguridad y UAT (Pruebas de Aceptación de Usuario).
    - Despliegue en el entorno de producción.

- **Fase 5: Evolución y Mantenimiento (Continuo)**
    - Soporte post-lanzamiento y planificación de la migración de la siguiente funcionalidad del Mainframe.

**Tiempo total estimado para el MVP: 5 meses.**

---

## 7. Estructura del Repositorio

- **/documentacion:** Contiene diagramas de arquitectura (PlantUML).
- **/integration-hub:** Código fuente del Hub de Integración (Spring Boot).
- **/mainframe-assets:** Ejemplos de código COBOL y JCL para ilustrar los componentes legacy.
- **/api-gateway:** Ficheros de configuración del API Gateway (Spring Cloud Gateway).

