package bittech.lib.protocol.websocket;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import bittech.lib.protocol.Listener;
import bittech.lib.protocol.ListenersManager;
import bittech.lib.utils.OutputToFileWriter;
import bittech.lib.utils.Require;
import bittech.lib.utils.exceptions.StoredException;
import bittech.lib.utils.storage.Storage;
import io.undertow.websockets.core.WebSocketChannel;

public class WebSocketServer {

	private final ListenersManager listenersManager = new ListenersManager();
	private final JsonCommandExec jsonCommandExec;
	private final AuthenticationModule authenticationModule = new AuthenticationModule();
	private final ApiCommandsSender apiCommandsSender = new ApiCommandsSender();

	final ExecutorService exService = Executors.newSingleThreadExecutor();

	public WebSocketServer(Storage storage) {

		listenersManager.registerListener(authenticationModule);
		jsonCommandExec = new JsonCommandExec(listenersManager);

		sendingAliveThread();
	}

	public synchronized String onReceived(WebSocketChannel channel, String message) {
		OutputToFileWriter.saveLine(System.currentTimeMillis() + " WEB: > " + message);
		String id = apiCommandsSender.getChannelId(channel);
		return jsonCommandExec.onReceived(id, message);
	}

//	private synchronized void removeDeadChannels() {
//		for (WebSocketChannel channel : channels) {
//			if(!channel.is) {
//				channels.remove(channel);
//			}
//		}
//	}

	private void sendingAliveThread() {
		System.out.println("sendingAliveThread");
		exService.submit(() -> {
			while (true) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					new StoredException("Sleep interrupted", e);
				}
				apiCommandsSender.broadcast("alive");
			}
		});
	}

	public void addListener(Listener listener) {
		listenersManager.registerListener(Require.notNull(listener, "listener"));
	}

	public synchronized void addChannel(WebSocketChannel channel) {
		apiCommandsSender.addChannel(channel);
	}

	public ApiCommandsSender getCommandSender() {
		return apiCommandsSender;
	}

	public AuthenticationModule getAuthenticationModule() {
		return authenticationModule;
	}

	public void setAuthenticator(final Authenticator authenticator) {
		authenticationModule.setAuthenticator(authenticator);
	}
	
}
