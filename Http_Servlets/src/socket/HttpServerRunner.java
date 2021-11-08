package socket;

public class HttpServerRunner {
	public static void main(String[] args) {
		HttpServer server = new HttpServer(9000);
		server.run();
	}
}
