package server;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;

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
