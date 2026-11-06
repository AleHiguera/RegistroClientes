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

    private PruebasSalasMultijugador() {}

    private static String crear(String anfitrion, int capacidad) {
        return SalasMultijugador.crear(anfitrion, 1, capacidad).sala().codigo();
    }

    private static List<String> jugadores(String usuario, String codigo) {
        return SalasMultijugador.consultar(usuario, codigo).sala().jugadores();
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

        // Esta va al final: deja vacías todas las salas de las pruebas anteriores.
        caso("Tras 5 minutos sin consultar el anfitrión pierde la sala", () -> {
            String codigo = crear("a18", 2);
            SalasMultijugador.unirse("b18", codigo);
            SalasMultijugador.quitarAusentes(System.currentTimeMillis() + TRAS_AUSENCIA);
            cierto(!SalasMultijugador.consultar("b18", codigo).exito(), "la sala siguió abierta");
        });
    }
}
