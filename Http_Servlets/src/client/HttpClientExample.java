package client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Version;

public class HttpClientExample {
	public static void main(String[] args) throws IOException, InterruptedException {
		/*Базовая конфигурация*/
		HttpClient.newHttpClient();
		/*Настройка клиента*/
		HttpClient httpClient = HttpClient.newBuilder()
			.version(Version.HTTP_1_1)
			.build();
		/*Отправка запроса и получение ответа*/
		HttpRequest request = HttpRequest.newBuilder(URI.create("https://www.google.com"))
				.GET()
				//.POST(BodyPublishers.ofFile(Path.of("/*путь к файлу*/")))
				.build();
		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		System.out.println(response.headers());
		System.out.println("---------------");
		System.out.println(response.body());
	}
}
