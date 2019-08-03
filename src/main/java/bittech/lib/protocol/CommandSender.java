package bittech.lib.protocol;

public interface CommandSender {
	
	public void send(Command<?,?> command);

}
