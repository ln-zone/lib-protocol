package bittech.lib.protocol.websocket;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import bittech.lib.protocol.Command;
import bittech.lib.protocol.CommandSender;
import bittech.lib.utils.OutputToFileWriter;
import bittech.lib.utils.Require;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;

public class ApiCommandsSender implements CommandSender {

	private final Map<String, WebSocketChannel> channelsById = new HashMap<String, WebSocketChannel>();
	private final Map<WebSocketChannel, String> channelsBySocket = new HashMap<WebSocketChannel, String>();

	private final Map<String, List<String>> apiClients = new HashMap<String, List<String>>();

	@Override
	public synchronized void send(String apiClient, Command<?, ?> command) {
		WebSocketChannel channel = channelsById.get(apiClient);
		String cmd = JsonCommandExec.commandRequestToJson(command);
		OutputToFileWriter.saveLine(System.currentTimeMillis() + " WEB: < " + cmd);
		WebSockets.sendText(cmd, channel, null);
	}

	public synchronized void addChannel(WebSocketChannel channel) {
		String randId = "" + (long) (Math.random() * Long.MAX_VALUE);
		channelsById.put(randId, channel);
		channelsBySocket.put(channel, randId);
	}

	public synchronized void delChannel(WebSocketChannel channel) {
//		channels.remove(channel); TODO: Think abut this
	}

	// Using for "alive" only
	public synchronized void broadcast(String message) {
		for (WebSocketChannel channel : channelsById.values()) {
			WebSockets.sendText(message, channel, null);
		}
	}

	public synchronized String getChannelId(WebSocketChannel channel) {
		return Require.notNull(channelsBySocket.get(channel), "channel ID");
	}

	public synchronized void addClient(String id, List<String> patterns) {
		apiClients.put(id, patterns);
	}

	public synchronized void sendToClients(Command<?, ?> cmd) {
		for (Entry<String, List<String>> entry : apiClients.entrySet()) {
			String apiClient = entry.getKey();
			List<String> patterns = entry.getValue();
			if (matches(cmd.type, patterns)) {
				send(apiClient, cmd);
			}
		}
	}

	private static boolean matches(final String commandType, final List<String> patterns) {
		for (String pattern : patterns) {
			if (commandType.startsWith(pattern)) {
				return true;
			}
		}
		return false;
	}

}
