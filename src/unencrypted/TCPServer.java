import java.io.*;
import java.net.*;
import java.util.Hashtable;
public class TCPServer {

    private Socket clientSocket = null; 
    private ServerSocket serverSocket = null; 
    private BufferedReader inFromClient = null;
    private DataOutputStream outToClient = null;
    public Hashtable<String, Socket> sendingSockets = null;
    public Hashtable<String, Socket> receivingSockets = null;

    public TCPServer(int port) {        
        try {   
            serverSocket = new ServerSocket(port);
            sendingSockets = new Hashtable<>();
            receivingSockets = new Hashtable<>();
        } catch(SocketException i) { 
            System.out.println(i); 
        }  catch(IOException i) {
            System.out.println((i));
        }   
    }
    
    void listenToRequest(){
        while (true) {
            try {
                clientSocket = serverSocket.accept(); 
                inFromClient = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                outToClient = new DataOutputStream(clientSocket.getOutputStream());
                ManageClient manager=new ManageClient(this, clientSocket, inFromClient, outToClient);
                manager.start();
            } catch(IOException i) { 
                System.out.println(i);
            }
        } 
    } 
    
    public static void main(String argv[]) throws Exception  { 
        TCPServer server = new TCPServer(6789);
        server.listenToRequest();
    } 
}

class ManageClient extends Thread {
    private String currentUser;
    private TCPServer server = null;
    private Socket clientSocket = null;
    private BufferedReader inFromClient = null;
    private DataOutputStream outToClient = null;

    public ManageClient(TCPServer server, Socket clientSocket, BufferedReader inFromClient, DataOutputStream outToClient){
        this.server=server;
        this.clientSocket=clientSocket;
        this.inFromClient=inFromClient;
        this.outToClient=outToClient;
    }
    
    @Override
    public void run() {
        while (true) {
            try {   
                String[] headerLine1_ARR=inFromClient.readLine().split(" ");
                switch (headerLine1_ARR[0]) {
                    case "REGISTER":
                        inFromClient.readLine();
                        if(headerLine1_ARR[1].equals("TOSEND")){
                            if(checkUsernameFormat(headerLine1_ARR[2])){
                                if(saveSender(headerLine1_ARR[2])){
                                    currentUser=headerLine1_ARR[2];
                                    outToClient.writeBytes("REGISTERED TOSEND "+headerLine1_ARR[2]+"\n\n");
                                }else{
                                    outToClient.writeBytes("ERROR 111 "+headerLine1_ARR[2]+"\n\n");
                                }
                            }else{
                                outToClient.writeBytes("ERROR 100 "+headerLine1_ARR[2]+"\n\n");
                            }
                        }
                        else if(headerLine1_ARR[1].equals("TORECV")){
                            if(checkUsernameFormat(headerLine1_ARR[2])){
                                if(saveReceiver(headerLine1_ARR[2])){
                                    currentUser=headerLine1_ARR[2];
                                    outToClient.writeBytes("REGISTERED TORECV "+headerLine1_ARR[2]+"\n\n");
                                    System.out.println("User "+currentUser+" Registered");
                                }else{
                                    outToClient.writeBytes("ERROR 111 "+headerLine1_ARR[2]+"\n\n");
                                }
                            }else{
                                outToClient.writeBytes("ERROR 100 "+headerLine1_ARR[2]+"\n\n");
                            }
                            this.stop();
                        }
                        break;
                    case "SEND":
                        String[] headerLine2_ARR=inFromClient.readLine().split(" "); inFromClient.readLine();                        
                        int contentLenght=Integer.parseInt(headerLine2_ARR[1]);
                        char[] messageContent=new char[contentLenght];
                        inFromClient.read(messageContent, 0, contentLenght);

                        String send="FORWARD "+currentUser+"\n"+
                                    "Content-length: "+contentLenght+"\n\n"+
                                    String.valueOf(messageContent);

                        Socket sendTo=server.receivingSockets.get(headerLine1_ARR[1]);
                        if(sendTo!=null){
                            DataOutputStream outToRecipient=new DataOutputStream(sendTo.getOutputStream());
                            outToRecipient.writeBytes(send);
                            BufferedReader inFromRecipient=new BufferedReader(new InputStreamReader(sendTo.getInputStream()));
                            String[] recipientResponse=inFromRecipient.readLine().split(" "); inFromRecipient.readLine();
                            if(recipientResponse[0].equals("RECEIVED")){
                                outToClient.writeBytes("SENT "+headerLine1_ARR[1]+"\n\n");  
                            }
                            else if((recipientResponse[0]+recipientResponse[1]).equals("ERROR 103")){
                                outToClient.writeBytes("ERROR 103 Header incomplete\n\n");
                            }
                        }
                        else{
                            outToClient.writeBytes("ERROR 102 Unable to send\n\n");
                        }
                        break;
                    default:
                        outToClient.writeBytes("ERROR 101 No user registered\n\n");
                        break;
                }
            } catch(IOException | NullPointerException i) {
                server.receivingSockets.remove(currentUser);
                server.sendingSockets.remove(currentUser);
                System.out.println("User "+currentUser+" Abruptly Disconnected");
                this.stop();
            } 
        }
    }
    
    boolean checkUsernameFormat(String username){
        return username.matches("[a-zA-Z0-9]*");
    }
    
    boolean saveSender(String userName){
        Socket socket=server.sendingSockets.get(userName);
        if(socket==null){
            server.sendingSockets.put(userName, clientSocket);
            return true;
        }
        return false;
    }
    
    boolean saveReceiver(String userName){
        Socket socket=server.receivingSockets.get(userName);
        if(socket==null){
            server.receivingSockets.put(userName, clientSocket);
            return true;
        }
        return false;
    }
}
 
           
