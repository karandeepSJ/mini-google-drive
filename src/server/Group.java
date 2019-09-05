package server;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Optional;
import java.util.Vector;

public class Group {
    private Vector<String> users;
    private String name;

    public Group(String name) {
        this.users = new Vector<String>();
        this.name = name;
    }

    public void joinGroup(String username) {
        this.users.add(username);
    }

    public void leaveGroup(String username) {
        this.users.remove(username);
    }

    public boolean isUserInGroup(String username) {
        return this.users.contains(username);
    }

    public Vector<String> listUsers() {
        return this.users;
    }

    public void broadcast(String message, String username) throws IOException {
        Optional<Socket> userSocket;
        for (String user : this.users) {
            if (!user.equals(username)) {
                userSocket = Optional.ofNullable(Server.clientSockets.get(user));
                if(userSocket.isPresent()) {
                    DataOutputStream outData = new DataOutputStream(userSocket.get().getOutputStream());
                    outData.writeUTF("> " + username + " sent the message: " + message);
                }
            }
        }
    }

    public static void main(String args[]){
    }
}
