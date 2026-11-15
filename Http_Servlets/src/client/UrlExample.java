package client;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/* Описание (2026): самый старый способ сделать HTTP-запрос в Java - java.net.URL + URLConnection (с Java 1.0).
 * URL сам выбирает обработчик по протоколу: для https:// - HttpsURLConnection, для file: - чтение файла.
 * Примеры выбираются аргументом:
 *   get  (по умолчанию) - GET https://www.google.com: заголовки ответа и начало тела;
 *   post [url]          - POST json_example.json на свой сервер (сначала запустить server.HttpServerRunner);
 *   file                - тот же URLConnection читает локальный файл по URL "file:...".
 * Неудобства URLConnection (одно соединение = один запрос, setDoOutput до соединения, ручная работа
 * с потоками) и привели к появлению java.net.http.HttpClient в Java 11 - см. HttpClientExample. */
public class UrlExample {
	public static void main(String[] args) throws IOException {
		// 2026: раньше все шаги шли подряд и программа падала на POST; теперь каждый шаг - отдельный пример
		String example = args.length > 0 ? args[0] : "get";
		switch (example) {
			case "get":
				get();
				break;
			case "post":
				post(args.length > 1 ? args[1] : "http://localhost:9000");
				break;
			case "file":
				file();
				break;
			default:
				System.out.println("Unknown example: " + example + " (expected: get, post [url], file)");
				System.exit(1);
		}
	}

	private static void get() throws IOException {
		/*в "урл указываем протокол + доменное имя"*/
		URL url = new URL("https://www.google.com");

		/*Открываем соединение к нашему серверу, который указали в кач-ве конструктора в на-
		 *шем объекте URL.*/
		URLConnection urlConnection = url.openConnection();
		/*Получаем заголовки*/
		// 2026: getHeaderFields() сам устанавливает соединение и отправляет GET-запрос
		System.out.println(urlConnection.getHeaderFields());
		/*Получение тела*/
		// fix 2026: поток тела открывался, но не читался, а getContent().toString() печатал не текст,
		// а имя объекта-потока (HttpInputStream@...). Читаем тело явно и печатаем начало - страница Google большая
		try (InputStream body = urlConnection.getInputStream()) {
			String text = new String(body.readAllBytes(), StandardCharsets.UTF_8);
			System.out.println(text.substring(0, Math.min(300, text.length())) + "...");
		}
	}

	private static void post(String url) throws IOException {
		/*Работа с "пост" запросами, т.е. отправка каких-то данных серверу*/
		// fix 2026: setDoOutput(true) вызывался после getHeaderFields(), когда соединение уже установлено
		// (IllegalStateException: Already connected), а write(null) - гарантированный NullPointerException.
		// Google на POST отвечает 405, поэтому данные (json_example.json) отправляются на свой сервер
		URLConnection urlConnection = new URL(url).openConnection();
		// 2026: setDoOutput(true) - "у запроса будет тело"; для HTTP это заодно меняет метод GET на POST
		urlConnection.setDoOutput(true);
		urlConnection.setRequestProperty("content-type", "application/json");
		try(OutputStream outputStream = urlConnection.getOutputStream()) {
			outputStream.write(Files.readAllBytes(Path.of("src", "resources", "json_example.json")));
		}
		System.out.println(urlConnection.getHeaderFields());
		try (InputStream body = urlConnection.getInputStream()) {
			System.out.println(new String(body.readAllBytes(), StandardCharsets.UTF_8));
		}
	}

	private static void file() throws IOException {
		/*Работа с файлами на компьютере*/
		// fix 2026: код был закомментирован и указывал на абсолютный путь C:\workspace\Http-Servlets\...
		// к файлу socket/DatagramRunner.java, которого нет в репозитории. Берем файл проекта по
		// относительному пути (запуск из папки Http_Servlets)
		URL fileChecker = new URL("file:src/socket/HttpServerRunner.java");
		URLConnection openedConnection = fileChecker.openConnection();
		System.out.println(new String(openedConnection.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
	}
}
