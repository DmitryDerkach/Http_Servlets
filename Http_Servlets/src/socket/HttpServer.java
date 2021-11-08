package socket;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;

public class HttpServer {
	private final int port;
	
	public HttpServer(int port) {
		this.port = port;
	}
	
	public void run() {
		try {
			ServerSocket server = new ServerSocket(port);
			Socket socket = server.accept();
			/*Обработка соединения*/
			processSocket(socket);
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
			System.out.println("Request: " + new String(inputStream.readNBytes(400)));
			//step 2 handle response
			byte[] body = Files.readAllBytes(Path.of("src", "resources", "html_example.html"));
			/*creating headers*/
			byte[] headers = String.format("HTTP/1.1 200 OK\n"
					+ "content-type: text/html\n"
					+ "content-length: %d\n", body.length).getBytes();
			outputStream.write(headers);
			/*Заголовок и тело отделяются пустой строкой*/
			outputStream.write(System.lineSeparator().getBytes());
			outputStream.write(body);
		} catch (IOException e) {
			//log error message
			e.printStackTrace();
		}
	}
}
 