package socket;

/* Описание (2026): копия server.HttpServerRunner - запускает socket.HttpServer на порту 9000. */
public class HttpServerRunner {
	public static void main(String[] args) {
		HttpServer server = new HttpServer(9000);
		server.run();
	}
}
