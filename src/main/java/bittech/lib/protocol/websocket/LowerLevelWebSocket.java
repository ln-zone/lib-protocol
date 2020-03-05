package bittech.lib.protocol.websocket;

import java.net.URI;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import bittech.lib.protocol.Command;
import bittech.lib.utils.Require;
import bittech.lib.utils.exceptions.StoredException;

public class LowerLevelWebSocket extends WebSocketClient {

	private Exception lastException = null;
	private final Object connected = new Object();
	private final JsonCommandExec exec;

	public LowerLevelWebSocket(JsonCommandExec exec, URI serverURI) {
		super(serverURI);
		try {
			this.exec = Require.notNull(exec, "exec");
			connect();
			synchronized (connected) {
				connected.wait();
				if (lastException != null) {
					throw new Exception("Failed to connect to '" + serverURI + "'", lastException);
				}
			}
		} catch (Exception ex) {
			throw new StoredException("Cannot create WebSocket connector", ex);
		}
	}

	@Override
	public void onOpen(ServerHandshake handshakedata) {
		System.out.println("opened connection");
		synchronized (connected) {
			connected.notify();
		}
	}

	@Override
	public void onMessage(String message) {
		if ("alive".equals(message)) {
			return;
		}
		exec.onReceived("", message);
	}

	@Override
	public void onClose(int code, String reason, boolean remote) {
		System.out.println(
				"Connection closed by " + (remote ? "remote peer" : "us") + " Code: " + code + " Reason: " + reason);
	}

	@Override
	public void onError(Exception ex) {
		synchronized (connected) {
			lastException = new StoredException("Web socket error", ex);
			connected.notify();
		}
	}

	public void send(String authKey, Command<?, ?> command) {
		String cmdStr = exec.prepareToSend(authKey, command);
//		System.out.println("Sending: " + cmdStr);
		send(cmdStr);
		exec.waitForResponse(command);
	}

}
