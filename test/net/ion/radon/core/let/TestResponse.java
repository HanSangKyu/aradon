package net.ion.radon.core.let;

import static org.junit.Assert.assertEquals;

import java.io.Reader;

import net.ion.framework.util.IOUtil;
import net.ion.radon.client.AradonClient;
import net.ion.radon.client.AradonClientFactory;
import net.ion.radon.client.IAradonRequest;
import net.ion.radon.core.Aradon;
import net.ion.radon.core.RadonAttributeKey;
import net.ion.radon.util.AradonTester;

import org.junit.Test;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.Method;
import org.restlet.engine.header.Header;
import org.restlet.util.Series;

public class TestResponse {

	@Test
	public void call() throws Exception {
		AradonTester at = AradonTester.create().register("", "/test", GetLet.class);
		Aradon aradon = at.getAradon();

		Request request = new Request(Method.GET, "riap://component/test");
		final Response response = new Response(request);

		aradon.handle(request, response);

		assertEquals(200, response.getStatus().getCode());
		assertEquals(GetLet.class.getCanonicalName(), response.getEntityAsText());
		assertEquals(GetLet.class.getCanonicalName(), IOUtil.toString(response.getEntity().getStream()));
	}

	@Test
	public void postEntity() throws Exception {
		AradonTester at = AradonTester.create().register("", "/test", GetLet.class);
		Aradon aradon = at.getAradon();
		aradon.startServer(9000);

		AradonClient ac = AradonClientFactory.create("http://localhost:9000");
		IAradonRequest req = ac.createRequest("/test");
		req.addParameter("name", "bleujin");
		req.addParameter("age", "20");

		Response res = req.handle(Method.POST);
		assertEquals(200, res.getStatus().getCode());
		
		ac.stop() ;
		aradon.stop();
	}

	@Test
	public void responseHeader() throws Exception {
		AradonTester at = AradonTester.create().register("", "/test", GetLet.class);
		Aradon aradon = at.getAradon();

		Request request = new Request(Method.GET, "riap://component/test");
		Response response = aradon.handle(request);

		assertEquals(200, response.getStatus().getCode());
		Series<Header> headers = (Series<Header>) response.getAttributes().get(RadonAttributeKey.ATTRIBUTE_HEADERS);

		assertEquals("0.8", headers.getFirstValue("X-Aradon-Version"));
	}

	@Test
	public void entityAsReader() throws Exception {
		AradonClient ac = AradonClientFactory.create("http://www.i-on.net");
		IAradonRequest ar = ac.createRequest("/index.html");

		Response res = ar.handle(Method.GET);
		Reader reader = new DetectStream(res).getBodyReader();

		assertEquals(true, IOUtil.toString(reader).length() > 100);
		ac.stop() ;
	}

}
