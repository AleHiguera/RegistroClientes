package servidor;

import static servidor.Pruebas.caso;
import static servidor.Pruebas.cierto;
import static servidor.Pruebas.iguales;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    /** Partida comenzada de [anfitrion] y su invitado, con ambos tableros finalizados: ya va en la ronda 1. */
    private static String enJuego(String anfitrion, int capacidad) {
        String codigo = comenzada(anfitrion, capacidad);
        SalasMultijugador.finalizarTablero(anfitrion, codigo, ORDEN);
        SalasMultijugador.finalizarTablero("b" + anfitrion.substring(1), codigo, OTRO_ORDEN);
        return codigo;
    }

    private static String enJuegoDeTres(String anfitrion, String segundo, String tercero) {
        String codigo = crear(anfitrion, 3);
        SalasMultijugador.unirse(segundo, codigo);
        SalasMultijugador.unirse(tercero, codigo);
        for (String jugador : List.of(anfitrion, segundo, tercero)) {
            SalasMultijugador.marcarListo(jugador, codigo, true);
        }
        for (String jugador : List.of(anfitrion, segundo, tercero)) {
            SalasMultijugador.finalizarTablero(jugador, codigo, ORDEN);
        }
        return codigo;
    }

    private static void completar(String usuario, String codigo, int zona) {
        SalasMultijugador.terminarTurno(usuario, codigo, sala(usuario, codigo).ronda(), false, zona);
    }

    private static void jugar(String usuario, String codigo) {
        SalasMultijugador.terminarTurno(usuario, codigo, sala(usuario, codigo).ronda(), false);
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

        caso("No hay dados mientras falte un tablero", () -> {
            String codigo = comenzada("a43", 2);
            SalasMultijugador.finalizarTablero("a43", codigo, ORDEN);
            var vista = sala("a43", codigo);
            iguales(0, vista.ronda());
            iguales(List.of(), vista.dados());
        });

        caso("Con el último tablero el sistema lanza los dados solo", () -> {
            var vista = sala("a44", enJuego("a44", 2));
            iguales(1, vista.ronda());
            iguales(2, vista.dados().size());
        });

        caso("Todos los jugadores ven exactamente el mismo resultado", () -> {
            String codigo = enJuegoDeTres("a45", "b45", "c45");
            var dados = sala("a45", codigo).dados();
            iguales(dados, sala("b45", codigo).dados());
            iguales(dados, sala("c45", codigo).dados());
        });

        caso("Consultar no vuelve a lanzar los dados", () -> {
            String codigo = enJuego("a46", 2);
            var primera = sala("a46", codigo);
            for (int i = 0; i < 20; i++) {
                iguales(primera.dados(), sala("b46", codigo).dados());
            }
            iguales(1, sala("a46", codigo).ronda());
        });

        caso("Los dados siempre valen del 1 al 6", () -> {
            String codigo = enJuego("a47", 2);
            for (int i = 0; i < 200; i++) {
                for (int dado : sala("a47", codigo).dados()) {
                    cierto(dado >= 1 && dado <= 6, "salió un " + dado);
                }
                jugar("a47", codigo);
                jugar("b47", codigo);
            }
            iguales(201, sala("a47", codigo).ronda());
        });

        caso("La ronda no avanza hasta que todos jugaron", () -> {
            String codigo = enJuego("a48", 2);
            var antes = sala("a48", codigo);
            jugar("a48", codigo);
            var despues = sala("b48", codigo);
            iguales(1, despues.ronda());
            iguales(antes.dados(), despues.dados());
            iguales(List.of("a48"), despues.jugaron());
        });

        caso("Cuando juega el último se lanza la siguiente ronda", () -> {
            String codigo = enJuego("a49", 2);
            jugar("a49", codigo);
            jugar("b49", codigo);
            var vista = sala("a49", codigo);
            iguales(2, vista.ronda());
            iguales(List.of(), vista.jugaron());
        });

        caso("Avisar dos veces no juega por los demás", () -> {
            String codigo = enJuego("a50", 2);
            SalasMultijugador.terminarTurno("a50", codigo, 1, false);
            SalasMultijugador.terminarTurno("a50", codigo, 1, false);
            iguales(1, sala("a50", codigo).ronda());
        });

        caso("Un aviso de otra ronda no cuenta", () -> {
            String codigo = enJuego("a51", 2);
            jugar("a51", codigo);
            jugar("b51", codigo);
            cierto(SalasMultijugador.terminarTurno("a51", codigo, 1, false).exito(), "rechazó el reintento");
            SalasMultijugador.terminarTurno("a51", codigo, 9, false);
            iguales(List.of(), sala("a51", codigo).jugaron());
        });

        caso("Antes de la primera ronda no se puede terminar un turno", () -> {
            String codigo = comenzada("a52", 2);
            SalasMultijugador.terminarTurno("a52", codigo, 0, false);
            iguales(List.of(), sala("a52", codigo).jugaron());
        });

        caso("Quien no está en la partida no puede terminar un turno", () -> {
            String codigo = enJuego("a53", 2);
            iguales("Esta partida ya no está disponible.",
                    SalasMultijugador.terminarTurno("intruso", codigo, 1, false).mensaje());
            iguales(List.of(), sala("a53", codigo).jugaron());
        });

        caso("Si se va el único que faltaba se lanza la siguiente ronda", () -> {
            String codigo = enJuegoDeTres("a54", "b54", "c54");
            jugar("a54", codigo);
            jugar("b54", codigo);
            SalasMultijugador.salir("c54", codigo);
            iguales(2, sala("a54", codigo).ronda());
        });

        caso("Quien llenó su tablero ya no hace esperar a los demás", () -> {
            String codigo = enJuego("a55", 2);
            SalasMultijugador.terminarTurno("a55", codigo, 1, true);
            jugar("b55", codigo);
            iguales(2, sala("b55", codigo).ronda());
            iguales(List.of("a55"), sala("b55", codigo).jugaron());
            jugar("b55", codigo);
            iguales(3, sala("b55", codigo).ronda());
        });

        caso("Con todos los tableros llenos no se lanza más", () -> {
            String codigo = enJuego("a56", 2);
            SalasMultijugador.terminarTurno("a56", codigo, 1, true);
            SalasMultijugador.terminarTurno("b56", codigo, 1, true);
            iguales(1, sala("a56", codigo).ronda());
        });

        caso("Si el anfitrión sale de una partida comenzada, sigue para los demás", () -> {
            String codigo = enJuegoDeTres("a57", "b57", "c57");
            SalasMultijugador.salir("a57", codigo);
            iguales(List.of("b57", "c57"), jugadores("b57", codigo));
            iguales(1, sala("c57", codigo).ronda());
        });

        caso("Si el anfitrión era el único que faltaba se lanza la siguiente ronda", () -> {
            String codigo = enJuegoDeTres("a58", "b58", "c58");
            jugar("b58", codigo);
            jugar("c58", codigo);
            SalasMultijugador.salir("a58", codigo);
            iguales(2, sala("b58", codigo).ronda());
        });

        caso("Si el anfitrión sale mientras colocan los números, no se les espera", () -> {
            String codigo = comenzada("a59", 2);
            SalasMultijugador.finalizarTablero("b59", codigo, ORDEN);
            SalasMultijugador.salir("a59", codigo);
            var vista = sala("b59", codigo);
            cierto(vista.configurada(), "siguió esperando al anfitrión");
            iguales(1, vista.ronda());
        });

        caso("El anfitrión que sale de una partida comenzada ya no puede volver", () -> {
            String codigo = enJuego("a60", 2);
            SalasMultijugador.salir("a60", codigo);
            iguales("La partida ya comenzó.", SalasMultijugador.unirse("a60", codigo).mensaje());
            cierto(!SalasMultijugador.consultar("a60", codigo).exito(), "siguió viendo la partida");
        });

        caso("Crear otra sala no cierra la partida comenzada que el anfitrión dejó", () -> {
            String codigo = enJuego("a61", 2);
            SalasMultijugador.crear("a61", 1, 2);
            iguales(List.of("b61"), jugadores("b61", codigo));
        });

        caso("Cuando sale el último jugador la partida desaparece", () -> {
            String codigo = enJuego("a62", 2);
            SalasMultijugador.salir("a62", codigo);
            SalasMultijugador.salir("b62", codigo);
            iguales("No existe ninguna partida con ese código.", SalasMultijugador.unirse("b62", codigo).mensaje());
        });

        caso("Insistir no provoca un lanzamiento mientras otros juegan", () -> {
            String codigo = enJuegoDeTres("a64", "b64", "c64");
            var dados = sala("a64", codigo).dados();
            for (int i = 0; i < 5; i++) {
                SalasMultijugador.terminarTurno("a64", codigo, 1, false);
            }
            SalasMultijugador.terminarTurno("a64", codigo, 2, false);
            var vista = sala("b64", codigo);
            iguales(1, vista.ronda());
            iguales(dados, vista.dados());
            iguales(List.of("a64"), vista.jugaron());
        });

        caso("Ninguna otra acción de un jugador lanza los dados", () -> {
            String codigo = enJuegoDeTres("a65", "b65", "c65");
            var dados = sala("a65", codigo).dados();
            SalasMultijugador.finalizarTablero("a65", codigo, ORDEN);
            SalasMultijugador.marcarListo("a65", codigo, true);
            SalasMultijugador.marcarListo("a65", codigo, false);
            SalasMultijugador.unirse("a65", codigo);
            SalasMultijugador.salir("intruso", codigo);
            var vista = sala("a65", codigo);
            iguales(1, vista.ronda());
            iguales(dados, vista.dados());
        });

        caso("Declarar lleno el propio tablero no juega por los demás", () -> {
            String codigo = enJuegoDeTres("a66", "b66", "c66");
            SalasMultijugador.terminarTurno("a66", codigo, 1, true);
            iguales(1, sala("b66", codigo).ronda());
        });

        caso("Si se va quien ya jugó, se sigue esperando a los que faltan", () -> {
            String codigo = enJuegoDeTres("a67", "b67", "c67");
            jugar("b67", codigo);
            SalasMultijugador.salir("b67", codigo);
            var vista = sala("a67", codigo);
            iguales(1, vista.ronda());
            iguales(List.of(), vista.jugaron());
            jugar("a67", codigo);
            iguales(1, sala("c67", codigo).ronda());
            jugar("c67", codigo);
            iguales(2, sala("a67", codigo).ronda());
        });

        caso("El mismo procedimiento se repite ronda tras ronda", () -> {
            String codigo = enJuegoDeTres("a68", "b68", "c68");
            for (int ronda = 1; ronda <= 10; ronda++) {
                final int esperada = ronda;
                var dados = sala("a68", codigo).dados();
                jugar("c68", codigo);
                iguales(List.of("c68"), sala("a68", codigo).jugaron());
                jugar("a68", codigo);
                iguales(List.of("a68", "c68"), sala("b68", codigo).jugaron());
                iguales(esperada, sala("b68", codigo).ronda());
                iguales(dados, sala("b68", codigo).dados());
                jugar("b68", codigo);
                var nueva = sala("c68", codigo);
                iguales(esperada + 1, nueva.ronda());
                iguales(List.of(), nueva.jugaron());
                iguales(nueva.dados(), sala("a68", codigo).dados());
                iguales(nueva.dados(), sala("b68", codigo).dados());
            }
        });

        caso("Al empezar no hay ninguna zona registrada", () ->
                iguales(Map.of(), sala("a69", enJuego("a69", 2)).zonas()));

        caso("Se registra en qué ronda completó la zona, y todos lo ven", () -> {
            String codigo = enJuego("a70", 2);
            jugar("a70", codigo);
            jugar("b70", codigo);
            completar("a70", codigo, 3);
            iguales(Map.of(3, Map.of("a70", 2)), sala("b70", codigo).zonas());
        });

        caso("El registro guarda el orden de finalización por rondas", () -> {
            String codigo = enJuegoDeTres("a71", "b71", "c71");
            completar("a71", codigo, 3);
            jugar("b71", codigo);
            jugar("c71", codigo);
            completar("b71", codigo, 3);
            jugar("a71", codigo);
            jugar("c71", codigo);
            completar("c71", codigo, 3);
            iguales(Map.of(3, Map.of("a71", 1, "b71", 2, "c71", 3)), sala("a71", codigo).zonas());
        });

        caso("Quienes completan la zona en la misma ronda quedan empatados", () -> {
            String codigo = enJuegoDeTres("a72", "b72", "c72");
            completar("c72", codigo, 3);
            completar("a72", codigo, 3);
            iguales(Map.of(3, Map.of("a72", 1, "c72", 1)), sala("b72", codigo).zonas());
        });

        caso("Cada zona tiene su propio registro", () -> {
            String codigo = enJuego("a73", 2);
            completar("a73", codigo, 3);
            completar("b73", codigo, 5);
            iguales(Map.of(3, Map.of("a73", 1), 5, Map.of("b73", 1)), sala("a73", codigo).zonas());
        });

        caso("Una zona ya completada conserva su primera ronda", () -> {
            String codigo = enJuego("a74", 2);
            completar("a74", codigo, 3);
            jugar("b74", codigo);
            completar("a74", codigo, 3);
            iguales(Map.of(3, Map.of("a74", 1)), sala("a74", codigo).zonas());
        });

        caso("No se pueden declarar dos zonas en la misma ronda", () -> {
            String codigo = enJuego("a75", 2);
            completar("a75", codigo, 3);
            completar("a75", codigo, 5);
            iguales(Map.of(3, Map.of("a75", 1)), sala("b75", codigo).zonas());
        });

        caso("Una zona avisada para otra ronda no se registra", () -> {
            String codigo = enJuego("a76", 2);
            SalasMultijugador.terminarTurno("a76", codigo, 7, false, 3);
            iguales(Map.of(), sala("a76", codigo).zonas());
        });

        caso("Quien no está en la partida no registra zonas", () -> {
            String codigo = enJuego("a77", 2);
            cierto(!SalasMultijugador.terminarTurno("intruso", codigo, 1, false, 3).exito(), "aceptó al intruso");
            iguales(Map.of(), sala("a77", codigo).zonas());
        });

        caso("Una zona que no existe se rechaza y el turno no cuenta", () -> {
            String codigo = enJuego("a78", 2);
            for (int zona : List.of(0, -1, 50)) {
                iguales("Esa zona no existe.", SalasMultijugador.terminarTurno("a78", codigo, 1, false, zona).mensaje());
            }
            iguales(List.of(), sala("a78", codigo).jugaron());
            iguales(Map.of(), sala("a78", codigo).zonas());
        });

        caso("Si un jugador sale, su lugar queda ocupado", () -> {
            String codigo = enJuegoDeTres("a79", "b79", "c79");
            completar("b79", codigo, 3);
            jugar("a79", codigo);
            jugar("c79", codigo);
            SalasMultijugador.salir("b79", codigo);
            completar("c79", codigo, 3);
            iguales(Map.of(3, Map.of("b79", 1, "c79", 2)), sala("a79", codigo).zonas());
        });

        caso("Si el anfitrión sale, su lugar también queda ocupado", () -> {
            String codigo = enJuego("a80", 2);
            completar("a80", codigo, 3);
            SalasMultijugador.salir("a80", codigo);
            iguales(Map.of(3, Map.of("a80", 1)), sala("b80", codigo).zonas());
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
