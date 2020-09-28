package bittech.lib.protocol.websocket;

import java.net.URI;

import bittech.lib.protocol.Command;
import bittech.lib.protocol.Listener;
import bittech.lib.protocol.ListenersManager;
import bittech.lib.utils.Utils;
import bittech.lib.utils.exceptions.StoredException;

public class WebSocketClient {

	LowerLevelWebSocket exampleClient;

	ListenersManager lisMan = new ListenersManager();
	JsonCommandExec exec = new JsonCommandExec(lisMan);

	public WebSocketClient(String serverUri) {
		try {
			lisMan = new ListenersManager();
			exec = new JsonCommandExec(lisMan);
			exampleClient = new LowerLevelWebSocket(exec, new URI(serverUri));
		} catch (Exception ex) {
			throw new StoredException("Cannot create WebSocketTester", ex);
		}
	}

	public void registerListener(Listener listener) {
		lisMan.registerListener(listener);
	}

	public void send(String authKey, Command<?, ?> command) {
		exampleClient.send(authKey, command);
		Utils.prn(command);
	}

}
