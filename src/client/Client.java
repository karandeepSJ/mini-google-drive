package client;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.StringTokenizer;

public class Client {
    private static String serverIP;
    private static int UDPPort;
    private static DatagramSocket clientUDPSocket;
    public static Socket clientTCPSocket;
    private static DataInputStream inData;
    private static DataOutputStream outData;
    private static String filePath;

    private static void greenMessage(String message) throws IOException {
        outData.writeUTF(Colors.ANSI_GREEN + "> " + message + Colors.ANSI_RESET);
    }

    private static void yellowMessage(String message) throws IOException {
        outData.writeUTF(Colors.ANSI_YELLOW + "> " + message + Colors.ANSI_RESET);
    }

    private static void errorMessage(String message) throws IOException {
        outData.writeUTF(Colors.ANSI_RED + "> " + message + Colors.ANSI_RESET);
    }

    public Client() throws IOException {
        serverIP = "127.0.0.1";
        int TCPPort = 7000;
        UDPPort = 7001;
        clientUDPSocket = new DatagramSocket();
        clientTCPSocket = new Socket(serverIP, TCPPort);
        System.out.println("Connected to Server");
        inData = new DataInputStream(clientTCPSocket.getInputStream());
        outData = new DataOutputStream(clientTCPSocket.getOutputStream());
    }

    public static void main(String[] args) {
        try {
            Client client = new Client();
            System.out.println("> Login using 'login <username>' or register using 'create_user <username>'\n");
            new Thread(new ResponseHandler(inData)).start();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("> Closing connection...");
                try {
                    outData.writeUTF("logout");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }));

            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));
            String inputCommand = bufferedReader.readLine();
            outData.writeUTF(inputCommand);

            while(true) {
                try {
                    inputCommand = bufferedReader.readLine();

                    StringTokenizer parsedInstruction = new StringTokenizer(inputCommand);
                    String event = parsedInstruction.nextToken();
                    if(event.startsWith("upload")) {
                        filePath = parsedInstruction.nextToken();
                        if (!Files.exists(Paths.get(filePath))) {
                            errorMessage("Cannot open file. Check if the file exists");
                            continue;
                        }
                    }
                    else if(event.equals("get_file")) {
                        filePath = parsedInstruction.nextToken();
                        String[] filePathTok = filePath.split("/");
                        if(filePathTok.length < 3) {
                            errorMessage("Please specify file path in format groupname/username/file_path");
                            continue;
                        }
                        filePath = filePathTok[filePathTok.length-1];
                    }
                    outData.writeUTF(inputCommand);
                } catch (Exception e) {
                    e.printStackTrace();
                    break;
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            System.exit(0);
        }
    }

    public static void transferFileTCP() throws IOException {
        File file;
        file = new File(filePath);
        long fileLen = file.length();
        outData.writeUTF("filesize " + fileLen);
        FileInputStream inFile = new FileInputStream(file);
        byte[] buffer = new byte[1024];
        yellowMessage("Sending file.....");
        BufferedOutputStream buffOut = new BufferedOutputStream(clientTCPSocket.getOutputStream());
        while (inFile.read(buffer) > 0) {
            buffOut.write(buffer);
        }
        buffOut.flush();
        inFile.close();
    }

    public static void transferFileUDP() throws IOException {
        File file = new File(filePath);
        long fileLen = file.length();
        outData.writeUTF("filesize " + fileLen);
        FileInputStream inFile = new FileInputStream(file);
        byte[] buffer = new byte[1024];
        yellowMessage("Sending file.....");
        while (inFile.read(buffer) > 0) {
            DatagramPacket packetUDP = new DatagramPacket(buffer, buffer.length, InetAddress.getByName(serverIP), UDPPort);
            clientUDPSocket.send(packetUDP);
        }
        inFile.close();
    }

    public static void downloadFile(int filesize) throws IOException {
        yellowMessage("Downloading file...");
        FileOutputStream outFile = new FileOutputStream(filePath);
        BufferedInputStream buffIn = new BufferedInputStream(clientTCPSocket.getInputStream());
        byte[] buffer = new byte[1024];
        int read = 0, remaining = filesize;
        while ((read = buffIn.read(buffer, 0, Math.min(buffer.length, remaining))) > 0) {
            remaining -= read;
            outFile.write(buffer, 0, read);
        }
        outFile.close();
        greenMessage("File download Complete");
    }

    public static void closeConnection() throws IOException {
        outData.close();
        inData.close();
        clientTCPSocket.close();
        System.exit(0);
    }
}
