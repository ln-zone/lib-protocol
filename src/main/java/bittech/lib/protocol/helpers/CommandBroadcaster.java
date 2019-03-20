package bittech.lib.protocol.helpers;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.Gson;

import bittech.lib.protocol.Command;
import bittech.lib.protocol.Node;
import bittech.lib.utils.Require;
import bittech.lib.utils.Utils;
import bittech.lib.utils.exceptions.StoredException;
import bittech.lib.utils.json.JsonBuilder;

public class CommandBroadcaster {

	class Services {
		private Set<String> services = new HashSet<String>();
	}

	private Node node;
	private final String fileName;
	private Services services = new Services();

	public CommandBroadcaster(Node node, String fileName) {
		this.node = Require.notNull(node, "node");
		this.fileName = Require.notEmpty(fileName, "fileName");
		load();
	}

	public synchronized void addService(String fromServiceName) {
		services.services.add(fromServiceName);
		if (fileName != null) {
			save();
		}
	}

	private void save() {
		try {
			Require.notEmpty(fileName, "fileName");
			Gson json = JsonBuilder.build();
			try (FileWriter fileWriter = new FileWriter(new File(fileName))) {
				json.toJson(services, fileWriter);
			}
		} catch (Exception ex) {
			throw new StoredException("Cannot save services to file: " + fileName, ex);
		}
	}
	
	private synchronized void load() {
		try {
			Require.notEmpty(fileName, "fileName");
			File file = new File(fileName);
			if(file.exists() == false) {
				return;
			}
			
			Gson json = JsonBuilder.build();
			try (FileReader fileReader = new FileReader(file)) {
				services = json.fromJson(fileReader, Services.class);
			}
		} catch (Exception ex) {
			throw new StoredException("Cannot save services to file: " + fileName, ex);
		}
	}

	public synchronized List<Command<?, ?>> broadcast(Command<?, ?> command) {
		List<Command<?, ?>> commands = new ArrayList<Command<?, ?>>(services.services.size());
		for (String connectionName : services.services) {
			Command<?, ?> cpyCommand = Utils.deepCopy(command, command.getClass());
			commands.add(cpyCommand);
			node.execute(connectionName, cpyCommand);
		}
		return commands;
	}

}
