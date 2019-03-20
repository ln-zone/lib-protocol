package lnzone.lib.p2p;

import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bittech.lib.protocol.Node;
import bittech.lib.utils.Config;
import bittech.lib.utils.Crypto;
import bittech.lib.utils.exceptions.StoredException;
import bittech.lib.utils.json.RawJson;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class SecurityTests extends TestCase {

	public SecurityTests(String testName) {
		super(testName);
	}

	/**
	 * @return the suite of tests being tested
	 */
	public static Test suite() {
		return new TestSuite(SecurityTests.class);
	}

	public void testWrongKey() throws Exception {

		System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "debug");

		Config.loadEmptyConfig();
		Config.getInstance().addEntry("connectionKeys", new RawJson(Crypto.generateKeys()));

		final Logger LOGGER = LoggerFactory.getLogger(this.getClass());

		LOGGER.debug("Test started");

		try (Node client = new Node("client"); Node server = new Node("server", 1772)) {
			try {
				client.connect("server", "localhost", 1772,
						"gbodadigbevimseg64gqcaibauaagsyagbeaeqiavtlivp744mazg5krvydoncbdn4p3fhvl3zqmnpm45acex3vs67xnif2vlzznvl2ipo5fj5hjrsn3222sqyscdjcuqagzzzc2wgyuhnicamaqaai");
				Assert.fail("No exception thrown for connection with public key not belonging to destinantion node");
			} catch (StoredException ex) {
				Assert.assertTrue(ex.getMessage().contains("Cannot connect"));
			}

		}
	}

	public void testFakeKey() throws Exception {

		System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "debug");

		Config.loadEmptyConfig();
		Config.getInstance().addEntry("connectionKeys", new RawJson(Crypto.generateKeys()));

		final Logger LOGGER = LoggerFactory.getLogger(this.getClass());

		LOGGER.debug("Test started");

		try (Node client = new Node("client"); Node server = new Node("server", 1772)) {

			client.connect("server", "localhost", 1772, "fake pubkey");
			Assert.fail("No exception thrown for connection with incorrect public key");
		} catch (StoredException ex) {
			Assert.assertTrue(ex.getMessage().contains("Cannot connect"));
		}

	}

}
