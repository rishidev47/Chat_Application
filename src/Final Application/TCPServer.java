import java.io.*; 
import java.net.*;
import java.util.Hashtable;


public class TCPServer {
    private String mode;
    private Socket clientSocket = null; 
    private ServerSocket serverSocket = null; 
    private BufferedReader inFromClient = null;
    private DataOutputStream outToClient = null;
    public Hashtable<String, Hashtable<String, Object>> database = null;

    public TCPServer(int port, String mode) {        
        try {   
            this.mode=mode;
            serverSocket = new ServerSocket(port);
            database =new Hashtable<String, Hashtable<String, Object>>();
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
                ManageClient manager=new ManageClient(this, clientSocket, inFromClient, outToClient, mode);
                manager.start();
            } catch(IOException i) { 
                System.out.println(i);
            }
        } 
    } 
    
    public static void main(String argv[]) throws Exception  {
        System.out.println("Enter the number corrosponding to the mode in which you want to run the application\n"+
                            "1. Unencrypted\n2. Encrypted\n3. Encrypted with Signature");
        BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in)); 
        String mode = "";
        while (true) {
            System.out.print("Mode: ");
            mode = inFromUser.readLine().trim();
            if (mode.equals("1") || mode.equals("2") || mode.equals("3"))
                break;
            else
                continue;
        }
        inFromUser.close();
        TCPServer server = new TCPServer(6789,mode);
        server.listenToRequest();
    } 
}

class ManageClient extends Thread {
    private String mode;
    private String currentUser;
    private TCPServer server = null;
    private Socket clientSocket = null;
    private BufferedReader inFromClient = null;
    private DataOutputStream outToClient = null;

    public ManageClient(TCPServer server, Socket clientSocket, BufferedReader inFromClient, DataOutputStream outToClient, String mode){
        this.mode=mode;
        this.server=server;
        this.clientSocket=clientSocket;
        this.inFromClient=inFromClient;
        this.outToClient=outToClient;
    }
    
    void unregister(){
        if(server.database.get(currentUser)!=null){
            server.database.remove(currentUser);
        }
        System.out.println("User "+currentUser+" Unregistered");
        this.stop();
    }

    void forwardMessage(String recipient) throws IOException {
        String[] headerLine2_ARR=inFromClient.readLine().split(" ");
        inFromClient.readLine();
        int contentLength=Integer.parseInt(headerLine2_ARR[1]);
        //char[] messageContent=new char[contentLength];
        //inFromClient.read(messageContent, 0, contentLength);
        String send="FORWARD "+currentUser+"\n"+
                    "Content-length: "+contentLength+"\n\n"+
                    readSomeByte(contentLength, inFromClient);

        Hashtable<String, Object> table = server.database.get(recipient);
        if(table==null){
            outToClient.writeBytes("ERROR 102 Unable to send\n\n");
            return;           
        }
        else{
            Socket sendTo = (Socket)table.get("recvSocket");
            if(sendTo!=null){
                DataOutputStream outToRecipient=new DataOutputStream(sendTo.getOutputStream());
                outToRecipient.writeBytes(send);
                BufferedReader inFromRecipient=new BufferedReader(new InputStreamReader(sendTo.getInputStream()));

                String[] recipientResponse=inFromRecipient.readLine().split(" "); 
                inFromRecipient.readLine();
                if(recipientResponse[0].equals("RECEIVED") && recipientResponse[1].equals(currentUser)){
                    outToClient.writeBytes("SENT "+recipient +"\n\n");
                }
                else if((recipientResponse[0]+recipientResponse[1]).equals("ERROR 103")){
                    outToClient.writeBytes("ERROR 103 Header incomplete\n\n");
                }
            }
            else{
                outToClient.writeBytes("ERROR 102 Unable to send\n\n");
                return;
            }
            
        }
    }
    
    void forwardMessageWithSignature(String sender) throws IOException {
        String[] headerLine2_ARR=inFromClient.readLine().split(" ");
        String[] headerLine3_ARR = inFromClient.readLine().split(" ");
        inFromClient.readLine();

        int contentLength_3=Integer.parseInt(headerLine2_ARR[1]);
        int sigContentLength= Integer.parseInt(headerLine3_ARR[1]);

        //char[] messageContent_3=new char[contentLength_3]; inFromClient.read(messageContent_3, 0, contentLength_3);
        //char[] signature=new char[sigContentLength]; inFromClient.read(signature, 0, sigContentLength);
        String messsage1=readSomeByte(contentLength_3, inFromClient);
        String signature1=readSomeByte(sigContentLength, inFromClient);
        String send_3="FORWARD "+currentUser+"\n"+
                    "Content-length: "+contentLength_3+"\n"+
                    "signature-lenght: "+sigContentLength+"\n\n"+
                    messsage1 + signature1;

        Hashtable<String, Object> table_3 = server.database.get(sender);
        if(table_3==null){
            outToClient.writeBytes("ERROR 102 Unable to send\n\n");
            return;           
        }
        else{
            Socket sendTo = (Socket)table_3.get("recvSocket");
            if(sendTo!=null){
                DataOutputStream outToRecipient=new DataOutputStream(sendTo.getOutputStream());
                outToRecipient.writeBytes(send_3);
                BufferedReader inFromRecipient=new BufferedReader(new InputStreamReader(sendTo.getInputStream()));
                String fKey_3=getPubKey(inFromRecipient.readLine().split(" ")[1]);
                if(fKey_3!=null){
                    inFromRecipient.readLine();
                    outToRecipient.writeBytes(fKey_3.length()+"\n"+fKey_3);
                }
                else{
                    outToClient.writeBytes("ERROR 105 "+sender+"\n");
                    return;
                }

                String[] recipientResponse=inFromRecipient.readLine().split(" "); inFromRecipient.readLine();

                if(recipientResponse[0].equals("RECEIVED") && recipientResponse[1].equals(currentUser)){
                    outToClient.writeBytes("SENT "+sender+"\n\n");  
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
                return;
            }
        }
    }

    String readSomeByte(int len, BufferedReader reader) throws IOException{
        int temp=0;
        String ret="";
        while(temp!=len){
            int t = reader.read();
            if(t!=-1){
                char c=(char)t;
                ret=ret+c;
                temp++;
            }
        }
        return ret;
    }
    
    @Override
    public void run() {
        while (true) {
            try {   
                String s=inFromClient.readLine();
                if(s!=null)System.out.println(s);
                String[] headerLine1_ARR=s.split(" ");
                switch (headerLine1_ARR[0]) {
                    case "UNREGISTER":
                        inFromClient.readLine();
                        unregister();
                        break;

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
                            if(!headerLine1_ARR[2].equals(mode)){
                                if(!headerLine1_ARR[2].equals("1")){
                                    int keyLength = Integer.parseInt(inFromClient.readLine()); 
                                    inFromClient.readLine();
                                    //char[] pubKey = new char[keylength];
                                    //inFromClient.read(pubKey, 0, keylength);
                                    readSomeByte(keyLength, inFromClient);
                                }
                                else{
                                    inFromClient.readLine();
                                }
                                outToClient.writeBytes("ERROR 120 Mode Missmatch "+mode+"\n\n");
                                break;
                            }
                            if(checkUsernameFormat(headerLine1_ARR[3]) && headerLine1_ARR.length==4){
                                
                                if(!mode.equals("1")){
                                    int keyLength = Integer.parseInt(inFromClient.readLine()); 
                                    inFromClient.readLine();
                                    //char[] pubKey = new char[keylength];
                                    //inFromClient.read(pubKey, 0, keylength);
                                    if(saveSender(headerLine1_ARR[3], readSomeByte(keyLength, inFromClient))){
                                        currentUser=headerLine1_ARR[3];
                                        outToClient.writeBytes("REGISTERED TOSEND "+headerLine1_ARR[3]+"\n\n");
                                    }else{
                                        outToClient.writeBytes("ERROR 111 "+headerLine1_ARR[3]+"\n\n");
                                    }
                                }
                                else{
                                    inFromClient.readLine();
                                    if(saveSender(headerLine1_ARR[3])){
                                        currentUser=headerLine1_ARR[3];
                                        outToClient.writeBytes("REGISTERED TOSEND "+headerLine1_ARR[3]+"\n\n");
                                    }else{
                                        outToClient.writeBytes("ERROR 111 "+headerLine1_ARR[3]+"\n\n");
                                    }
                                }
                                
                            }else{
                                if(!mode.equals("1")){
                                    int keyLength = Integer.parseInt(inFromClient.readLine()); 
                                    inFromClient.readLine();
                                    //char[] pubKey = new char[keylength];
                                    //inFromClient.read(pubKey, 0, keylength);
                                    readSomeByte(keyLength, inFromClient);
                                }
                                else{
                                    inFromClient.readLine();
                                }
                                outToClient.writeBytes("ERROR 100 "+headerLine1_ARR[3]+"\n\n");
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
                        try {
                            switch (mode) {
                                case "1":
                                    
                                    forwardMessage(headerLine1_ARR[1]);
                                    break;
                                
                                case "2":
                                    forwardMessage(headerLine1_ARR[1]);
                                    
                                    break;
                                case "3":
                                    forwardMessageWithSignature(headerLine1_ARR[1]);
    
                                    break;
                                  
                                default:
                                    break;
                            }
                            break;
                        } catch (Exception e) {
                            e.printStackTrace();
                            break;
                        }
                    default:
                        outToClient.writeBytes("ERROR 101 No user registered\n\n");
                        break;
                }

            } catch(Exception i) {  //IOException | NullPointerException
                if(currentUser!=null){
                    if(server.database.get(currentUser)!=null){
                        server.database.remove(currentUser);
                        System.out.println("User "+currentUser+" Abruptly Disconnected");
                    }
                }           
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
    
    boolean saveSender(String userName){

        //Socket socket=server.sendingSockets.get(userName);
        Socket sendSocket;
        Hashtable<String, Object> table = server.database.get(userName);
        if(table==null){
            table = new Hashtable<>();
            table.put("sendSocket",clientSocket);;
            server.database.put(userName,table);
            return true;
        }
        else{
            sendSocket = (Socket)server.database.get(userName).get("sendSocket");
            if(sendSocket==null){
                //server.sendingSockets.put(userName, clientSocket);
                table.put("sendSocket",clientSocket);
                return true;
            }
            
        }
        return false;
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



