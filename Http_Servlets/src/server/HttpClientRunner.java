package server;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;

/* Описание (2026): клиент для своего сервера (server.HttpServerRunner должен быть запущен).
 * Через java.net.http.HttpClient отправляет POST на http://localhost:9000 с телом из файла
 * src/resources/json_example.json и печатает заголовки и тело ответа. Сервер при этом печатает
 * в свою консоль полученный запрос - видно, во что HttpClient превращает билдер: в текст HTTP. */
public class HttpClientRunner {
	public static void main(String[] args) throws IOException, InterruptedException {
		HttpClient httpClient = HttpClient.newBuilder()
				.version(Version.HTTP_1_1)
				.build();
		
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create("http://localhost:9000"))
				.header("content-type", "application/json")
				//.header("accept", "text/html")
				.POST(HttpRequest.BodyPublishers.ofFile(Path.of("src", "resources", "json_example.json")))
				.build();
		HttpResponse<String> response = 
				httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		
		System.out.println(response.headers());
		System.out.println(response.body());
	}
}
