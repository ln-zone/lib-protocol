package bittech.lib.protocol;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.UnknownHostException;

import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

import bittech.lib.protocol.ConnectionListener;
import bittech.lib.protocol.ErrorResponse;
import bittech.lib.protocol.Message;
import bittech.lib.protocol.Node;
import bittech.lib.protocol.Request;
import bittech.lib.protocol.commands.PingCommand;
import bittech.lib.utils.Config;
import bittech.lib.utils.Crypto;
import bittech.lib.utils.Require;
import bittech.lib.utils.exceptions.StoredException;
import bittech.lib.utils.json.JsonBuilder;
import bittech.lib.utils.json.RawJson;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class BasicTests extends TestCase {

	private static final Logger LOGGER = LoggerFactory.getLogger(BasicTests.class);

	private final class ListenedInfo {
		public String clientListened;
		public String serverListened;
		public String clientDisconnected;
		public String serverDisconnected;
	}

	public BasicTests(String testName) {
		super(testName);
		Config.loadEmptyConfig();
		Config.getInstance().addEntry("connectionKeys", new RawJson(Crypto.generateKeys()));
	}

	/**
	 * @return the suite of tests being tested
	 */
	public static Test suite() {
		return new TestSuite(BasicTests.class);
	}

	public void testConnection() throws Exception {

		System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "debug");

		final Logger LOGGER = LoggerFactory.getLogger(this.getClass());

		LOGGER.debug("Test started");

		final ListenedInfo listenedInfo = new ListenedInfo();

		try (Node client = new Node("client"); Node server = new Node("server", 1772)) {

			client.registerConnectionListener(new ConnectionListener() {
				@Override
				public void onConnected(String peerName) {
					listenedInfo.clientListened = peerName;
				}

				@Override
				public void onDisconnected(String peerName) throws StoredException {
					listenedInfo.clientDisconnected = Require.notNull(peerName, "peerName");
				}

			});

			server.registerConnectionListener(new ConnectionListener() {
				@Override
				public void onConnected(String peerName) {
					listenedInfo.serverListened = peerName;
				}

				@Override
				public void onDisconnected(String peerName) throws StoredException {
					listenedInfo.serverDisconnected = Require.notNull(peerName, "peerName");
				}

			});

			client.connect("server", "localhost", 1772);
		}

		Assert.assertEquals("Incorrect server listened", "client", listenedInfo.serverListened);
		Assert.assertEquals("Incorrect client listened", "server", listenedInfo.clientListened);
		Assert.assertEquals("Incorrect server disconnected", "client", listenedInfo.serverDisconnected);
		Assert.assertEquals("Incorrect client disconnected", "server", listenedInfo.clientDisconnected);

	}

	public void testCommands() throws Exception {

		System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "debug");

		final Logger LOGGER = LoggerFactory.getLogger(this.getClass());

		LOGGER.debug("Test started");

		try (Node peer1 = new Node("peer1"); Node peer2 = new Node("peer2", 1772)) {

			peer1.connect("peer2", "localhost", 1772);

			peer1.registerListener(new PingListener(0, "A dupa tam"));

			PingCommand pingCommand = new PingCommand("Puk puk");
			peer2.execute("peer1", pingCommand);

			LOGGER.debug(pingCommand.toString());

			Assert.assertEquals("Incorrect ping response", pingCommand.response.message, "A dupa tam");

		}

	}

	public void testToSelf() {
		System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "info");

		final Logger LOGGER = LoggerFactory.getLogger(this.getClass());

		LOGGER.debug("Test started");

		try (Node peer1 = new Node("peer1", 1772)) {

			peer1.connect("peer1", "localhost", 1772);

			// LOGGER.info("");
			PingListener pl = new PingListener(0, "Kto tam?");
			peer1.registerListener(pl);
			PingCommand pingCommand = new PingCommand("Puk puk");
			peer1.execute("peer1", pingCommand);
			Assert.assertEquals("Incorrect ping response", pingCommand.response.message, "Kto tam?");
			peer1.unregisterListener(pl);

		}
	}

	public void testCommandsMassive() throws Exception {

		System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "info");

		final Logger LOGGER = LoggerFactory.getLogger(this.getClass());

		LOGGER.debug("Test started");

		try (Node peer1 = new Node("peer1"); Node peer2 = new Node("peer2", 1772)) {

			peer1.connect("peer2", "localhost", 1772);

			for (int i = 0; i < 10; i++) {
				LOGGER.info("" + i);
				PingListener pl = new PingListener(0, "" + i);
				peer1.registerListener(pl);
				PingCommand pingCommand = new PingCommand("Puk puk");
				peer2.execute("peer1", pingCommand);
				Assert.assertEquals("Incorrect ping response", pingCommand.response.message, "" + i);
				peer1.unregisterListener(pl);
			}
		}
	}

	private void throwAbc() {
		throw new RuntimeException("Wywaloło błęda!");
	}

	public void testExceptionJson() throws Exception {

		System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "info");

		try {
			throwAbc();
		} catch (Exception ex) {
			Exception ex2 = new Exception("Nie dziala kurka blaszka", ex);
			long milisec = System.currentTimeMillis();
			ex2.printStackTrace(new PrintStream(new OutputStream() {
				@Override
				public void write(int b) {
				}
			}));
			LOGGER.debug("How long print took?: " + (System.currentTimeMillis() - milisec));
			milisec = System.currentTimeMillis();
			Gson gson = JsonBuilder.build();
			String g = gson.toJson(ex2);
			LOGGER.debug("How long json took?: " + (System.currentTimeMillis() - milisec));
			LOGGER.debug(g);
		}

	}

	public void testError() throws UnknownHostException, IOException, Exception {

		System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "debug");

		final Logger LOGGER = LoggerFactory.getLogger(this.getClass());

		LOGGER.debug("Test started");

		Node peer1 = new Node("peer1");

		Node peer2 = new Node("peer2", 1772);
		peer1.connect("peer2", "localhost", 1772);

		ErrorResponse error = new ErrorResponse(-1, "nie dziala", 123L);
		peer1.registerListener(new PingListener(0, error));

		PingCommand pingCommand = new PingCommand("Puk puk");
		peer2.execute("peer1", pingCommand);

		Assert.assertEquals("Incorrect ping response", pingCommand.response, null);
		Assert.assertEquals("Incorrect ping error coe", pingCommand.error.errorCode, error.errorCode);
		Assert.assertEquals("Incorrect ping error description", pingCommand.error.message, error.message);
		Assert.assertEquals("Incorrect ping error json exception", pingCommand.error.exceptionId, error.exceptionId);

		peer1.close();
		peer2.close();

	}

	public void testTimeoutFast() throws Exception {

		System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "info");

		final Logger LOGGER = LoggerFactory.getLogger(this.getClass());

		LOGGER.debug("Test started");

		try (Node peer1 = new Node("peer1"); Node peer2 = new Node("peer2", 1772)) {

			peer1.connect("peer2", "localhost", 1772);

			PingListener pl = new PingListener(5000, "Pong");
			peer1.registerListener(pl);

			{
				PingCommand pingCommand = new PingCommand("Ping");
				pingCommand.setTimeout(1000);
				try {
					peer2.execute("peer1", pingCommand);
					Assert.fail("No exception thrown");
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}

			{
				PingCommand pingCommand = new PingCommand("Ping");
				pingCommand.setTimeout(6000);
				try {
					peer2.execute("peer1", pingCommand);
					Assert.assertEquals("Incorrect ping response", pingCommand.response.message, "Pong");
				} catch (Exception ex) {
					Assert.fail("Exception thrown");
					ex.printStackTrace();
				}
			}

			peer1.unregisterListener(pl);

		}
	}

	public void testTimeoutSlow() throws Exception {

		System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "info");

		final Logger LOGGER = LoggerFactory.getLogger(this.getClass());

		LOGGER.debug("Test started");

		try (Node peer1 = new Node("peer1"); Node peer2 = new Node("peer2", 1772)) {

			peer1.connect("peer2", "localhost", 1772);

			PingListener pl = new PingListener(15000, "Pong");
			peer1.registerListener(pl);

			{
				PingCommand pingCommand = new PingCommand("Ping");
				pingCommand.setTimeout(10000);
				try {
					peer2.execute("peer1", pingCommand);
					Assert.fail("No exception thrown");
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}

			{
				PingCommand pingCommand = new PingCommand("Ping");
				pingCommand.setTimeout(20000);
				try {
					peer2.execute("peer1", pingCommand);
					Assert.assertEquals("Incorrect ping response", pingCommand.response.message, "Pong");
				} catch (Exception ex) {
					Assert.fail("Exception thrown");
					ex.printStackTrace();
				}
			}

			peer1.unregisterListener(pl);

		}
	}

	// public void testMessageWithId() throws Exception {
	//
	// User user = new User();
	// user.name = "Franek";
	// user.age = 12;
	//
	// Message msg = new Message(1234, user);
	//
	// User user2 = (User)msg.recreateObject();
	// Assert.assertEquals(user.name, user2.name);
	// Assert.assertEquals(user.age, user2.age);
	// }
	//
	// public void testMessageWithId2() throws Exception {
	//
	// User user = new User();
	// user.name = "Franek";
	// user.age = 12;
	//
	// Message msg = new Message(1234, user);
	//
	// Gson json = JsonBuilder.build();
	// String str = json.toJson(msg);
	//
	// Message msg2 = json.fromJson(str, Message.class);
	//
	// User user2 = (User)msg2.recreateObject();
	// Assert.assertEquals(user.name, user2.name);
	// Assert.assertEquals(user.age, user2.age);
	// }

	public void testMessageWithId3() throws Exception {

		Gson json = JsonBuilder.build();

		String str = "{\"id\":1583636757601810432,\"senderTimestamp\":0,\"className\":\"btcduke.lib.p2p.commands.IntroduceCommand\",\"rawJson\":{\"type\":\"btcduke.lib.p2p.commands.IntroduceCommand\",\"request\":{\"serviceName\":\"client\",\"peerPubEncryptedName\":\"EKNsoYwumOUb1f0D3jIaO/YKRjDtITzPO9DsXYO+W+xAUxoHhG0pGWAHloMAdFw6faA+7mJi8D3MNSdYhnymyg\\u003d\\u003d\"},\"timeout\":10000}}";

		json.fromJson(str, Message.class);
	}

	public void testGetRequestClass() throws Exception {
		PingCommand pc = new PingCommand("Hello");
		Class<Request> rc = pc.getRequestClass();
		Assert.assertEquals("lnzone.lib.p2p.commands.PingRequest", rc.getCanonicalName());
	}

	public void testGetResponseClass() throws Exception {
		PingCommand pc = new PingCommand("Hello");
		Class<Request> rc = pc.getResponseClass();
		Assert.assertEquals("lnzone.lib.p2p.commands.PingResponse", rc.getCanonicalName());
	}

}
