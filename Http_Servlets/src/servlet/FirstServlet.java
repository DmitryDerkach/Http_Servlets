package servlet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/* Описание (2026): первый сервлет курса - обработчик адреса /first (маппинг в WebContent/WEB-INF/web.xml).
 * Сервлет - это класс, которым управляет контейнер (Tomcat): контейнер сам принимает TCP-соединение,
 * разбирает текст HTTP-запроса в объект HttpServletRequest, по URL находит сервлет и вызывает его метод
 * service(), а тот по HTTP-методу - doGet/doPost/... Все, что сервлет запишет в HttpServletResponse,
 * контейнер превратит обратно в текст HTTP-ответа (сравните с ручной работой в server.HttpServer).
 * Жизненный цикл: init() - один раз при первом обращении, затем doGet/doPost на каждый запрос
 * (один и тот же объект на все запросы и потоки!), destroy() - один раз при остановке приложения.
 * Режимы: GET /first, GET /first?param=...&other=... (параметры на странице), GET /first?headers
 * (все заголовки запроса), POST /first (сервлет отвечает тем, что получил). */
//@WebServlet("/first")
//Можно ставить аннотацию и не писать маппинг вручную в xml документе
public class FirstServlet extends HttpServlet{

	@Override
	public void init(ServletConfig config) throws ServletException {
		config.getServletContext();
		super.init(config);
		// 2026: печать в лог Tomcat - видно, что init вызывается один раз, при первом запросе к /first
		System.out.println("FirstServlet.init(): the servlet is created");
	}

	@Override
	public void destroy() {
		// TODO Auto-generated method stub
		// 2026: destroy вызывается один раз - при остановке Tomcat или передеплое приложения
		System.out.println("FirstServlet.destroy(): the servlet is removed");
		super.destroy();
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		final String paramValue = req.getParameter("param");
		/*Все возможные параметры, которые мы передаем на наш сервлет*/
		var parameterMap = req.getParameterMap();
		resp.setContentType("text/html");
		resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
		/*Записываем нашу страничку в выходной поток*/
		//resp.getOutputStream()
		/*Но, если мы не хотим работать с байтами, а хотим работать со строками, тогда пишем:*/
		try(PrintWriter writer = resp.getWriter()){
			/*Как видно, теги не пишутся, потому что браузер автоматически преобразует, то
			 *тело, которое ему вернули, в html страничку*/
			writer.write("Hello from First Servlet");
			// 2026: параметры раньше читались, но никуда не выводились - показываем их на странице
			if (paramValue != null) {
				writer.write("<p>param = " + escape(paramValue) + "</p>");
			}
			if (!parameterMap.isEmpty()) {
				writer.write("<h3>All parameters (getParameterMap)</h3><ul>");
				for (Map.Entry<String, String[]> parameter : parameterMap.entrySet()) {
					writer.write("<li>" + escape(parameter.getKey()) + " = "
							+ escape(Arrays.toString(parameter.getValue())) + "</li>");
				}
				writer.write("</ul>");
			}
			// fix 2026: вывод заголовков был закомментирован - теперь включается параметром ?headers
			// и печатает заголовки в лог Tomcat (как было задумано) и на страницу
			if (parameterMap.containsKey("headers")) {
				req.getHeader("user-agent");
				writer.write("<h3>Request headers (getHeaderNames)</h3><ul>");
				Enumeration<String> headers = req.getHeaderNames();
				while (headers.hasMoreElements()) {
					String particularHeader = headers.nextElement();
					System.out.println(particularHeader + ": " + req.getHeader(particularHeader));
					writer.write("<li><b>" + escape(particularHeader) + "</b>: "
							+ escape(req.getHeader(particularHeader)) + "</li>");
				}
				writer.write("</ul>");
			}
		}

	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		// fix 2026: без явной кодировки тело запроса читается в ISO-8859-1 и кириллица превращается
		// в "ÐŸÑ€Ð¸Ð²ÐµÑ‚". Кодировку нужно задать ДО первого чтения параметров или тела
		req.setCharacterEncoding(StandardCharsets.UTF_8.name());
		// fix 2026: код был закомментирован; println(map) печатал массивы как [Ljava.lang.String;@1b2c3d
		var parametersMap = req.getParameterMap();
		System.out.println(format(parametersMap));
		List<String> body;
		try(BufferedReader reader = req.getReader();
			Stream<String> lines = reader.lines()) {
			body = lines.collect(Collectors.toList());
		}
		body.forEach(System.out::println);
		// 2026: раньше ответ был пустым (200 без тела) - отвечаем тем, что получили
		resp.setContentType("text/plain");
		resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
		try (PrintWriter writer = resp.getWriter()) {
			writer.println("FirstServlet.doPost() received");
			writer.println("parameters: " + format(parametersMap));
			writer.println("body (" + body.size() + " lines):");
			body.forEach(writer::println);
		}
	}

	// 2026: Map<String, String[]> в читаемом виде: {name=[value1, value2]}
	private static String format(Map<String, String[]> parameters) {
		return parameters.entrySet().stream()
				.map(parameter -> parameter.getKey() + "=" + Arrays.toString(parameter.getValue()))
				.collect(Collectors.joining(", ", "{", "}"));
	}

	// 2026: значения из запроса нельзя вставлять в HTML как есть - иначе ?param=<script>... выполнится
	// в браузере (XSS). Экранируем спецсимволы HTML
	private static String escape(String value) {
		return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}








}
