package server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Random;

public class Room implements AutoCloseable {
    private static SocketServer server;// used to refer to accessible server functions
    private String name;
    private final static Logger log = Logger.getLogger(Room.class.getName());

    // Commands
    private final static String COMMAND_TRIGGER = "/";
    private final static String CREATE_ROOM = "createroom";
    private final static String JOIN_ROOM = "joinroom";
    private final static String ROLL = "roll";
    private final static String FLIP = "flip";
    private final static String MUTE = "mute";
    private final static String UNMUTE = "unmute";


    public Room(String name) {
	this.name = name;
    }

    public static void setServer(SocketServer server) {
	Room.server = server;
    }

    public String getName() {
	return name;
    }

    private List<ServerThread> clients = new ArrayList<ServerThread>();
    HashMap<String,ArrayList<String>> MuteLists = new HashMap<String,ArrayList<String>>();

    protected synchronized void addClient(ServerThread client) {
	client.setCurrentRoom(this);
	if (clients.indexOf(client) > -1) {
	    log.log(Level.INFO, "Attempting to add a client that already exists");
	}
	else {
	    clients.add(client);
	    if (client.getClientName() != null) {
		client.sendClearList();
		sendConnectionStatus(client, true, "joined the room " + getName());
		updateClientList(client);
	    }
	}
    }

    private void updateClientList(ServerThread client) {
	Iterator<ServerThread> iter = clients.iterator();
	while (iter.hasNext()) {
	    ServerThread c = iter.next();
	    if (c != client) {
		boolean messageSent = client.sendConnectionStatus(c.getClientName(), true, null);
	    }
	}
    }

    protected synchronized void removeClient(ServerThread client) {
	clients.remove(client);
	if (clients.size() > 0) {
	    // sendMessage(client, "left the room");
	    sendConnectionStatus(client, false, "left the room " + getName());
	}
	else {
	    cleanupEmptyRoom();
	}
    }

    private void cleanupEmptyRoom() {
	// If name is null it's already been closed. And don't close the Lobby
	if (name == null || name.equalsIgnoreCase(SocketServer.LOBBY)) {
	    return;
	}
	try {
	    log.log(Level.INFO, "Closing empty room: " + name);
	    close();
	}
	catch (Exception e) {
	    // TODO Auto-generated catch block
	    e.printStackTrace();
	}
    }

    protected void joinRoom(String room, ServerThread client) {
	server.joinRoom(room, client);
    }

    protected void joinLobby(ServerThread client) {
	server.joinLobby(client);
    }

    /***
     * Helper function to process messages to trigger different functionality.
     * 
     * @param message The original message being sent
     * @param client  The sender of the message (since they'll be the ones
     *                triggering the actions)
     */
    private boolean processCommands(String message, ServerThread client) {
	boolean wasCommand = false;
	try {
	    if (message.indexOf(COMMAND_TRIGGER) > -1) {
		String[] comm = message.split(COMMAND_TRIGGER);
		log.log(Level.INFO, message);
		String part1 = comm[1];
		String[] comm2 = part1.split(" ");
		String command = comm2[0];
		if (command != null) {
		    command = command.toLowerCase();
		}
		String roomName;
		switch (command) {
		case CREATE_ROOM:
		    roomName = comm2[1];
		    if (server.createNewRoom(roomName)) {
			joinRoom(roomName, client);
		    }
		    wasCommand = true;
		    break;
		case JOIN_ROOM:
		    roomName = comm2[1];
		    joinRoom(roomName, client);
		    wasCommand = true;
		    break;
		case ROLL:
			Random randRoll = new Random(); 
			int rollMax = Integer.parseInt(comm2[1]);
			int rollResult = randRoll.nextInt(rollMax);
			String rollResult2 = Integer.toString(rollResult);
			sendMessage(client, "rolled " + rollResult2 + " out of " + rollMax);
			wasCommand = true;
			break;
		case FLIP:
			Random randFlip = new Random(); 
			int flipResult = randFlip.nextInt(2);
			if (flipResult == 0) {
				sendMessage(client, "flipped a coin. It landed on heads!");
			}
			else {
				sendMessage(client, "flipped a coin. It landed on tails!");
			}
			wasCommand = true;
			break;
		case MUTE:
			boolean CreateMuteList = MuteLists.containsKey(client.getClientName());
			if (!CreateMuteList) {
				MuteLists.put(client.getClientName(), new ArrayList<String>());
			}			
			MuteLists.get(client.getClientName()).add(comm2[1]);
			wasCommand = true;
			break;
		case UNMUTE:
			if (MuteLists.containsKey(client.getClientName())) {
				if(MuteLists.get(client.getClientName()).contains(comm2[1])) {
					MuteLists.get(client.getClientName()).remove(comm2[1]);
				}
			}
			wasCommand = true;
			break;
			
			
		}
	    }
	}
	catch (Exception e) {
	    e.printStackTrace();
	}
	return wasCommand;
    }
    
    private boolean processPrivateMessage(String message, ServerThread client) {
    	boolean wasPrivate = false;
    	try {
    	    if (message.indexOf("@") == 0) {
    		String[] comm = message.split("@");
    		log.log(Level.INFO, message);
    		String part1 = comm[1];
    		String[] comm2 = part1.split(" ");
    		String username = comm2[0];
    		sendPrivateMessage(client, username, message);
    		wasPrivate = true;
    	    }
    	}
    	catch (Exception e) {
    	    e.printStackTrace();
    	}
    	return wasPrivate;
        }
    

    // TODO changed from string to ServerThread
    protected void sendConnectionStatus(ServerThread client, boolean isConnect, String message) {
	Iterator<ServerThread> iter = clients.iterator();
	while (iter.hasNext()) {
	    ServerThread c = iter.next();
	    boolean messageSent = c.sendConnectionStatus(client.getClientName(), isConnect, message);
	    if (!messageSent) {
		iter.remove();
		log.log(Level.INFO, "Removed client " + c.getId());
	    }
	}
    }

    /***
     * Takes a sender and a message and broadcasts the message to all clients in
     * this room. Client is mostly passed for command purposes but we can also use
     * it to extract other client info.
     * 
     * @param sender  The client sending the message
     * @param message The message to broadcast inside the room
     */
    protected void sendMessage(ServerThread sender, String message) {
	log.log(Level.INFO, getName() + ": Sending message to " + clients.size() + " clients");
	if (processCommands(message, sender)) {
	    // it was a command, don't broadcast
	    return;
	}
	if (processPrivateMessage(message, sender)) {
	    // it was a private message, don't broadcast
	    return;
	}
	Iterator<ServerThread> iter = clients.iterator();
	while (iter.hasNext()) {
	    ServerThread client = iter.next();
	    if (MuteLists.containsKey(client.getClientName())) {
	    	if (MuteLists.get(client.getClientName()).contains(sender.getClientName())){
	    		continue;
	    	} else {
	    		boolean messageSent = client.send(sender.getClientName(), message);
			    if (!messageSent) {
					iter.remove();
			    }
	log.log(Level.INFO, "Removed client " + client.getId());
	    	}
	    } else {
	    	boolean messageSent = client.send(sender.getClientName(), message);
		    if (!messageSent) {
				iter.remove();
		    }
log.log(Level.INFO, "Removed client " + client.getId());		    
	    }
	}
    }
    
    protected void sendPrivateMessage(ServerThread sender, String receiver, String message) {
    	Iterator<ServerThread> iter = clients.iterator();
    	while (iter.hasNext()) {
    	    ServerThread client = iter.next();
    	    if(client.getClientName().equals(receiver) || client.getClientName().equals(sender.getClientName())) {
    	    	if (MuteLists.containsKey(client.getClientName())) {
    		    	if (MuteLists.get(client.getClientName()).contains(sender.getClientName())){
    		    		continue;
    		    	} else {
    		    		client.send(sender.getClientName(), message);
    		    	}
    	    	} else {
    	    		client.send(sender.getClientName(), message);
    	    	}
    	    	
    	    }
    	    // iter.remove();
    	    }
    	}
    

    /***
     * Will attempt to migrate any remaining clients to the Lobby room. Will then
     * set references to null and should be eligible for garbage collection
     */
    @Override
    public void close() throws Exception {
	int clientCount = clients.size();
	if (clientCount > 0) {
	    log.log(Level.INFO, "Migrating " + clients.size() + " to Lobby");
	    Iterator<ServerThread> iter = clients.iterator();
	    Room lobby = server.getLobby();
	    while (iter.hasNext()) {
		ServerThread client = iter.next();
		lobby.addClient(client);
		iter.remove();
	    }
	    log.log(Level.INFO, "Done Migrating " + clients.size() + " to Lobby");
	}
	server.cleanupRoom(this);
	name = null;
	// should be eligible for garbage collection now
    }

}