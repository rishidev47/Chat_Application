import java.net.*;
import java.io.*;

public class TCPClient{
    private Socket sendSocket = null;
    private Socket receiveSocket = null; 
    private int serverPort=6789;
    BufferedReader inFromUser=null;
    BufferedReader inFromServerSEND=null;
    BufferedReader inFromServerRECV=null;
    private DataOutputStream outToSEND = null;
    private DataOutputStream outToRECV = null;
    private String address="localhost";


    public TCPClient() {
        try { 
            sendSocket = new Socket(address, serverPort);
            inFromUser = new BufferedReader(new InputStreamReader(System.in));
            inFromServerSEND=new BufferedReader(new InputStreamReader(sendSocket.getInputStream()));
            outToSEND    = new DataOutputStream(sendSocket.getOutputStream());

            receiveSocket = new Socket(address, serverPort);
            inFromServerRECV =new BufferedReader(new InputStreamReader(receiveSocket.getInputStream()));
            outToRECV = new DataOutputStream(receiveSocket.getOutputStream());
        } catch(UnknownHostException u) { 
            System.out.println(u); 
        } catch(IOException i) { 
            System.out.println(i); 
        }
    }
    
    void register() throws IOException{
        String userName = ""; 
        boolean reg=false;
        while(!reg){
            System.out.print("Username: ");
            userName=inFromUser.readLine().trim();
            reg=registerToSend(userName);
        }
        if(reg){
            reg=registerToReceive(userName);
        }
        if(reg){
            System.out.println("Registered sucessfully as "+userName+'\n'+"Now you can send message to any user like this: \"@username message\" and then click enter to send");
            ReceiveMessages rec=new ReceiveMessages(receiveSocket, inFromServerRECV, outToRECV);
            rec.start();
            send();
        }      
    }

    boolean registerToSend(String userName) throws IOException{
        boolean ret=false;
        String received= "";
        String send = "REGISTER TOSEND "+userName;
        String[] servermessage;
        outToSEND.writeBytes(send+'\n'+'\n');
        received=inFromServerSEND.readLine();
        servermessage=received.split(" ");
        switch(servermessage[0]+" "+servermessage[1]){
            case "REGISTERED TOSEND":
                inFromServerSEND.readLine();
                ret=true;
                break;
            case "ERROR 100":
                inFromServerSEND.readLine();
                System.out.println("Failed to register : Username "+servermessage[2]+" not well formatted. Usernames can only contain alphabets and numbers without spaces");
                ret=false;
                break;
            case "ERROR 111":
                inFromServerSEND.readLine();
                System.out.println("Failed to register : Username "+servermessage[2]+" already registered. Register again with different username");
                ret=false;
                break;
        }
        return ret;
    }
    
    boolean registerToReceive(String userName) throws IOException{
        boolean ret=false;
        String received= "";
        String send = "REGISTER TORECV "+userName;
        String[] servermessage;
        outToRECV.writeBytes(send+'\n'+'\n');
        received=inFromServerRECV.readLine();
        servermessage=received.split(" ");
        switch(servermessage[0]+" "+servermessage[1]){
            case "REGISTERED TORECV":
                inFromServerRECV.readLine();
                ret=true;
                break;
        }
        return ret;
    }
    
    void send() throws IOException{

        while(true){
            String ipnutfromuser=inFromUser.readLine();
            String[] userResponse_ARR=ipnutfromuser.split(" ");
            if(ipnutfromuser.charAt(0)=='@'&& userResponse_ARR.length>=2){

                userResponse_ARR=ipnutfromuser.split(" ",2);
                String recipient=userResponse_ARR[0].replace("@","");
                String message=userResponse_ARR[1].trim();
                int contentLength=message.length();

                String send="SEND "+recipient+"\n"+
                            "Content-length: "+contentLength+"\n\n"+
                            message;
                
                            outToSEND.writeBytes(send);

                String serverResponse=inFromServerSEND.readLine(); inFromServerSEND.readLine();
                String[] serverResponse_ARR=serverResponse.split(" ");

                switch (serverResponse_ARR[0]) {
                    case "SENT":
                        System.out.println("Message sent successfully to "+recipient);
                        break;
                    case "ERROR":
                        if(serverResponse_ARR[1].equals("102")){
                            System.out.println("Unable to send message to "+recipient);
                        }
                        else if(serverResponse_ARR[1].equals("103")){
                            System.out.println("Header Incomplete");
                        }
                        break;
                    default:
                        break;
                }                  
            }
            else{
                System.out.println("Badly formatted send statement");
            }
        }
    }
    
    public static void main(String argv[]) throws Exception 
    {
        TCPClient client = new TCPClient();
        client.register();
                   
    } 
}

class ReceiveMessages extends Thread{
    private Socket receiveSocket = null;
    BufferedReader inFromServerRECV=null;
    private DataOutputStream outToRECV = null;
    public ReceiveMessages(Socket receiveSocket, BufferedReader inFromServerRECV, DataOutputStream outToRECV){
        this.receiveSocket=receiveSocket;
        this.inFromServerRECV=inFromServerRECV;
        this.outToRECV=outToRECV;
    }
    @Override
    public void run() {

        while(true){
            try{
                String[] headerLine1_ARR = inFromServerRECV.readLine().split(" ");

                if(headerLine1_ARR[0].equals("FORWARD")){
                    String[] headerLine2_ARR = inFromServerRECV.readLine().split(" "); inFromServerRECV.readLine();
                    int contentLength=Integer.parseInt(headerLine2_ARR[1]);

                    char[] temp=new char[contentLength]; inFromServerRECV.read(temp, 0, contentLength);
                    String messageContent= String.valueOf(temp);
                    
                    System.out.println("Message From "+headerLine1_ARR[1]+": "+messageContent);
                    outToRECV.writeBytes("RECEIVED "+headerLine1_ARR[1]+"\n\n");

                }
                else{

                }

            } catch(IOException ex){
                System.out.println(ex);
            }
        }
    }
}