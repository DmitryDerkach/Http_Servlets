package server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.function.IntConsumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/* Описание (2026): тесты своего HTTP-сервера - и server.HttpServer, и его копии socket.HttpServer.
 * Каждый тест запускает сервер на свободном порту в фоновом потоке и общается с ним "сырым" сокетом:
 * так видны байты ответа (\r\n) и зависание (таймаут чтения 5 секунд вместо вечного ожидания). */
@Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD) // a hanging server must fail the test, not stop the build
public class HttpServerTest {

	private static final int TIMEOUT_MS = 5_000;

	private final ByteArrayOutputStream serverOutput = new ByteArrayOutputStream();
	private PrintStream originalOut;

	static Stream<Arguments> servers() {
		IntConsumer serverPackage = port -> new server.HttpServer(port).run();
		IntConsumer socketPackage = port -> new socket.HttpServer(port).run();
		return Stream.of(Arguments.of("server.HttpServer", serverPackage),
				Arguments.of("socket.HttpServer", socketPackage));
	}

	@BeforeEach
	void captureServerOutput() {
		originalOut = System.out;
		System.setOut(new PrintStream(serverOutput, true, StandardCharsets.UTF_8));
	}

	@AfterEach
	void restoreOutput() {
		System.setOut(originalOut);
	}

	/** Starts the server in a daemon thread on a free port and waits until it accepts connections. */
	public static int start(IntConsumer server) throws Exception {
		int port;
		try (ServerSocket probe = new ServerSocket(0)) {
			port = probe.getLocalPort();
		}
		int serverPort = port;
		Thread thread = new Thread(() -> server.accept(serverPort), "http-server-" + port);
		thread.setDaemon(true);
		thread.start();
		for (int attempt = 0; attempt < 100; attempt++) {
			try {
				// a complete request (not just a connect), so the server doesn't write into a closed socket
				exchange(port, "GET / HTTP/1.1\r\nHost: localhost\r\n\r\n");
				return port;
			} catch (ConnectException notYet) {
				// only "connection refused" means "not started yet"; a read timeout means the server hangs
				Thread.sleep(50);
			}
		}
		throw new IllegalStateException("the server did not start on port " + port);
	}

	/** Sends raw bytes and reads the whole response (the server closes the connection after answering). */
	static String exchange(int port, String request) throws IOException {
		try (Socket socket = new Socket("localhost", port)) {
			socket.setSoTimeout(TIMEOUT_MS);
			OutputStream out = socket.getOutputStream();
			out.write(request.getBytes(StandardCharsets.UTF_8));
			out.flush();
			InputStream in = socket.getInputStream();
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("servers")
	void answersShortRequestWithoutHanging(String name, IntConsumer server) throws Exception {
		// curl sends about 80 bytes; before the fix readNBytes(400) waited for 400 bytes forever
		int port = start(server);
		String response = exchange(port, "GET / HTTP/1.1\r\nHost: localhost\r\n\r\n");
		assertTrue(response.startsWith("HTTP/1.1 200 OK\r\n"), response);
		assertTrue(response.contains("<h1>Hello World</h1>"), response);
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("servers")
	void usesCrLfBetweenHeadersAndBeforeBody(String name, IntConsumer server) throws Exception {
		int port = start(server);
		String response = exchange(port, "GET / HTTP/1.1\r\nHost: localhost\r\n\r\n");
		int headEnd = response.indexOf("\r\n\r\n");
		assertTrue(headEnd > 0, "no empty line (\\r\\n\\r\\n) between headers and body: " + response);
		String head = response.substring(0, headEnd + 2);
		assertEquals(head.split("\n", -1).length, head.split("\r\n", -1).length,
				"every header line must end with \\r\\n: " + head);
		assertTrue(head.toLowerCase().contains("content-length: 96\r\n"), head);
		assertTrue(response.substring(headEnd + 4).startsWith("<!DOCTYPE html>"), response);
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("servers")
	void servesSeveralRequestsInARow(String name, IntConsumer server) throws Exception {
		// before the fix the server answered one connection and stopped
		int port = start(server);
		for (int request = 1; request <= 3; request++) {
			String response = exchange(port, "GET / HTTP/1.1\r\nHost: localhost\r\n\r\n");
			assertTrue(response.startsWith("HTTP/1.1 200 OK"), "request " + request + ": " + response);
		}
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("servers")
	void readsTheWholePostBody(String name, IntConsumer server) throws Exception {
		// the same request as HttpClientRunner: 603 bytes of JSON; before the fix only the first 400 bytes
		// of the request (headers + the beginning of the body) were read
		int port = start(server);
		HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port))
				.header("content-type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofFile(Path.of("src", "resources", "json_example.json")))
				.build();
		HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
		assertEquals(200, response.statusCode());
		assertTrue(response.body().contains("Hello World"));
		String printed = serverOutput.toString(StandardCharsets.UTF_8);
		assertTrue(printed.contains("Request: POST / HTTP/1.1"), printed);
		assertTrue(printed.contains("\"GlossSee\": \"markup\""), "the end of the JSON body is missing: " + printed);
	}
}
