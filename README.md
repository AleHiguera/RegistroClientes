# Registro de clientes

Aplicación cliente-servidor en Java para registrar clientes con autenticación segura.
Todo se usa desde la terminal: el cliente tiene menús para **crear la cuenta**, **iniciar sesión**,
**ver tus datos** y **borrar la cuenta**.

## Cómo ejecutarlo (VS Code o cualquier terminal)

Requisito: JDK 17 o superior (probado con JDK 23). No hace falta ninguna extensión.

1. Abre la carpeta del proyecto en VS Code y abre una terminal (**Ctrl + ñ**).
2. Compila:
   ```
   .\compilar.bat
   ```
3. Arranca el servidor:
   ```
   .\servidor.bat
   ```
   (opcional: `.\servidor.bat 9000` para usar otro puerto)
4. Abre **otra** terminal (botón **+** del panel de terminal) y arranca el cliente:
   ```
   .\cliente.bat
   ```
   Puedes abrir varias terminales con varios clientes.

También están como tareas de VS Code: **Ctrl + Shift + P → Tasks: Run Task → Servidor / Cliente**.

Al arrancar, el servidor muestra las IP a las que se pueden conectar los clientes de la red.
Las contraseñas no se ven en pantalla mientras las escribes.

## Iniciar sesión desde Brilliant

El servidor también abre una API HTTPS (puerto **8443**) para la app Brilliant, que corre en el navegador
y no puede usar el puerto 8080 de la consola. Usa las mismas cuentas: se pueden crear desde la consola
o desde la propia app.

1. Arranca el servidor (`.\servidor.bat`, o `.\servidor.bat 8080 8443` para elegir los dos puertos).
2. **Una vez por navegador/celular**: abre `https://<IP-del-servidor>:8443`, comprueba que la huella
   coincide con la que imprime el servidor y acepta el aviso del certificado.
3. Abre Brilliant: pide usuario y PIN antes de elegir el mapa. Por defecto busca el servidor en el mismo
   equipo desde el que se abrió la app (se puede cambiar en "Servidor" o con
   `flutter run --dart-define=SERVIDOR=https://192.168.1.5:8443`).

| Petición | Datos | Respuesta |
|---|---|---|
| `POST /api/login` | formulario `usuario`, `password` | JSON `ok`, `mensaje`, `usuario`, `nombre`, `token` |
| `POST /api/registro` | formulario `usuario`, `nombre`, `correo`, `password` | igual que login (la cuenta queda con sesión iniciada) |
| `POST /api/latido` | cabecera `Authorization: Bearer <token>` | JSON `ok`, `mensaje` (401 si la sesión ya no está activa) |
| `POST /api/logout` | cabecera `Authorization: Bearer <token>` | JSON `ok`, `mensaje` |

Un usuario solo puede estar conectado en un lugar a la vez, sumando consola y web: un segundo inicio de sesión
se rechaza (`409` en la API) con el mensaje "ya está conectado, intenta con otro". La consola libera el usuario al
cerrar sesión o perder la conexión. La app envía un latido cada 10 s y, si cierra sin avisar, el usuario queda
libre a los 30 s.

## Primera ejecución tras clonar

No hace falta configurar nada. Si no existen, el servidor los crea solo:

| Archivo | Qué es |
|---|---|
| `clientes.db` | Base de datos SQLite con la tabla `clientes` |
| `servidor.p12` | Certificado TLS del servidor (se genera con `keytool`, incluido en Java) |
| `servidores_conocidos.properties` | (lado cliente) huellas de los servidores en los que se confía |

Los tres están en `.gitignore`, así que cada computadora genera los suyos.

## Seguridad

- **Contraseñas con BCrypt** (librería jBCrypt, costo 12): se guarda solo el hash con sal, nunca la contraseña.
- **Conexión cifrada con TLS 1.2/1.3**: la contraseña no viaja en texto plano por la red.
- **Verificación del servidor**: la primera vez el cliente muestra la huella SHA-256 del certificado
  para compararla con la que imprime el servidor; después, si el certificado cambia, se rechaza la conexión.
- **Bloqueo por fuerza bruta**: 5 intentos fallidos bloquean ese usuario 5 minutos.
- **Sin pistas a atacantes**: el mismo mensaje y el mismo tiempo de respuesta tanto si el usuario existe como si no.
- **Consultas preparadas** (`PreparedStatement`) contra inyección SQL.
- **Validación en el servidor** (usuario, correo, contraseña: PIN de exactamente 4 números).
- **Borrar la cuenta pide de nuevo la contraseña** y una confirmación (s/n).
- Sesión ligada a la conexión, con cierre automático tras 15 minutos de inactividad.
- **Una sola sesión por usuario**: el aviso de "ya está conectado" solo aparece con la contraseña correcta.

## Estructura

```
src/
  comun/      Protocolo de mensajes, validaciones y huella del certificado
  servidor/   ServidorAuth (main), BaseDatos, GestorCuentas, SesionesActivas, AtencionCliente, ServidorWeb (API HTTPS)
  cliente/    ClienteConsola (main, menús), ConexionServidor, ConfianzaTofu
lib/
  sqlite-jdbc-3.51.0.0.jar
  jbcrypt-0.4.jar
```
