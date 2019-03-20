package bittech.lib.protocol;

import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bittech.lib.protocol.Node;
import bittech.lib.utils.Config;
import bittech.lib.utils.Crypto;
import bittech.lib.utils.json.RawJson;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class MultithreadingTest extends TestCase {

	public MultithreadingTest(String testName) {
		super(testName);
		Config.loadEmptyConfig();
		Config.getInstance().addEntry("connectionKeys", new RawJson(Crypto.generateKeys()));
	}

	/**
	 * @return the suite of tests being tested
	 */
	public static Test suite() {
		return new TestSuite(MultithreadingTest.class);
	}

	private class WorkerThread extends Thread {

		// private final Logger LOGGER = LoggerFactory.getLogger(this.getClass());

		final int port;
		final String peerName;

		public WorkerThread(int port, String peerName) {
			this.port = port;
			this.peerName = peerName;
		}

		@Override
		public void run() {
			Node peer = new Node(peerName);

			try {
				peer.connect("nomatter", "localhost", port);
				peer.close();
			} catch (Exception e) {
				e.printStackTrace();
			}

		}
	}

	public void testMultithreading() throws Exception {
		System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "info");
		final Logger LOGGER = LoggerFactory.getLogger(this.getClass());
		LOGGER.debug("Start tests");

		Node srv = new Node("srv", 1772);

		List<WorkerThread> threads = new LinkedList<WorkerThread>();
		for (int i = 1; i < 100; i++) {
			WorkerThread th = new WorkerThread(1772, "peer" + i);
			threads.add(th);
		}

		LOGGER.debug("JOINING THREADS");
		for (WorkerThread th : threads) {
			th.start();
		}

		LOGGER.debug("JOINING THREAD");
		for (WorkerThread th : threads) {
			th.join();
		}

		srv.close();

	}

}
