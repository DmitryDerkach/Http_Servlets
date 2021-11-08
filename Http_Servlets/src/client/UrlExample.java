package client;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;

public class UrlExample {
	public static void main(String[] args) throws IOException {
		/*в "урл указываем протокол + доменное имя"*/
		URL url = new URL("https://www.google.com");
		
		/*Открываем соединение к нашему серверу, который указали в кач-ве конструктора в на-
		 *шем объекте URL.*/
		URLConnection urlConnection = url.openConnection();
		/*Получаем заголовки*/
		System.out.println(urlConnection.getHeaderFields());
		/*Получение тело*/
		InputStream body = urlConnection.getInputStream();
		/*Работа с "пост" запросами, т.е. отправка каких-то данных нашему клиенту*/
		urlConnection.setDoOutput(true);
		try(OutputStream outputStream = urlConnection.getOutputStream()) {
			outputStream.write(null);
		}
		
		
		System.out.println(urlConnection.getContent().toString());

		/*Работа с файлами на компьютере*/
//		URL fileCheker = new URL("file:C:\\workspace\\Http-Servlets\\src\\socket\\DatagramRunner.java");
//		URLConnection openedConnection = fileCheker.openConnection();
//		System.out.println(new String(openedConnection.getInputStream().readAllBytes()));
		
	}
}
