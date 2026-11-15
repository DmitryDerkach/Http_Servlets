package client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import server.HttpServerTest;

/* Описание (2026): тесты клиентских примеров без интернета - POST идет на свой server.HttpServer,
 * запущенный на свободном порту. Примеры "get" ходят в Google и проверяются запуском ./run.sh client. */
@Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD) // the hand-made server answers one connection at a time: a stuck client must fail the test, not hang
class ClientExamplesTest {

	private String serverUrl;

	private final ByteArrayOutputStream output = new ByteArrayOutputStream();
	private PrintStream originalOut;

	@BeforeEach
	void startServerAndCaptureOutput() throws Exception {
		// a server per test: the hand-made server serves one connection at a time, so a client stuck
		// in one test must not block the next ones
		serverUrl = "http://localhost:" + HttpServerTest.start(port -> new server.HttpServer(port).run());
		originalOut = System.out;
		System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
	}

	@AfterEach
	void restoreOutput() {
		System.setOut(originalOut);
	}

	private String printed() {
		return output.toString(StandardCharsets.UTF_8);
	}

	@Test
	void urlConnectionPostsJson() throws Exception {
		// before the fix: IllegalStateException: Already connected, then write(null)
		UrlExample.main(new String[] {"post", serverUrl});
		assertTrue(printed().contains("<h1>Hello World</h1>"), printed());
		assertTrue(printed().contains("Request: POST / HTTP/1.1"), printed());
		assertTrue(printed().contains("\"GlossSee\": \"markup\""), printed());
	}

	@Test
	void urlConnectionReadsLocalFile() throws Exception {
		// the restored example: a file: URL relative to the project folder
		UrlExample.main(new String[] {"file"});
		assertTrue(printed().contains("public class HttpServerRunner"), printed());
		assertTrue(printed().contains("Описание (2026)"), printed());
	}

	@Test
	void httpClientPostsFile() throws Exception {
		// the restored example: .POST(BodyPublishers.ofFile(...))
		HttpClientExample.main(new String[] {"post", serverUrl});
		assertTrue(printed().contains("content-length=[96]"), printed());
		assertTrue(printed().contains("<h1>Hello World</h1>"), printed());
		assertTrue(printed().contains("\"GlossSee\": \"markup\""), printed());
	}
}
