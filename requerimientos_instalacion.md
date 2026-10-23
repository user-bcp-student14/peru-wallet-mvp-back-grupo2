# Requerimientos de instalacion: backend wallet Java + Quarkus

## 1. Alcance

Proyecto: `C:\Proyecto\reqflow-workspace\hackaton`.
Sistema objetivo: Windows 10/11 con PowerShell 5.1 o superior y acceso a internet.
Backend acordado: Java con Quarkus, persistencia local en archivos JSON y datos mock.

Se incluyen esta guia y `instalar_requisitos.ps1`, el archivo realmente ejecutable.
Un Markdown no instala programas por abrirlo ni por escribir su nombre en terminal.
Mantener ambos archivos juntos. No se han ejecutado instalaciones al crear esta guia.

El instalador comprueba antes de instalar. No reinstala herramientas detectadas,
no elimina otras versiones de Java y no actualiza automaticamente un Maven antiguo.
Si existen instalaciones en rutas no estandar que no figuran en PATH/JAVA_HOME,
configurarlas primero para que el script pueda reconocerlas.

## 2. Que necesitamos

| Componente | Version o criterio | Para que sirve |
| --- | --- | --- |
| JDK, no solo JRE | Java 21 LTS, con `java` y `javac` | Compilar, probar y ejecutar el backend. El script instala Temurin 21 si no encuentra un JDK 21. |
| Apache Maven | Maven 3.9+; si falta, se instala 3.9.16 | Resolver dependencias, compilar, ejecutar pruebas y arrancar Quarkus. No se selecciona Maven 4 preview. |
| Git | Version disponible y operativa | Versionar y colaborar. No es necesario para ejecutar un JAR ya construido. |
| WinGet / App Installer | Disponible si se van a instalar paquetes con WinGet | Instalar JDK/Git y las opciones adicionales. |
| PowerShell | 5.1+ | Ejecutar el instalador en Windows. |
| VS Code | Opcional | Editor. Se puede solicitar su instalacion con un parametro. |
| GitHub CLI | Opcional | Issues, PR y autenticacion del laboratorio; no es requisito del backend. |
| Navegador | Chrome u otro instalado | Consultar Swagger/Dev UI cuando el proyecto los configure. |

La eleccion Java 21 es la base de este proyecto nuevo. Las extensiones y la version
de Quarkus se fijaran en el `pom.xml` al crear el backend; no se instala Quarkus
globalmente como si fuera un servidor externo.

## 3. Que NO necesitamos instalar para este alcance

- PostgreSQL, MySQL, MongoDB, SQLite ni otro servidor/base de datos.
- Docker, Podman, Kubernetes o GraalVM: no buscamos una imagen nativa ni contenedores.
- Python, CrewAI, MCP, OpenSpec, Node.js o Angular para compilar/ejecutar el backend.
  Son herramientas o proyectos separados y se prepararan solo si el ejercicio lo exige.
- Un programa especial para JSON. Java lo leera/escribira usando las dependencias
  del proyecto, por ejemplo Jackson mediante Quarkus REST Jackson.
- Postman: es opcional; PowerShell, curl o Swagger pueden probar la API.

## 4. Primero: comprobar sin instalar

Abrir PowerShell y ejecutar:

```powershell
Set-Location 'C:\Proyecto\reqflow-workspace\hackaton'
.\instalar_requisitos.ps1
```

Este modo solo detecta herramientas y ejecuta consultas de version. No descarga,
no instala y no modifica PATH, JAVA_HOME ni datos del proyecto. Que termine sin
excepcion no significa que todo exista: revisar la tabla y cualquier aviso FALTA.

Si Windows bloquea un script descargado, revisar primero su contenido. Si confias
en el archivo, puedes desbloquear solo ese archivo con `Unblock-File` o aplicar
una politica temporal de proceso, siempre que la organizacion lo permita:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy RemoteSigned
```

No modificar la politica global ni evadir una politica corporativa. Si hay una
restriccion, solicitar ayuda al administrador.

## 5. Instalar exclusivamente lo que falte

Solo cuando hayas revisado el diagnostico:

```powershell
.\instalar_requisitos.ps1 -Instalar
```

Opcionalmente, incluir editor y herramientas de colaboracion:

```powershell
.\instalar_requisitos.ps1 -Instalar -IncluirEditor -IncluirGitHubCli
```

Para comprobar tambien esas opciones sin instalarlas:

```powershell
.\instalar_requisitos.ps1 -IncluirEditor -IncluirGitHubCli
```

El script no se eleva automaticamente, no inicia sesiones y no solicita tokens.
Si WinGet o una politica exige permisos administrativos, detenerse y pedir al
administrador que complete la instalacion autorizada. No reintentar con elevacion
automatica ni compartir contrasenas.

Si `winget --version` no funciona, instalar/actualizar **App Installer** desde
Microsoft Store o pedirlo al administrador y abrir otra terminal. WinGet no se
descarga mediante scripts de terceros. Internet, permisos y acceso a los
repositorios de descarga son requisitos para una maquina nueva.

## 6. Que hace el instalador

1. Busca herramientas en el PATH de la terminal, en el PATH guardado del usuario
   y del sistema, y en rutas comunes de instalacion.
2. Busca un JDK 21 operativo, no solo una instalacion de `java` sin compilador.
3. Solo con `-Instalar`, usa WinGet para componentes ausentes. IDs utilizados:
   `EclipseAdoptium.Temurin.21.JDK`, `Git.Git` y, si se solicitan,
   `Microsoft.VisualStudioCode` y `GitHub.cli`.
4. Si falta Maven, descarga el ZIP 3.9.16 desde el archivo oficial de Apache,
   comprueba su SHA-512 publicado y lo extrae. La comparacion detecta corrupcion;
   no equivale a verificar una firma PGP con una clave independiente.
5. Instala Maven en `%LOCALAPPDATA%\Programs\Apache\Maven\apache-maven-3.9.16`.
6. Solo en modo instalacion, configura JAVA_HOME, MAVEN_HOME y agrega las rutas
   necesarias al PATH de usuario sin borrar sus otras entradas ni duplicarlas.
7. Comprueba que Maven 3.9+ puede ejecutarse con el JDK 21 encontrado.

Las versiones de parche de JDK/Git/editor dependen del catalogo WinGet; Maven
tiene una version de descarga fijada. Si ya existe un Maven 3.9+ operativo,
se conserva. Si la descarga o el checksum falla, no se intenta una fuente no oficial.

Se puede repetir el comando para detectar lo que falta. No es una transaccion con
rollback de programas: si un paso posterior falla, las instalaciones previas
correctas se conservan. Revisar la tabla y corregir el bloqueo antes de continuar.

## 7. Verificar en una terminal nueva

```powershell
java --version
javac --version
$env:JAVA_HOME
mvn --version
git --version
```

Esperado: `java` y `javac` 21; Maven 3.9+; y la salida de Maven indicando Java 21.
Si aparece otro Java, revisar el orden de PATH y JAVA_HOME antes de compilar.

Con varias versiones de JDK, el modo instalacion selecciona 21 para el usuario,
lo que puede afectar otros proyectos. El modo de comprobacion no cambia esa
seleccion. No desinstalar otros JDK ni cambiar configuracion global a ciegas.

## 8. Editor: extensiones opcionales

En VS Code, instalar **Extension Pack for Java** y **Quarkus Tools** desde
Extensiones. Son ayudas de desarrollo, no requisitos para Maven. Para evitar
reinstalaciones por terminal, consultar primero:

```powershell
code --list-extensions
```

Solo si faltan los identificadores respectivos:

```powershell
code --install-extension vscjava.vscode-java-pack
code --install-extension redhat.vscode-quarkus
```

GitHub Copilot requiere extension, licencia/sesion y politicas compatibles;
instalar herramientas no acredita ese acceso. No forma parte del runtime Java.

## 9. Dependencias del proyecto: se descargan con Maven

El proyecto ya tiene `pom.xml` con Quarkus 3.27.0 y Java 21. Maven resuelve las
dependencias mediante el BOM de Quarkus. Componentes utilizados:

| Dependencia | Uso |
| --- | --- |
| `quarkus-rest-jackson` | Endpoints REST y conversion JSON. |
| `quarkus-hibernate-validator` | Validacion de entradas. No implica usar una base de datos. |
| Extension de pruebas Quarkus/JUnit compatible con la version elegida | Pruebas del backend. |
| REST Assured, alcance test | Pruebas HTTP de los endpoints. |

No instalar Hibernate ORM ni drivers JDBC para guardar un archivo JSON.
Maven descarga las bibliotecas al compilar. El wrapper `mvnw`/`mvnw.cmd` ya esta
incluido y fija Maven 3.9.16; no requiere reinstalar el Maven global existente.

## 10. Persistencia mock JSON

No requiere instalacion adicional, pero si reglas de implementacion:

- Usar solo datos ficticios, sin DNI, credenciales ni documentos reales.
- Definir la ruta configurable del archivo y permisos de lectura/escritura.
- Conservar un seed de pruebas separado del archivo mutable de ejecucion.
- No sobrescribir el JSON en cada arranque ni borrar datos para corregir un error.
- Validar contenido y esquema, y controlar archivo inexistente o corrupto.
- Evitar escrituras concurrentes, escrituras parciales y duplicados por reintentos;
  definir escritura segura y recuperacion en el diseno del repositorio JSON.
- Aislar archivos temporales de pruebas del archivo de desarrollo.
- Mantener una sola instancia para esta practica; JSON local no equivale a una
  base transaccional distribuida ni es una solucion de pagos productiva.

La demo ya implementa un esquema JSON local y estados mock. KYC y el contrato
definitivos requieren aprobacion antes de usar datos reales. Consultar README.md
y docs/criterios_aceptacion.md para limites y supuestos.

## 11. Como correr el backend

Estos comandos se ejecutan desde esta carpeta, que ya contiene `pom.xml`.
El instalador solo prepara herramientas; el backend se creo en una entrega posterior.

Con Maven Wrapper (preferido cuando este versionado):

```powershell
.\mvnw.cmd test
.\mvnw.cmd quarkus:dev
```

O con Maven instalado:

```powershell
mvn test
mvn quarkus:dev
```

El backend escucha en `http://localhost:8080`, con salud en `/q/health` y Swagger
en `/q/swagger-ui/`. Las pruebas de este proyecto usan puerto aleatorio para no
interferir con un MCP del laboratorio en 8081.

Para empaquetar y ejecutar el fast-jar habitual de Quarkus:

```powershell
mvn package
java -jar target/quarkus-app/quarkus-run.jar
```

Conservar toda la carpeta `target/quarkus-app`, no solo el JAR. Detener el modo
desarrollo con Ctrl+C antes de arrancar otro proceso en el mismo puerto.

## 12. Validacion y fuentes

Validacion realizada el 23 de octubre de 2026: sintaxis PowerShell correcta y
ejecucion del modo de verificacion sin parametros. Se encontraron Java/Javac 21,
Maven 3.10.0 y Git. Maven pudo usar Java 21. La busqueda encontro Maven en el PATH
guardado del sistema aunque no aparecia inicialmente en el PATH de la terminal.

Se compararon PATH, JAVA_HOME y MAVEN_HOME de usuario y sistema antes/despues:
no cambiaron. No se ejecuto `-Instalar` ni se instalo ninguna herramienta.
La instalacion real en una maquina limpia, las descargas y los posibles permisos
de WinGet no quedan probados por esa comprobacion.

Fuentes consultadas: [Quarkus Getting Started](https://quarkus.io/guides/getting-started)
y [Apache Maven Downloads](https://maven.apache.org/download.cgi).
Actualizacion posterior: backend generado, 20 pruebas aprobadas y paquete Quarkus
construido con Maven Wrapper. La instalacion en una maquina nueva sigue sin probarse.