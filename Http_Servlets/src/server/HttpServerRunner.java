package server;

/* Описание (2026): запускает свой HTTP-сервер (server.HttpServer) на порту 9000.
 * Сервер работает, пока процесс не остановят (Ctrl+C). Проверить: server.HttpClientRunner,
 * curl http://localhost:9000 или браузер. Запускать из папки Http_Servlets: ответ читается
 * из файла src/resources/html_example.html относительно рабочей папки. */
public class HttpServerRunner {
	public static void main(String[] args) {
		HttpServer server = new HttpServer(9000);
		server.run();
	}
}
