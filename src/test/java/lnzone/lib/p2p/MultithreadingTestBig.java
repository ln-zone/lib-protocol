package lnzone.lib.p2p;

import java.util.LinkedList;
import java.util.List;

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
import lnzone.lib.p2p.commands.PingCommand;

public class MultithreadingTestBig extends TestCase {

	public MultithreadingTestBig(String testName) {
		super(testName);
		Config.loadEmptyConfig();
		Config.getInstance().addEntry("connectionKeys", new RawJson(Crypto.generateKeys()));
	}

	/**
	 * @return the suite of tests being tested
	 */
	public static Test suite() {
		return new TestSuite(MultithreadingTestBig.class);
	}

	private class WorkerThread extends Thread {

		private final Logger LOGGER = LoggerFactory.getLogger(this.getClass());

		private Exception lastException = null;

		final int listeningPort;
		final int connectToPort;
		final String myName;
		private Node peer = null;

		public WorkerThread(int listeningPort, int connectToPort, String myName) {
			this.listeningPort = listeningPort;
			this.connectToPort = connectToPort;
			this.myName = myName;
		}

		public void stopListening() throws StoredException {
			peer.close();
		}

		public void throwLastException() throws Exception {
			if (lastException != null) {
				throw lastException;
			}
		}

		@Override
		public void run() {
			try {
				LOGGER.debug("Createing node " + myName + " that listening on " + listeningPort);
				peer = new Node(myName, listeningPort);
				PingListener pl = new PingListener(0, "Pong from port " + myName);
				peer.registerListener(pl);
				Thread.sleep(1000);
				if (connectToPort > 0) {
					LOGGER.debug("Node " + myName + " connecting to " + connectToPort);
					peer.connect("nomatter", "localhost", connectToPort);

					PingCommand pingCmd = new PingCommand("Ping from " + myName);
					peer.execute("nomatter", pingCmd);
				}
			} catch (Exception e) {
				lastException = e;
				e.printStackTrace();
			}

		}
	}

	public void testMultithreading() throws Exception {
		System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "info");
		final Logger LOGGER = LoggerFactory.getLogger(this.getClass());
		LOGGER.debug("Start tests");

		List<WorkerThread> threads = new LinkedList<WorkerThread>();
		threads.add(new WorkerThread(1399, -1, "peer-1"));
		for (int i = 0; i < 10; i++) {
			int connectTo = (int) (Math.random() * i);
			WorkerThread wtSrv = threads.get(connectTo);
			WorkerThread th = new WorkerThread(1300 + i, wtSrv.listeningPort, "peer" + i);
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

		for (WorkerThread th : threads) {
			th.stopListening();
		}

		for (WorkerThread th : threads) {
			th.throwLastException();
		}

	}

}
