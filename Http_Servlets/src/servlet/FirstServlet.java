package servlet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.stream.Stream;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

//@WebServlet("/first")
//Можно ставить аннотацию и не писать маппинг вручную в xml документе
public class FirstServlet extends HttpServlet{

	@Override
	public void init(ServletConfig config) throws ServletException {
		config.getServletContext();
		super.init(config);
	}
	
	@Override
	public void destroy() {
		// TODO Auto-generated method stub
		super.destroy();
	}
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
//		req.getHeader("user-agent");
//		Enumeration<String> headers = req.getHeaderNames();
//		while (headers.hasMoreElements()) {
//			String particularHeader = headers.nextElement();
//			System.out.println(req.getHeader(particularHeader));
//		}
		final String paramValue = req.getParameter("param");
		/*Все возсожные параметры, которе мы передаем на наш сервлет*/
		var paremeterMap = req.getParameterMap();
		resp.setContentType("text/html");
		resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
		/*Записываем нашу страничку в выходной поток*/
		//resp.getOutputStream()
		/*Но, если мы не хотим работать с байтами, а хотим работать со строкми, тогда пишем:*/
		try(PrintWriter writer = resp.getWriter()){
			/*Как видно, теги не пишутся, потому что браузер автоматически преобразует, то 
			 *тело, которое ему вернули, в html страничку*/
			writer.write("Hello from First Servlet");
		}
		
	}
	
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
//		var parametrsMap = req.getParameterMap();
//		System.out.println(parametrsMap);
		try(BufferedReader reader = req.getReader();
			Stream<String> lines = reader.lines()) {
			lines.forEach(System.out::println);
		}
	}
	
	
	
	
	
	
	
	
}
