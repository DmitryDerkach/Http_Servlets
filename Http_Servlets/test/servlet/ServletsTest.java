package servlet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.catalina.Context;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.webresources.DirResourceSet;
import org.apache.catalina.webresources.StandardRoot;
import org.apache.tomcat.util.scan.StandardJarScanner;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/* Описание (2026): тесты сервлетов на настоящем Tomcat 9 - встроенном (embedded), том же самом Tomcat,
 * что Spring Boot запускает внутри своего приложения. Приложение собирается как в WAR: WebContent -
 * корень сайта (index.html, WEB-INF/web.xml), а скомпилированные классы и ресурсы (target/classes)
 * подключаются как WEB-INF/classes. Docker для тестов не нужен. */
@Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class ServletsTest {

	private static final ByteArrayOutputStream tomcatOutput = new ByteArrayOutputStream();
	private static PrintStream originalOut;
	private static Tomcat tomcat;
	private static String baseUrl;
	private static final HttpClient client = HttpClient.newHttpClient();

	@BeforeAll
	static void startTomcat() throws Exception {
		// what the servlets print with System.out (init, headers, POST body) goes to the Tomcat log (catalina.out)
		originalOut = System.out;
		System.setOut(new PrintStream(tomcatOutput, true, StandardCharsets.UTF_8));

		tomcat = new Tomcat();
		tomcat.setBaseDir(Files.createDirectories(Path.of("target", "tomcat")).toString());
		tomcat.setPort(0); // any free port
		tomcat.getConnector();
		Context context = tomcat.addWebapp("", new File("WebContent").getAbsolutePath());
		WebResourceRoot resources = new StandardRoot(context);
		resources.addPreResources(new DirResourceSet(resources, "/WEB-INF/classes",
				new File("target/classes").getAbsolutePath(), "/"));
		context.setResources(resources);
		// the test classpath has many jars (JUnit, Tomcat itself): don't scan them for TLDs and annotations
		((StandardJarScanner) context.getJarScanner()).setScanClassPath(false);
		tomcat.start();
		baseUrl = "http://localhost:" + tomcat.getConnector().getLocalPort();
	}

	@AfterAll
	static void stopTomcat() throws Exception {
		tomcat.stop();
		tomcat.destroy();
		System.setOut(originalOut);
	}

	private static HttpResponse<String> get(String path) throws Exception {
		return client.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).build(),
				HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
	}

	private static HttpResponse<String> post(String path, String contentType, String body) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
				.header("Content-Type", contentType)
				.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
				.build();
		return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
	}

	@Test
	void welcomePageIsServed() throws Exception {
		HttpResponse<String> response = get("/");
		assertEquals(200, response.statusCode());
		assertTrue(response.body().contains("<h1>Hello World</h1>"));
	}

	@Test
	void firstServletAnswersGet() throws Exception {
		HttpResponse<String> response = get("/first");
		assertEquals(200, response.statusCode());
		assertEquals("text/html;charset=UTF-8", response.headers().firstValue("Content-Type").orElse(""));
		assertEquals("Hello from First Servlet", response.body());
	}

	@Test
	void firstServletShowsParametersEscaped() throws Exception {
		HttpResponse<String> response = get("/first?param=%3Cb%3Ex%3C%2Fb%3E&city=Minsk&city=Warsaw");
		assertTrue(response.body().contains("<p>param = &lt;b&gt;x&lt;/b&gt;</p>"), response.body());
		assertTrue(response.body().contains("<li>city = [Minsk, Warsaw]</li>"), response.body());
		assertFalse(response.body().contains("<b>x</b>"), "parameters must be HTML-escaped: " + response.body());
	}

	@Test
	void firstServletListsRequestHeaders() throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/first?headers"))
				.header("X-Course", "dmdev").build();
		HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
		assertTrue(response.body().contains("<li><b>x-course</b>: dmdev</li>"), response.body());
		assertTrue(tomcatOutput.toString(StandardCharsets.UTF_8).contains("x-course: dmdev"));
	}

	@Test
	void firstServletReadsCyrillicBodyAsUtf8() throws Exception {
		// application/json without "; charset=": before the fix the body was decoded as ISO-8859-1
		HttpResponse<String> response = post("/first", "application/json", "{\"name\":\"Привет\"}");
		assertEquals(200, response.statusCode());
		assertTrue(response.body().contains("{\"name\":\"Привет\"}"), response.body());
		assertTrue(tomcatOutput.toString(StandardCharsets.UTF_8).contains("{\"name\":\"Привет\"}"));
	}

	@Test
	void firstServletReadsFormParameters() throws Exception {
		String form = "name=" + URLEncoder.encode("Привет, сервлет!", StandardCharsets.UTF_8);
		HttpResponse<String> response = post("/first", "application/x-www-form-urlencoded", form);
		assertTrue(response.body().contains("parameters: {name=[Привет, сервлет!]}"), response.body());
	}

	@Test
	void firstServletIsCreatedOnce() throws Exception {
		get("/first");
		get("/first");
		String log = tomcatOutput.toString(StandardCharsets.UTF_8);
		assertEquals(1, log.split("FirstServlet.init\\(\\)", -1).length - 1, log);
	}

	@Test
	void unsupportedMethodIs405() throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/first"))
				.PUT(HttpRequest.BodyPublishers.noBody()).build();
		assertEquals(405, client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode());
	}

	@Test
	void downloadServletSendsTheJsonFile() throws Exception {
		HttpResponse<byte[]> response = client.send(
				HttpRequest.newBuilder(URI.create(baseUrl + "/download")).build(),
				HttpResponse.BodyHandlers.ofByteArray());
		assertEquals(200, response.statusCode());
		assertEquals("attachment; filename=\"filename.json\"",
				response.headers().firstValue("Content-Disposition").orElse(""));
		assertArrayEquals(Files.readAllBytes(Path.of("src", "resources", "json_example.json")), response.body());
	}

	@Test
	void unknownPathIs404() throws Exception {
		assertEquals(404, get("/missing").statusCode());
	}
}
