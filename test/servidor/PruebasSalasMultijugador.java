package servidor;

import static servidor.Pruebas.caso;
import static servidor.Pruebas.cierto;
import static servidor.Pruebas.iguales;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

final class PruebasSalasMultijugador {
    private static final long TRAS_AUSENCIA = SalasMultijugador.SIN_CONTACTO_MAX_MS + 60_000L;
    private static final String ORDEN = "1,2,3,4,5,6";
    private static final String OTRO_ORDEN = "6,5,4,3,2,1";

    private PruebasSalasMultijugador() {}

    private static String crear(String anfitrion, int capacidad) {
        return SalasMultijugador.crear(anfitrion, 1, capacidad).sala().codigo();
    }

    private static List<String> jugadores(String usuario, String codigo) {
        return SalasMultijugador.consultar(usuario, codigo).sala().jugadores();
    }

    private static SalasMultijugador.Vista sala(String usuario, String codigo) {
        return SalasMultijugador.consultar(usuario, codigo).sala();
    }

    /** Sala de [anfitrion] con un invitado ("b" + el resto del nombre), ya comenzada porque ambos confirmaron. */
    private static String comenzada(String anfitrion, int capacidad) {
        String invitado = "b" + anfitrion.substring(1);
        String codigo = crear(anfitrion, capacidad);
        SalasMultijugador.unirse(invitado, codigo);
        SalasMultijugador.marcarListo(invitado, codigo, true);
        SalasMultijugador.marcarListo(anfitrion, codigo, true);
        return codigo;
    }

    static void ejecutar() {
        caso("Solo se aceptan de 2 a 6 jugadores", () -> {
            cierto(!SalasMultijugador.crear("a1", 1, 1).exito(), "aceptó 1 jugador");
            cierto(!SalasMultijugador.crear("a1", 1, 7).exito(), "aceptó 7 jugadores");
            cierto(SalasMultijugador.crear("a1", 1, 2).exito(), "rechazó 2 jugadores");
            cierto(SalasMultijugador.crear("a1", 1, 6).exito(), "rechazó 6 jugadores");
        });

        caso("Solo se aceptan los mapas del 1 al 4", () -> {
            cierto(!SalasMultijugador.crear("a2", 0, 2).exito(), "aceptó el mapa 0");
            cierto(!SalasMultijugador.crear("a2", 5, 2).exito(), "aceptó el mapa 5");
            cierto(SalasMultijugador.crear("a2", 4, 2).exito(), "rechazó el mapa 4");
        });

        caso("El creador entra como anfitrión con lo que eligió", () -> {
            var sala = SalasMultijugador.crear("a3", 3, 4).sala();
            iguales("a3", sala.anfitrion());
            iguales(List.of("a3"), sala.jugadores());
            iguales(3, sala.mapa());
            iguales(4, sala.capacidad());
        });

        caso("Los jugadores aparecen en orden de llegada", () -> {
            String codigo = crear("a4", 4);
            SalasMultijugador.unirse("b4", codigo);
            SalasMultijugador.unirse("c4", codigo);
            iguales(List.of("a4", "b4", "c4"), jugadores("a4", codigo));
        });

        caso("El código sirve en minúsculas y con espacios", () -> {
            String codigo = crear("a5", 2);
            cierto(SalasMultijugador.unirse("b5", " " + codigo.toLowerCase(Locale.ROOT) + " ").exito(), "no entró");
        });

        caso("Un código inexistente se rechaza", () ->
                iguales("No existe ninguna partida con ese código.", SalasMultijugador.unirse("a6", "------").mensaje()));

        caso("Una sala llena no acepta a nadie más", () -> {
            String codigo = crear("a7", 2);
            SalasMultijugador.unirse("b7", codigo);
            iguales("La partida ya está llena.", SalasMultijugador.unirse("c7", codigo).mensaje());
        });

        caso("Volver a unirse no ocupa otro lugar, aunque la sala esté llena", () -> {
            String codigo = crear("a8", 2);
            SalasMultijugador.unirse("b8", codigo);
            cierto(SalasMultijugador.unirse("b8", codigo).exito(), "rechazó a quien ya estaba");
            iguales(List.of("a8", "b8"), jugadores("a8", codigo));
        });

        caso("Quien no está en la sala no puede consultarla", () -> {
            String codigo = crear("a9", 2);
            cierto(!SalasMultijugador.consultar("intruso", codigo).exito(), "dejó consultar a un extraño");
        });

        caso("Al salir un invitado se libera su lugar", () -> {
            String codigo = crear("a10", 2);
            SalasMultijugador.unirse("b10", codigo);
            SalasMultijugador.salir("b10", codigo);
            cierto(SalasMultijugador.unirse("c10", codigo).exito(), "no se liberó el lugar");
        });

        caso("Si el anfitrión sale la sala se cierra para todos", () -> {
            String codigo = crear("a11", 2);
            SalasMultijugador.unirse("b11", codigo);
            SalasMultijugador.salir("a11", codigo);
            cierto(!SalasMultijugador.consultar("b11", codigo).exito(), "la sala siguió abierta");
        });

        caso("Salir de una sala que ya no existe no es un error", () ->
                cierto(SalasMultijugador.salir("a12", "------").exito(), "falló al salir"));

        caso("Un jugador está en una sola sala a la vez", () -> {
            String primera = crear("a13", 3);
            String segunda = crear("b13", 3);
            SalasMultijugador.unirse("c13", primera);
            SalasMultijugador.unirse("c13", segunda);
            iguales(List.of("a13"), jugadores("a13", primera));
            iguales(List.of("b13", "c13"), jugadores("b13", segunda));
        });

        caso("Si la nueva sala está llena se conserva el lugar en la anterior", () -> {
            String primera = crear("a14", 3);
            String llena = crear("b14", 2);
            SalasMultijugador.unirse("c14", primera);
            SalasMultijugador.unirse("d14", llena);
            cierto(!SalasMultijugador.unirse("c14", llena).exito(), "entró a una sala llena");
            iguales(List.of("a14", "c14"), jugadores("a14", primera));
        });

        caso("Crear otra sala cierra la que el anfitrión tenía", () -> {
            String anterior = crear("a15", 2);
            crear("a15", 2);
            cierto(!SalasMultijugador.consultar("a15", anterior).exito(), "la sala anterior siguió abierta");
        });

        caso("Nadie se pasa del cupo aunque entren todos a la vez", () -> {
            String codigo = crear("a16", 6);
            ExecutorService hilos = Executors.newFixedThreadPool(16);
            List<Callable<Boolean>> entradas = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                String usuario = "carrera" + i;
                entradas.add(() -> SalasMultijugador.unirse(usuario, codigo).exito());
            }
            int aceptados = 0;
            for (Future<Boolean> entrada : hilos.invokeAll(entradas)) {
                aceptados += entrada.get() ? 1 : 0;
            }
            hilos.shutdown();
            iguales(5, aceptados);
            iguales(6, jugadores("a16", codigo).size());
        });

        caso("Un jugador que sigue consultando no se considera ausente", () -> {
            String codigo = crear("a17", 2);
            SalasMultijugador.quitarAusentes(System.currentTimeMillis() + SalasMultijugador.SIN_CONTACTO_MAX_MS - 60_000L);
            cierto(SalasMultijugador.consultar("a17", codigo).exito(), "se quitó antes de tiempo");
        });

        caso("Una sala nueva todavía no ha comenzado", () ->
                cierto(!SalasMultijugador.crear("a19", 1, 2).sala().comenzada(), "nació comenzada"));

        caso("Al entrar nadie está listo", () -> {
            String codigo = crear("a20", 3);
            SalasMultijugador.unirse("b20", codigo);
            iguales(List.of(), sala("a20", codigo).listos());
        });

        caso("Quien se marca listo aparece así para todos", () -> {
            String codigo = crear("a27", 3);
            SalasMultijugador.unirse("b27", codigo);
            SalasMultijugador.unirse("c27", codigo);
            SalasMultijugador.marcarListo("c27", codigo, true);
            SalasMultijugador.marcarListo("a27", codigo, true);
            iguales(List.of("a27", "c27"), sala("b27", codigo).listos());
        });

        caso("Mientras falte alguien por confirmar no comienza", () -> {
            String codigo = crear("a21", 3);
            SalasMultijugador.unirse("b21", codigo);
            SalasMultijugador.unirse("c21", codigo);
            SalasMultijugador.marcarListo("a21", codigo, true);
            SalasMultijugador.marcarListo("b21", codigo, true);
            cierto(!sala("c21", codigo).comenzada(), "comenzó sin c21");
        });

        caso("Cuando todos están listos comienza sola", () -> {
            String codigo = crear("a22", 3);
            SalasMultijugador.unirse("b22", codigo);
            SalasMultijugador.marcarListo("b22", codigo, true);
            var ultimo = SalasMultijugador.marcarListo("a22", codigo, true);
            iguales("La partida comenzó.", ultimo.mensaje());
            cierto(ultimo.sala().comenzada(), "no comenzó");
            cierto(sala("b22", codigo).comenzada(), "el invitado no lo ve");
        });

        caso("A solas no comienza aunque el anfitrión esté listo", () -> {
            String codigo = crear("a23", 2);
            SalasMultijugador.marcarListo("a23", codigo, true);
            cierto(!sala("a23", codigo).comenzada(), "comenzó con un solo jugador");
        });

        caso("Quien se desmarca vuelve a hacer esperar a los demás", () -> {
            String codigo = crear("a24", 2);
            SalasMultijugador.unirse("b24", codigo);
            SalasMultijugador.marcarListo("b24", codigo, true);
            SalasMultijugador.marcarListo("b24", codigo, false);
            SalasMultijugador.marcarListo("a24", codigo, true);
            iguales(List.of("a24"), sala("a24", codigo).listos());
            cierto(!sala("a24", codigo).comenzada(), "comenzó sin b24");
        });

        caso("Si llega alguien nuevo también debe confirmar", () -> {
            String codigo = crear("a25", 3);
            SalasMultijugador.unirse("b25", codigo);
            SalasMultijugador.marcarListo("b25", codigo, true);
            SalasMultijugador.unirse("c25", codigo);
            SalasMultijugador.marcarListo("a25", codigo, true);
            cierto(!sala("a25", codigo).comenzada(), "comenzó sin c25");
            SalasMultijugador.marcarListo("c25", codigo, true);
            cierto(sala("a25", codigo).comenzada(), "no comenzó con todos listos");
        });

        caso("Si sale el único que faltaba comienza con los demás", () -> {
            String codigo = crear("a26", 3);
            SalasMultijugador.unirse("b26", codigo);
            SalasMultijugador.unirse("c26", codigo);
            SalasMultijugador.marcarListo("a26", codigo, true);
            SalasMultijugador.marcarListo("b26", codigo, true);
            SalasMultijugador.salir("c26", codigo);
            cierto(sala("a26", codigo).comenzada(), "no comenzó al irse c26");
        });

        caso("Quien sale pierde su confirmación", () -> {
            String codigo = crear("a28", 3);
            SalasMultijugador.unirse("b28", codigo);
            SalasMultijugador.unirse("c28", codigo);
            SalasMultijugador.marcarListo("b28", codigo, true);
            SalasMultijugador.salir("b28", codigo);
            SalasMultijugador.unirse("b28", codigo);
            iguales(List.of(), sala("a28", codigo).listos());
        });

        caso("Quien no está en la sala no puede marcarse listo", () -> {
            String codigo = crear("a29", 2);
            iguales("Esta partida ya no está disponible.",
                    SalasMultijugador.marcarListo("intruso", codigo, true).mensaje());
        });

        caso("Nadie nuevo entra a una partida que ya comenzó", () -> {
            String codigo = comenzada("a30", 4);
            iguales("La partida ya comenzó.", SalasMultijugador.unirse("c30", codigo).mensaje());
            iguales(List.of("a30", "b30"), jugadores("a30", codigo));
        });

        caso("Quien ya estaba dentro puede volver aunque haya comenzado", () -> {
            String codigo = comenzada("a31", 2);
            cierto(SalasMultijugador.unirse("b31", codigo).exito(), "no dejó volver a b31");
            iguales(List.of("a31", "b31"), jugadores("a31", codigo));
        });

        caso("Quien sale de una partida comenzada ya no puede volver", () -> {
            String codigo = comenzada("a32", 3);
            SalasMultijugador.salir("b32", codigo);
            iguales("La partida ya comenzó.", SalasMultijugador.unirse("b32", codigo).mensaje());
        });

        caso("Ya comenzada nadie puede desmarcarse", () -> {
            String codigo = comenzada("a33", 2);
            cierto(SalasMultijugador.marcarListo("b33", codigo, false).exito(), "falló al desmarcarse");
            cierto(sala("a33", codigo).comenzada(), "la partida dejó de estar comenzada");
            iguales(List.of("a33", "b33"), sala("a33", codigo).listos());
        });

        caso("Al comenzar nadie ha finalizado su tablero", () -> {
            var vista = sala("a34", comenzada("a34", 2));
            iguales(List.of(), vista.finalizados());
            cierto(!vista.configurada(), "ya estaba configurada");
            iguales(List.of(), vista.miTablero());
        });

        caso("Antes de comenzar no se puede finalizar el tablero", () -> {
            String codigo = crear("a35", 2);
            iguales("La partida todavía no comienza.",
                    SalasMultijugador.finalizarTablero("a35", codigo, ORDEN).mensaje());
        });

        caso("Sin todos los tableros los dados no se liberan", () -> {
            String codigo = comenzada("a36", 2);
            var vista = SalasMultijugador.finalizarTablero("a36", codigo, ORDEN).sala();
            iguales(List.of("a36"), vista.finalizados());
            cierto(!vista.configurada(), "se configuró con un tablero pendiente");
        });

        caso("Con todos los tableros finalizados la partida queda configurada", () -> {
            String codigo = comenzada("a37", 2);
            SalasMultijugador.finalizarTablero("a37", codigo, ORDEN);
            var vista = SalasMultijugador.finalizarTablero("b37", codigo, OTRO_ORDEN).sala();
            iguales(List.of("a37", "b37"), vista.finalizados());
            cierto(vista.configurada(), "no quedó configurada");
            cierto(sala("a37", codigo).configurada(), "el otro jugador no lo ve");
        });

        caso("Cada jugador conserva su propio orden y no ve el ajeno", () -> {
            String codigo = comenzada("a38", 2);
            SalasMultijugador.finalizarTablero("a38", codigo, ORDEN);
            iguales(List.of(), sala("b38", codigo).miTablero());
            SalasMultijugador.finalizarTablero("b38", codigo, OTRO_ORDEN);
            iguales(List.of(1, 2, 3, 4, 5, 6), sala("a38", codigo).miTablero());
            iguales(List.of(6, 5, 4, 3, 2, 1), sala("b38", codigo).miTablero());
        });

        caso("Un tablero finalizado ya no cambia", () -> {
            String codigo = comenzada("a39", 3);
            SalasMultijugador.finalizarTablero("a39", codigo, ORDEN);
            cierto(SalasMultijugador.finalizarTablero("a39", codigo, OTRO_ORDEN).exito(), "el reintento falló");
            iguales(List.of(1, 2, 3, 4, 5, 6), sala("a39", codigo).miTablero());
        });

        caso("Quien no está en la sala no puede finalizar", () -> {
            String codigo = comenzada("a40", 2);
            iguales("Esta partida ya no está disponible.",
                    SalasMultijugador.finalizarTablero("intruso", codigo, ORDEN).mensaje());
            iguales(List.of(), sala("a40", codigo).finalizados());
        });

        caso("Solo se aceptan los números del 1 al 6 sin repetir", () -> {
            String codigo = comenzada("a41", 2);
            for (String malo : List.of("", "1,2,3", "1,1,2,3,4,5", "0,1,2,3,4,5", "2,3,4,5,6,7", "1,2,3,4,5,x",
                    "1,2,3,4,5,6,6")) {
                cierto(!SalasMultijugador.finalizarTablero("a41", codigo, malo).exito(), "aceptó " + malo);
            }
            iguales(List.of(), sala("a41", codigo).finalizados());
        });

        caso("Si se va el único que faltaba, los demás ya no esperan", () -> {
            String codigo = comenzada("a42", 2);
            SalasMultijugador.finalizarTablero("a42", codigo, ORDEN);
            SalasMultijugador.salir("b42", codigo);
            cierto(sala("a42", codigo).configurada(), "siguió esperando a quien se fue");
        });

        // Esta va al final: deja vacías todas las salas de las pruebas anteriores.
        caso("Tras 5 minutos sin consultar el anfitrión pierde la sala", () -> {
            String codigo = crear("a18", 2);
            SalasMultijugador.unirse("b18", codigo);
            SalasMultijugador.quitarAusentes(System.currentTimeMillis() + TRAS_AUSENCIA);
            cierto(!SalasMultijugador.consultar("b18", codigo).exito(), "la sala siguió abierta");
        });
    }
}
