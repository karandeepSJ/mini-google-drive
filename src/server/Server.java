package server;

import java.net.*;
import java.util.HashMap;
import java.util.Vector;

public class Server {
    public static HashMap<String, Socket> clientSockets;
    public static Vector<String> usernames;
    public static HashMap<String, Group> groups;
    public static HashMap<String, SocketAddress> IPAddresses;

    public Server() {
        try {
            System.out.println("Server running on Port 7000 for TCP and port 7001 for UDP");
            ServerSocket serverSocketTCP = new ServerSocket(7000) ;
            DatagramSocket serverSocketUDP = new DatagramSocket(7001);
            clientSockets = new HashMap<String, Socket>();
            usernames = new Vector<String>();
            groups = new HashMap<String, Group>();
            IPAddresses = new HashMap<String, SocketAddress>();
            while(true)
            {
                Socket TCPSocket = serverSocketTCP.accept();
                ClientListener client = new ClientListener(TCPSocket, serverSocketUDP) ;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            System.exit(0);
        }
    }

    public static void main(String args[]) throws Exception {
        Server server = new Server() ;
    }
}