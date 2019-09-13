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
    public Hashtable<String, Hashtable<String, Object>> database = null;

    public TCPServer(int port) {        
        try {   
            serverSocket = new ServerSocket(port);
            sendingSockets = new Hashtable<>();
            receivingSockets = new Hashtable<>();
            database =new Hashtable<>();
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
                    case "FETCHKEY":
                        inFromClient.readLine();
                        String fetchedKey=getPubKey(headerLine1_ARR[1]);
                        if(fetchedKey!=null){
                            outToClient.writeBytes(fetchedKey.length()+"\n"+fetchedKey);
                        }
                        else{
                            outToClient.writeBytes("ERROR 105 "+headerLine1_ARR[1]+"\n");
                        }
                        break;
                    case "REGISTER":
                        
                        if(headerLine1_ARR[1].equals("TOSEND")){
                            if(checkUsernameFormat(headerLine1_ARR[2])){
                                int keylength = Integer.parseInt(inFromClient.readLine()); inFromClient.readLine();
                                char[] pubKey = new char[keylength];
                                inFromClient.read(pubKey, 0, keylength);

                                if(saveSender(headerLine1_ARR[2], new String(pubKey))){
                                    currentUser=headerLine1_ARR[2];
                                    outToClient.writeBytes("REGISTERED TOSEND "+headerLine1_ARR[2]+"\n\n");
                                }else{
                                    outToClient.writeBytes("ERROR 111 "+headerLine1_ARR[2]+"\n\n");
                                }
                            }else{
                                int keylength = Integer.parseInt(inFromClient.readLine()); inFromClient.readLine();
                                char[] pubKey = new char[keylength];
                                inFromClient.read(pubKey, 0, keylength);
                                
                                outToClient.writeBytes("ERROR 100 "+headerLine1_ARR[2]+"\n\n");
                            }
                        }
                        else if(headerLine1_ARR[1].equals("TORECV")){
                            inFromClient.readLine();
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
                        String[] headerLine2_ARR=inFromClient.readLine().split(" ");
                        String[] headerLine3_ARR = inFromClient.readLine().split(" ");
                        inFromClient.readLine();

                        int contentLength=Integer.parseInt(headerLine2_ARR[1]);
                        int sigContentLength= Integer.parseInt(headerLine3_ARR[1]);

                        char[] messageContent=new char[contentLength]; inFromClient.read(messageContent, 0, contentLength);
                        char[] signature=new char[sigContentLength]; inFromClient.read(signature, 0, sigContentLength);
                        String messsage1=new String(messageContent);
                        String signature1=new String(signature);
                        String send="FORWARD "+currentUser+"\n"+
                                    "Content-length: "+contentLength+"\n"+
                                    "signature-lenght: "+sigContentLength+"\n\n"+
                                    messsage1 + signature1;

                        Hashtable<String, Object> table = server.database.get(headerLine1_ARR[1]);
                        if(table==null){
                            outToClient.writeBytes("ERROR 102 Unable to send\n\n");
                            break;           
                        }
                        else{
                            Socket sendTo = (Socket)table.get("recvSocket");
                            if(sendTo!=null){
                                DataOutputStream outToRecipient=new DataOutputStream(sendTo.getOutputStream());
                                outToRecipient.writeBytes(send);
                                BufferedReader inFromRecipient=new BufferedReader(new InputStreamReader(sendTo.getInputStream()));
                                String fKey=getPubKey(inFromRecipient.readLine().split(" ")[1]);
                                if(fKey!=null){
                                    inFromRecipient.readLine();
                                    outToRecipient.writeBytes(fKey.length()+"\n"+fKey);
                                }
                                else{
                                    outToClient.writeBytes("ERROR 105 "+headerLine1_ARR[1]+"\n");
                                    break;
                                }

                                String[] recipientResponse=inFromRecipient.readLine().split(" "); inFromRecipient.readLine();

                                if(recipientResponse[0].equals("RECEIVED") && recipientResponse[1].equals(currentUser)){
                                    outToClient.writeBytes("SENT "+headerLine1_ARR[1]+"\n\n");  
                                }
                                else if((recipientResponse[0]+recipientResponse[1]).equals("ERROR 103")){
                                    outToClient.writeBytes("ERROR 103 Header incomplete\n\n");
                                }
                                else if((recipientResponse[0]+recipientResponse[1]).equals("ERROR 106")){
                                    outToClient.writeBytes("ERROR 105 Message Tempered\n\n");
                                }
                            }
                            else{
                                outToClient.writeBytes("ERROR 102 Unable to send\n\n");
                                break;
                            }
                            break; 
                        }

                    default:
                        outToClient.writeBytes("ERROR 101 No user registered\n\n");
                        break;
                }
            } catch(IOException | NullPointerException i) {
                if(server.database.get(currentUser)!=null){
                    server.database.remove(currentUser);
                }
                System.out.println("User "+currentUser+" Abruptly Disconnected");
                this.stop();
            } 
        }
    }
    
    String getPubKey(String userName){
        String ret=null;
        Hashtable<String, Object> table = server.database.get(userName);
        if(table==null){
            return ret;
        }
        else{
            ret = (String)table.get("publicKey");            
        }
        return ret;
    }
    
    boolean checkUsernameFormat(String username){
        return username.matches("[a-zA-Z0-9]*");
    }
    
    boolean saveSender(String userName, String pubKey){

        //Socket socket=server.sendingSockets.get(userName);
        Socket sendSocket;
        Hashtable<String, Object> table = server.database.get(userName);
        if(table==null){
            table = new Hashtable<>();
            table.put("sendSocket",clientSocket);
            table.put("publicKey",pubKey);
            server.database.put(userName,table);
            return true;
        }
        else{
            sendSocket = (Socket)server.database.get(userName).get("sendSocket");
            if(sendSocket==null){
                //server.sendingSockets.put(userName, clientSocket);
                table.put("sendSocket",clientSocket);
                table.put("publicKey",pubKey);
                return true;
            }
            
        }
        return false;
    }
    
    boolean saveReceiver(String userName){
        
        //Socket socket=server.receivingSockets.get(userName);
        Socket receivingSocket;
        Hashtable<String, Object> table = server.database.get(userName);
        if(table==null){
            table = new Hashtable<>();
            table.put("recvSocket",clientSocket);
            server.database.put(userName,table);
            return true;            
        }
        else{
            receivingSocket = (Socket)server.database.get(userName).get("recvSocket");
            if(receivingSocket==null){
                //server.receivingSocket.put(userName, clientSocket);
                table.put("recvSocket",clientSocket);
                return true;
            }
            
        }
        return false;
    }
}



