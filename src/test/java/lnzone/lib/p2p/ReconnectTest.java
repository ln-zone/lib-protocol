package lnzone.lib.p2p;

import org.junit.Assert;
import org.junit.Ignore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bittech.lib.protocol.Node;
import bittech.lib.utils.Config;
import bittech.lib.utils.Crypto;
import bittech.lib.utils.Utils;
import bittech.lib.utils.json.RawJson;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import lnzone.lib.p2p.commands.PingCommand;

@Ignore // TODO: Unignore and fix
public class ReconnectTest extends TestCase {

	public ReconnectTest(String testName) {
		super(testName);
		Config.loadEmptyConfig();
		Config.getInstance().addEntry("connectionKeys", new RawJson(Crypto.generateKeys()));
		Config.getInstance().addEntry("printExceptions", new RawJson(true));

	}

	/**
	 * @return the suite of tests being tested
	 */
	public static Test suite() {
		return new TestSuite(ReconnectTest.class);
	}

	public void testReconnect() throws Exception {

		System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "debug");

		final Logger LOGGER = LoggerFactory.getLogger(this.getClass());

		LOGGER.debug("Test started");

		try (Node client = new Node("client")) {

			client.connectAsync("server", "localhost", 1772);

			{

				Node server = new Node("server", 1772);
				LOGGER.debug("----------------------------<>--------------------");

				PingListener pl = new PingListener(0, "ok");
				server.registerListener(pl);
				// Thread.sleep(3000);
				PingCommand pingCommand = new PingCommand("Puk puk");
				while (true) {
					try {
						LOGGER.info("------------------------------------------------");
						client.execute("server", pingCommand);
						break;
					} catch (Exception ex) {
						ex.printStackTrace();
					}
				}
				Assert.assertEquals("ok", pingCommand.response.message);

				try {
					server.close();
				} catch (Exception ex) {
					// do nothing
				}
			}

			{
				Node server = new Node("server", 1772);

				PingListener pl = new PingListener(0, "ok");
				server.registerListener(pl);
				PingCommand pingCommand = new PingCommand("Puk puk");
				while (true) {
					try {
						client.execute("server", pingCommand);
						break;
					} catch (Exception ex) {
						// do nothing
					}
				}
				Assert.assertEquals("ok", pingCommand.response.message);

				try {
					server.close();
				} catch (Exception ex) {
					// do nothing
				}
			}

		}

	}

	public void testReconnect2() throws Exception {

		System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "info");

		final Logger LOGGER = LoggerFactory.getLogger(this.getClass());

		LOGGER.debug("Test started");

		try (Node client = new Node("client")) {

			client.connectAsync("server", "localhost", 1772);

			{

				PingListener pl = new PingListener(0, "ok");
				client.registerListener(pl);

				LOGGER.info("---------------------------------");

				Node server = new Node("server", 1772);

				PingCommand pingCommand = new PingCommand("Puk puk");
				
				while (true) {
					try {
						server.execute("client", pingCommand);
						break;
					} catch (Exception ex) {
						ex.printStackTrace();
					}
				}
				
				Utils.prn(pingCommand);
				Assert.assertEquals("ok", pingCommand.response.message);

				try {
					server.close();
				} catch (Exception ex) {
					// do nothing
				}
			}

			{
				Node server = new Node("server", 1772);

				PingCommand pingCommand = new PingCommand("Puk puk");
				while (true) {
					try {
						server.execute("client", pingCommand);
						break;
					} catch (Exception ex) {
						// do nothing
					}
				}
				Assert.assertEquals("ok", pingCommand.response.message);

				try {
					server.close();
				} catch (Exception ex) {
					// do nothing
				}
			}

		}

	}

	public void testReconnectNoSleep() throws Exception {

		System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "info");

		final Logger LOGGER = LoggerFactory.getLogger(this.getClass());

		LOGGER.debug("Test started");

		try (Node client = new Node("client")) {

			client.connectAsync("server", "localhost", 1772);

			try (Node server = new Node("server", 1772)) {

				Thread.sleep(2000);

				PingListener pl = new PingListener(0, "ok");
				server.registerListener(pl);
				PingCommand pingCommand = new PingCommand("Puk puk");
				client.execute("server", pingCommand);
				Assert.assertEquals("ok", pingCommand.response.message);

			}

			Thread.sleep(1000);

			try (Node server = new Node("server", 1772)) {

				Thread.sleep(2000);

				PingListener pl = new PingListener(0, "ok");
				server.registerListener(pl);
				PingCommand pingCommand = new PingCommand("Puk puk");
				client.execute("server", pingCommand);
				Assert.assertEquals("ok", pingCommand.response.message);

			}

		}

	}

}
