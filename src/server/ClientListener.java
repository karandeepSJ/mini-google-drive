package server;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

class ClientListener extends Thread {
    private Socket TCPSocket;
    private DataInputStream inData;
    private DataOutputStream outData;
    private String username;
    private DatagramSocket UDPSocket;

    private void greenMessage(String message) throws IOException {
        this.outData.writeUTF(Colors.ANSI_GREEN + "> " + message + Colors.ANSI_RESET);
    }

    private void yellowMessage(String message) throws IOException {
        this.outData.writeUTF(Colors.ANSI_YELLOW + "> " + message + Colors.ANSI_RESET);
    }

    private void errorMessage(String message) throws IOException {
        this.outData.writeUTF(Colors.ANSI_RED + "> " + message + Colors.ANSI_RESET);
    }

    public ClientListener(Socket TCPSocket, DatagramSocket UDPSocket) throws IOException {
        this.TCPSocket = TCPSocket;
        this.UDPSocket = UDPSocket;
        this.inData = new DataInputStream(this.TCPSocket.getInputStream());
        this.outData = new DataOutputStream(this.TCPSocket.getOutputStream());
        try {
            String loginInstruction = inData.readUTF();
            StringTokenizer parsedInstruction = new StringTokenizer(loginInstruction);
            String event = parsedInstruction.nextToken();
            if (event.equals("create_user")) {
                this.username = parsedInstruction.nextToken();
                if (Server.usernames.contains(this.username)) {
                    this.errorMessage("Username already exists");
                    this.TCPSocket.close();
                    this.inData.close();
                    this.outData.close();
                } else {
                    Server.usernames.add(this.username);
                    Server.IPAddresses.put(this.username, this.TCPSocket.getRemoteSocketAddress());
                    Server.clientSockets.put(this.username, this.TCPSocket);
                    File files_dir = new File("server/userfiles/" + this.username);
                    files_dir.mkdirs();
                    System.out.println("User " + this.username + " created");
                    this.greenMessage("Success");
                    start();
                }
            } else if (event.equals("login")) {
                this.username = parsedInstruction.nextToken();
                if (Server.usernames.contains(this.username)) {
                    Server.IPAddresses.put(this.username, this.TCPSocket.getRemoteSocketAddress());
                    Server.clientSockets.put(this.username, this.TCPSocket);
                    System.out.println("User " + this.username + " logged in");
                    this.greenMessage("Success");
                    start();
                } else {
                    outData.writeUTF("> Username does not exist");
                    this.TCPSocket.close();
                    this.inData.close();
                    this.outData.close();
                }
            } else {
                this.errorMessage("Please login or register first");
                this.inData.close();
                this.outData.close();
                this.TCPSocket.close();
            }

        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void run() {
        while(true) {
            try {
                String clientInstruction;
                clientInstruction = this.inData.readUTF();
                if(clientInstruction.equals("logout")) {
                    Server.clientSockets.remove(this.username);
                    this.greenMessage("LOGGED OUT");
                    this.inData.close();
                    this.outData.close();
                    this.TCPSocket.close();
                    break;
                }

                StringTokenizer parsedInstruction = new StringTokenizer(clientInstruction);
                if(!parsedInstruction.hasMoreTokens())
                    continue;
                String event = parsedInstruction.nextToken();

                switch (event) {
                    case "create_group": {
                        String groupName = parsedInstruction.nextToken();
                        if (Server.groups.containsKey(groupName)) {
                            this.errorMessage("Group " + groupName + " already exists. Please try another name.");
                        } else {
                            Group g = new Group(groupName);
                            Server.groups.put(groupName, g);
                            this.greenMessage("Group " + groupName + " created. You can join it by entering 'join_group " + groupName + "'");
                        }
                        break;
                    }
                    case "list_groups":
                        if (Server.groups.size() == 0)
                            this.yellowMessage("No groups created");
                        else {
                            this.outData.writeUTF("> List of Groups:");
                            for (String key : Server.groups.keySet()) {
                                this.outData.writeUTF("> " + key);
                            }
                        }
                        break;
                    case "join_group": {
                        String groupName = parsedInstruction.nextToken();
                        Optional<Group> g = Optional.ofNullable(Server.groups.get(groupName));
                        if (!g.isPresent()) {
                            this.errorMessage("Group does not exist");
                        } else {
                            if (g.get().isUserInGroup(this.username))
                                this.errorMessage("You are already in this group");
                            else {
                                g.get().joinGroup(this.username);
                                this.greenMessage("Successfully joined group");
                            }
                        }
                        break;
                    }
                    case "leave_group": {
                        String groupName = parsedInstruction.nextToken();
                        Optional<Group> g = Optional.ofNullable(Server.groups.get(groupName));
                        if (!g.isPresent()) {
                            this.errorMessage("Group does not exist");
                        } else {
                            if (!g.get().isUserInGroup(this.username))
                                this.errorMessage("You are not a member of this group");
                            else {
                                g.get().leaveGroup(this.username);
                                this.greenMessage("Successfully left group");
                            }
                            }
                        break;
                    }
                    case "list_detail": {
                        String groupName = parsedInstruction.nextToken();
                        Optional<Group> g = Optional.ofNullable(Server.groups.get(groupName));
                        if (!g.isPresent()) {
                            this.errorMessage("Group does not exist");
                        } else {
                            Vector<String> users = g.get().listUsers();
                            if (users.size() == 0) {
                                this.errorMessage("There are no users in this group");
                            } else {
                                this.outData.writeUTF("> List of users and user files in group " + groupName + " :");
                                for (String username : users) {
                                    StringBuilder det = new StringBuilder("> " + username + ": ");
                                    Path userDir = Paths.get("server/userfiles", username);
                                    Files.walk(userDir)
                                            .filter(Files::isDirectory)
                                            .forEach(filename -> det.append(
                                                    filename.toString()
                                                            .replace("server/userfiles/", "")
                                                            + "/ , "));
                                    Files.walk(userDir)
                                            .filter(Files::isRegularFile)
                                            .forEach(filename -> det.append(
                                                    filename.toString()
                                                            .replace("server/userfiles/", "")
                                                            + " , "));
                                    String detStr = det.toString();
                                    detStr = detStr.substring(0, detStr.length() - 2);
                                    this.outData.writeUTF(detStr);
                                }
                            }
                        }
                        break;
                    }
                    case "share_msg": {
                        String groupName = parsedInstruction.nextToken();
                        Optional<Group> g = Optional.ofNullable(Server.groups.get(groupName));
                        if (!g.isPresent()) {
                            this.errorMessage("Group does not exist");
                        } else {
                            if (!g.get().isUserInGroup(this.username))
                                this.errorMessage("You cannot share message since you are not a member of this group");
                            else {
                                String message = parsedInstruction.nextToken("");
                                message = message.substring(2, message.length() - 1);
                                g.get().broadcast(message, this.username);
                                this.greenMessage("Message sent successfully");
                            }
                        }
                        break;
                    }
                    case "create_folder":
                        String dirPath = parsedInstruction.nextToken();
                        Path dir = Paths.get("server/userfiles", this.username, dirPath);
                        if (Files.exists(dir)) {
                            this.errorMessage("Directory already exists");
                        } else {
                            Files.createDirectories(dir);
                            this.greenMessage("Successfully created directory");
                        }
                        break;
                    case "upload": {
                        String filename = parsedInstruction.nextToken();
                        String[] parsed = filename.split("/");
                        filename = parsed[parsed.length - 1];
                        Path filePath = Paths.get("server/userfiles", this.username, filename);
                        if (Files.exists(filePath)) {
                            this.errorMessage("Another file with this name already exists");
                        } else {
                            this.outData.writeUTF("File send approved - TCP");
                            String sizeInstr = inData.readUTF();
                            StringTokenizer tokSizeInstr = new StringTokenizer(sizeInstr);
                            if (tokSizeInstr.nextToken().equals("filesize")) {
                                int filesize = Integer.parseInt(tokSizeInstr.nextToken());
                                FileOutputStream outFile = new FileOutputStream(filePath.toString());
                                BufferedInputStream buffIn = new BufferedInputStream(this.TCPSocket.getInputStream());
                                byte[] buffer = new byte[1024];
                                int read = 0, remaining = filesize;
                                while ((read = buffIn.read(buffer, 0, Math.min(buffer.length, remaining))) > 0) {
                                    remaining -= read;
                                    outFile.write(buffer, 0, read);
                                }
                                outFile.close();
                                this.greenMessage("File Transfer Complete");
                            }
                        }
                        break;
                    }
                    case "upload_udp": {
                        String filename = parsedInstruction.nextToken();
                        String[] parsed = filename.split("/");
                        filename = parsed[parsed.length - 1];
                        Path filePath = Paths.get("server/userfiles", this.username, filename);
                        if (Files.exists(filePath)) {
                            this.errorMessage("Another file with this name already exists");
                        } else {
                            this.outData.writeUTF("File send approved - UDP");
                            String sizeInstr = inData.readUTF();
                            StringTokenizer tokSizeInstr = new StringTokenizer(sizeInstr);
                            if (tokSizeInstr.nextToken().equals("filesize")) {
                                int filesize = Integer.parseInt(tokSizeInstr.nextToken());
                                FileOutputStream outFile = new FileOutputStream(filePath.toString());
                                byte[] buffer = new byte[1024];
                                DatagramPacket UDPRecv;
                                int read = 1024, remaining = filesize;
                                while (remaining > 0) {
                                    if (remaining < 1024)
                                        read = remaining;

                                    UDPRecv = new DatagramPacket(buffer, read);
                                    this.UDPSocket.receive(UDPRecv);
                                    remaining -= read;
                                    outFile.write(buffer, 0, read);
                                }
                                outFile.close();
                                this.greenMessage("File Transfer Complete");
                            }
                        }
                        break;
                    }
                    case "move_file":
                        if (parsedInstruction.countTokens() != 2) {
                            this.outData.writeUTF("Please check usage format: move_file 'source_path' 'dest_path'");
                            continue;
                        }
                        Path source = Paths.get("server/userfiles", this.username, parsedInstruction.nextToken());
                        Path dest = Paths.get("server/userfiles", this.username, parsedInstruction.nextToken());
                        if (!Files.exists(source)) {
                            this.errorMessage("File Not Found");
                            continue;
                        }
                        if (Files.exists(dest)) {
                            this.errorMessage("The destination already has a file with this name");
                            continue;
                        }
                        String[] destTok = dest.toString().split("/");
                        System.out.println(Paths.get(String.join("/", Arrays.copyOf(destTok, destTok.length - 1))));
                        if (!Files.exists(Paths.get(String.join("/", Arrays.copyOf(destTok, destTok.length - 1))))) {
                            this.errorMessage("Destination directory does not exist");
                            continue;
                        }

                        Files.move(source, dest);
                        this.greenMessage("File moved");

                        break;
                    case "get_file": {
                        String filePath = parsedInstruction.nextToken();
                        StringTokenizer filePathTok = new StringTokenizer(filePath, "/");
                        String groupName = filePathTok.nextToken();
                        Optional<Group> g = Optional.ofNullable(Server.groups.get(groupName));
                        if (!g.isPresent()) {
                            this.errorMessage("Group does not exist");
                            continue;
                        }
                        String user = filePathTok.nextToken();
                        if (!g.get().isUserInGroup(user)) {
                            this.errorMessage("This user is not a member of this group");
                            continue;
                        }
                        Path path = Paths.get("server/userfiles", user, filePathTok.nextToken("").substring(1));
                        if (!Files.exists(path) || !Files.isRegularFile(path)) {
                            this.errorMessage("File Not Found");
                            continue;
                        }
                        File file = new File(path.toString());
                        long fileLen = file.length();
                        this.outData.writeUTF("filesize " + fileLen);
                        FileInputStream inFile = new FileInputStream(file);
                        byte[] buffer = new byte[1024];
                        BufferedOutputStream buffOut = new BufferedOutputStream(this.TCPSocket.getOutputStream());
                        while (inFile.read(buffer) > 0) {
                            buffOut.write(buffer);
                        }
                        buffOut.flush();
                        inFile.close();

                        break;
                    }
                    case "filesize":
                        break;
                    default:
                        this.errorMessage("Command not found");
                        break;
                }

            } catch (Exception e) {
                e.printStackTrace();
                break;
            }
        }
    }
}
