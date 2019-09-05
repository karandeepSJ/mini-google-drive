package client;

import java.io.DataInputStream;

class ResponseHandler implements Runnable {
    private DataInputStream inData;

    public ResponseHandler(DataInputStream inData) {
        this.inData = inData;
    }

    @Override
    public void run() {
        String response = null;
        while(true) {
            try {
                response = this.inData.readUTF();
                if (response.equals("File send approved - TCP")){
                    Client.transferFileTCP();
                    continue;
                }
                else if (response.equals("File send approved - UDP")){
                    Client.transferFileUDP();
                    continue;
                }
                else if(response.startsWith("filesize ")) {
                    int filesize = Integer.parseInt(response.substring(9));
                    Client.downloadFile(filesize);
                    continue;
                }
                System.out.println(response);
                if(response.equals("> Please login or register first")) {
                    System.exit(0);
                    Client.closeConnection();
                }
                else if(response.equals("> LOGGED OUT")) {
                    this.inData.close();
                    Client.closeConnection();
                    break;
                }
            }
            catch (Exception e) {
                e.printStackTrace();
                break;
            }
        }
    }
}
