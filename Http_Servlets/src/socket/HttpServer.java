package socket;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/* Описание (2026): побайтовая копия server.HttpServer - простейшего HTTP-сервера "руками" на ServerSocket.
 * В курсе dmdev пакет socket содержал примеры TCP/UDP-сокетов (например, DatagramRunner из комментария
 * в client.UrlExample), но в репозиторий они не попали - здесь осталась только копия HTTP-сервера.
 * Подробные пояснения - в server.HttpServer. Запуск: socket.HttpServerRunner (порт 9000). */
public class HttpServer {
	private final int port;

	public HttpServer(int port) {
		this.port = port;
	}

	public void run() {
		// fix 2026: ServerSocket закрывается (try-with-resources), а соединения принимаются в цикле -
		// раньше сервер обслуживал ровно один запрос и завершался
		try (ServerSocket server = new ServerSocket(port)) {
			while (true) {
				// 2026: accept() блокирует поток, пока не подключится клиент, и возвращает сокет этого соединения
				Socket socket = server.accept();
				/*Обработка соединения*/
				processSocket(socket);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void processSocket(Socket socket) {
		try(socket;
			InputStream inputStream = new DataInputStream(socket.getInputStream());
			OutputStream outputStream = new DataOutputStream(socket.getOutputStream());
			) {
			//step 1 handle request
			// fix 2026: было readNBytes(400) - метод ждёт ровно 400 байт, поэтому короткий запрос
			// (curl, браузер) "вешал" сервер, а длинный обрезался. Теперь читаем весь запрос
			System.out.println("Request: " + readRequest(inputStream));
			//step 2 handle response
			byte[] body = Files.readAllBytes(Path.of("src", "resources", "html_example.html"));
			/*creating headers*/
			// fix 2026: по спецификации HTTP каждая строка заголовков заканчивается \r\n (было \n)
			byte[] headers = String.format("HTTP/1.1 200 OK\r\n"
					+ "content-type: text/html\r\n"
					+ "content-length: %d\r\n", body.length).getBytes();
			outputStream.write(headers);
			/*Заголовок и тело отделяются пустой строкой*/
			// fix 2026: пустая строка - тоже \r\n (System.lineSeparator() на Linux/macOS дает только \n)
			outputStream.write("\r\n".getBytes());
			outputStream.write(body);
		} catch (IOException e) {
			//log error message
			e.printStackTrace();
		}
	}

	// 2026: чтение HTTP-запроса. Заголовки идут до пустой строки (байты \r\n\r\n), а длину тела
	// сообщает заголовок Content-Length - больше читать нельзя: клиент ждет ответа и сам соединение не закроет
	private String readRequest(InputStream inputStream) throws IOException {
		ByteArrayOutputStream head = new ByteArrayOutputStream();
		int lastFourBytes = 0;
		int b;
		while ((b = inputStream.read()) != -1) {
			head.write(b);
			lastFourBytes = (lastFourBytes << 8) | b;
			if (lastFourBytes == 0x0D0A0D0A) { // \r\n\r\n
				break;
			}
		}
		String headers = head.toString(StandardCharsets.UTF_8);
		int contentLength = 0;
		for (String line : headers.split("\r\n")) {
			if (line.toLowerCase().startsWith("content-length:")) {
				contentLength = Integer.parseInt(line.substring("content-length:".length()).trim());
			}
		}
		byte[] body = inputStream.readNBytes(contentLength);
		return headers + new String(body, StandardCharsets.UTF_8);
	}
}
