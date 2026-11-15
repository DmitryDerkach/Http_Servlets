package client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.net.http.HttpClient.Version;

/* Описание (2026): современный HTTP-клиент из Java 11 - java.net.http.HttpClient.
 * Три объекта: HttpClient (настройки: версия HTTP, таймауты, редиректы; переиспользуется),
 * HttpRequest (адрес, метод, заголовки, тело - собирается билдером) и HttpResponse
 * (статус, заголовки, тело, превращенное BodyHandler-ом в нужный тип, здесь - в String).
 * Примеры выбираются аргументом:
 *   get  (по умолчанию) - GET https://www.google.com;
 *   post [url]          - POST с телом из файла json_example.json на свой сервер
 *                         (сначала запустить server.HttpServerRunner). */
public class HttpClientExample {
	public static void main(String[] args) throws IOException, InterruptedException {
		// 2026: выбор примера аргументом; post - восстановленный закомментированный вариант с телом из файла
		String example = args.length > 0 ? args[0] : "get";
		/*Базовая конфигурация*/
		HttpClient.newHttpClient();
		/*Настройка клиента*/
		HttpClient httpClient = HttpClient.newBuilder()
			.version(Version.HTTP_1_1)
			.build();
		/*Отправка запроса и получение ответа*/
		HttpRequest request;
		if (example.equals("post")) {
			String url = args.length > 1 ? args[1] : "http://localhost:9000";
			request = HttpRequest.newBuilder(URI.create(url))
					.header("content-type", "application/json")
					// fix 2026: был путь-заглушка "/*путь к файлу*/" - отправляем файл проекта
					.POST(BodyPublishers.ofFile(Path.of("src", "resources", "json_example.json")))
					.build();
		} else if (example.equals("get")) {
			request = HttpRequest.newBuilder(URI.create("https://www.google.com"))
					.GET()
					.build();
		} else {
			System.out.println("Unknown example: " + example + " (expected: get, post [url])");
			System.exit(1);
			return;
		}
		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		System.out.println(response.headers());
		System.out.println("---------------");
		// 2026: страница Google - десятки КБ одной строкой, поэтому печатаем только ее начало
		String body = response.body();
		System.out.println(body.length() > 300 ? body.substring(0, 300) + "..." : body);
	}
}
