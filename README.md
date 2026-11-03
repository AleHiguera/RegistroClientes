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
- **Validación en el servidor** (usuario, correo, contraseña de mínimo 8 caracteres con letras y números).
- **Borrar la cuenta pide de nuevo la contraseña** y una confirmación (s/n).
- Sesión ligada a la conexión, con cierre automático tras 15 minutos de inactividad.

## Estructura

```
src/
  comun/      Protocolo de mensajes, validaciones y huella del certificado
  servidor/   ServidorAuth (main), BaseDatos, GestorCuentas, AtencionCliente
  cliente/    ClienteConsola (main, menús), ConexionServidor, ConfianzaTofu
lib/
  sqlite-jdbc-3.51.0.0.jar
  jbcrypt-0.4.jar
```
