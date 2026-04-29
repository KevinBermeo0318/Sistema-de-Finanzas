Sistema de Finanzas (Spring Boot)
================================

Descripcion
----------
Proyecto de gestion de finanzas personales usando Spring Boot. Permite:
- Registrar ingresos y gastos
- Categorizar transacciones (comida, transporte, etc.)
- Generar reportes mensuales con totales y por categoria
- Exportar/reportar gráficos en PNG (puede conectarse a un frontend)

Estructura
---------
- pom.xml: descriptor Maven con dependencias (Spring Boot, JPA, H2, Apache POI, PDFBox, XChart)
- src/main/java: codigo fuente del backend
  - com.example.finance.FinanceApplication: clase principal
  - model: entidades Category y Transaction
  - repository: JPA repositories
  - service: logica de negocio (FinanceService)
  - controller: endpoints REST (FinanceController)
- src/main/resources/application.properties: configuracion (H2 en memoria)

Dependencias
-----------
Usamos Spring Boot 3.x, H2 (in-memory), Apache POI (para Excel), PDFBox (para PDF en el futuro) y XChart para graficas PNG.

Endpoints
---------
- POST /api/transactions
  - Crear una transaccion. JSON: {"amount": 10.0, "type": "expense", "category": "comida", "description": "almuerzo", "date": "2024-04-28"}
- GET /api/transactions?start=YYYY-MM-DD&end=YYYY-MM-DD&category=xxx
  - Listar transacciones con filtros opcionales
- GET /api/report/monthly?year=2024&month=4
  - Devuelve JSON con income, expense, byCategory y transactions
- GET /api/report/monthly/plot?year=2024&month=4
  - Devuelve PNG con grafica por categoria

Ejecucion
---------
1. Compilar y ejecutar con Maven:

   mvn spring-boot:run

2. Acceder a API en http://localhost:8080

Autenticacion
------------
Se habilito seguridad básica HTTP. Credenciales por defecto (en memoria):
- user / password

Para los endpoints use Basic Auth con esas credenciales.

Tests
-----
Ejecutar tests con:

  mvn test


Notas
-----
- La aplicacion usa H2 en memoria; para persistencia en disco ajusta spring.datasource.url.
- PDF export no esta completamente implementado, pero se incluyeron las dependencias necesarias para agregarla.
- No hay autenticacion; todo es accesible localmente.

Firma
-----
Kevin S. Bermeo Rico - Estudiante de Ing de Sistemas Univalle
