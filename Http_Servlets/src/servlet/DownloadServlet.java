package servlet;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/* Описание (2026): сервлет, который отдает файл на скачивание - GET /download возвращает json_example.json.
 * Маппинг задан аннотацией @WebServlet (в отличие от FirstServlet, у которого маппинг в web.xml) -
 * Tomcat при старте сам сканирует классы приложения и находит такие аннотации.
 * Заголовок Content-Disposition: attachment говорит браузеру "не показывай, а сохрани как файл
 * filename.json". Файл берется не с диска по пути, а из classpath приложения (WEB-INF/classes) через
 * ClassLoader - так ресурс находится одинаково в Eclipse, в собранном WAR и в любом Tomcat. */
@WebServlet("/download")
public class DownloadServlet extends HttpServlet {

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		// 2026: getClassLoader() здесь - загрузчик классов веб-приложения (видит WEB-INF/classes).
		// Сборка из archive.war 2021 года использовала ClassLoader.getSystemResourceAsStream - системный
		// загрузчик Tomcat этот файл не видит, и /download отдавал пустой ответ
		InputStream stream = DownloadServlet.class.getClassLoader().getResourceAsStream("json_example.json");
		// fix 2026: если ресурса нет в classpath, getResourceAsStream возвращает null - было NullPointerException
		// и ответ 500; теперь честный 404
		if (stream == null) {
			resp.sendError(HttpServletResponse.SC_NOT_FOUND, "json_example.json is not in the classpath");
			return;
		}
		resp.setHeader("Content-Disposition", "attachment; filename=\"filename.json\"");
		resp.setContentType("application/json");
		resp.setCharacterEncoding(StandardCharsets.UTF_8.name());

		try(ServletOutputStream outputStream = resp.getOutputStream();
			InputStream input = stream){
			outputStream.write(input.readAllBytes());
		}
	}

}
