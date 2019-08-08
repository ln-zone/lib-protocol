package bittech.lib.protocol;

public interface CommandSender {
	
	public void send(String receiverId, Command<?,?> command);

}
