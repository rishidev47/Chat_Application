import java.net.*;
import java.io.*;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.security.MessageDigest;

import javax.crypto.Cipher;

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

    private KeyPair generateKeyPair; //= generateKeyPair();
	private byte[] publicKey; //= generateKeyPair.getPublic().getEncoded();
    private byte[] privateKey; //= generateKeyPair.getPrivate().getEncoded();


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
    
    void register() throws Exception{

        generateKeyPair = CryptographyExample.generateKeyPair();
	    publicKey = generateKeyPair.getPublic().getEncoded();
        privateKey = generateKeyPair.getPrivate().getEncoded();

        String userName = ""; 
        boolean reg=false;
        try{
            while(!reg){
                System.out.print("Username: ");
                userName=inFromUser.readLine().trim();
                if(userName.equals(""))continue;
                reg=registerToSend(userName);
            }
            if(reg){
                reg=registerToReceive(userName);
            }
            if(reg){
                System.out.println("Registered sucessfully as "+userName+'\n'+"Now you can send message to any user like this: \"@username message\" and then click enter to send");
                ReceiveMessages rec=new ReceiveMessages(receiveSocket, inFromServerRECV, outToRECV, privateKey);
                rec.start();
                try{
                    send();
                }catch(IOException | NullPointerException ex){
                    System.out.println("Server Down");
                    rec.stop();
                    Thread.currentThread().stop();
                }
                
            }  
        }catch(Exception ex){
            Thread.currentThread().stop();

        }
            
    }

    boolean registerToSend(String userName) throws IOException{
        boolean ret=false;
        String pubKey = java.util.Base64.getEncoder().encodeToString(publicKey);
        int pubKeyLength = pubKey.length();
        String send = "REGISTER TOSEND "+userName+"\n"+
                        pubKeyLength+"\n\n"+
                        pubKey;

        outToSEND.writeBytes(send);

        String[] servermessage = inFromServerSEND.readLine().split(" ");
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
        outToRECV.writeBytes(send+"\n\n");
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
    
    void send() throws Exception{

        while(true){
            String inputfromuser=inFromUser.readLine();
            if(inputfromuser.equals(""))continue;
            String[] userResponse_ARR=inputfromuser.split(" ",2);
            if(inputfromuser.charAt(0)=='@'&& userResponse_ARR.length==2 && inputfromuser.charAt(1)!='@' && userResponse_ARR[1].length()<=53){

                userResponse_ARR=inputfromuser.split(" ",2);
                String recipient=userResponse_ARR[0].replace("@","");
                String message=userResponse_ARR[1].trim();
                // int contentLength=message.length();

                String reqToFetchKey="FETCHKEY "+recipient+"\n\n";
                outToSEND.writeBytes(reqToFetchKey);
                String[] fetchKeyResponse =inFromServerSEND.readLine().split(" ");
                if(fetchKeyResponse.length==1){
                    int keyLength=Integer.parseInt(fetchKeyResponse[0]);
                    char[] encodedPubKey = new char[keyLength];
                    inFromServerSEND.read(encodedPubKey, 0, keyLength);

                    byte[] pubKey = java.util.Base64.getDecoder().decode(new String(encodedPubKey));
                    byte[] encryptedMessage = CryptographyExample.encrypt(pubKey, message.getBytes());
                    String encodedContent = java.util.Base64.getEncoder().encodeToString(encryptedMessage);
                    int contentLength =encodedContent.length();

                    MessageDigest md = MessageDigest.getInstance("SHA-256");
                    byte[] shaBytes = md.digest(encryptedMessage);
                    byte[] encryptedSignature=null;
                    
                    try {
                        encryptedSignature = CryptographySignatureExample.encrypt(privateKey, shaBytes);
                    } catch (Exception e) {
                        System.out.println(e);
                    }
                    String encodedSignature = java.util.Base64.getEncoder().encodeToString(encryptedSignature);
                    int sigContentLenght =encodedSignature.length();
                    
                    String send="SEND "+recipient+"\n"+
                                "Content-length: "+contentLength+"\n"+
                                "signature-lenght: "+sigContentLenght+"\n\n"+
                                encodedContent + encodedSignature;

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
                            else if(serverResponse_ARR[1].equals("106")){
                                System.out.println("Message Tempered");
                            }
                            break;
                        default:
                            break;
                    }                  
                }
                else{
                    System.out.println("Unable to fetch key of "+recipient);
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
    private byte[] privateKey;
    private Socket receiveSocket = null;
    BufferedReader inFromServerRECV=null;
    private DataOutputStream outToRECV = null;
    public ReceiveMessages(Socket receiveSocket, BufferedReader inFromServerRECV, DataOutputStream outToRECV, byte[] privateKey){
        this.privateKey=privateKey;
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

                    String[] headerLine2_ARR = inFromServerRECV.readLine().split(" "); 
                    String[] headerLine3_ARR = inFromServerRECV.readLine().split(" ");

                    int contentLength=Integer.parseInt(headerLine2_ARR[1]);
                    int sigContentLength= Integer.parseInt(headerLine3_ARR[1]);
                    inFromServerRECV.readLine();

                    char[] temp=new char[contentLength]; inFromServerRECV.read(temp, 0, contentLength);
                    char[] temp1=new char[sigContentLength]; inFromServerRECV.read(temp1, 0, sigContentLength);
                    byte[] decodedContent= java.util.Base64.getDecoder().decode(new String(temp));
                    byte[] decodedSignature = java.util.Base64.getDecoder().decode(new String(temp1));
                    String messageContent="";
 
                    String reqToFetchKey="FETCHKEY "+headerLine1_ARR[1]+"\n\n";
                    
                    outToRECV.writeBytes(reqToFetchKey);

                    

                    String[] fetchKeyResponse = inFromServerRECV.readLine().split(" ");
                    int keyLength=Integer.parseInt(fetchKeyResponse[0]);
                    char[] encodedPubKey = new char[keyLength];
                    inFromServerRECV.read(encodedPubKey, 0, keyLength);
                    
                    byte[] pubKey = java.util.Base64.getDecoder().decode(new String(encodedPubKey));
                    
                    byte[] decryptedSignature=null;
                    try {
                        decryptedSignature = CryptographySignatureExample.decrypt(pubKey, decodedSignature);
                    } catch (Exception e) {
                        System.out.println(e);
                    }
                    
                    boolean messageIsIntegrated=false;
                    try {
                        MessageDigest md = MessageDigest.getInstance("SHA-256");
                        byte[] shaBytes = md.digest(decodedContent);
                        messageIsIntegrated = Arrays.equals(decryptedSignature, shaBytes);
                    } catch (Exception e) {
                        System.out.println(e);
                    }
                    

                    if(messageIsIntegrated){
                        try {
                            messageContent = new String(CryptographyExample.decrypt(privateKey, decodedContent));
                            System.out.println("Message From "+headerLine1_ARR[1]+": "+messageContent);
                            outToRECV.writeBytes("RECEIVED "+headerLine1_ARR[1]+"\n\n");
                        } catch (Exception e) {
                            System.out.println(e);
                        }
                    }
                    else{
                        outToRECV.writeBytes("ERROR 105 Message Tempered\n\n");
                    }
                    

                }
                else{

                }

            } catch(IOException | NullPointerException ex){
                this.stop();
            }
        }
    }
}

class CryptographyExample {

    private static final String ALGORITHM = "RSA";

    public static byte[] encrypt(byte[] publicKey, byte[] inputData)throws Exception {

        PublicKey key = KeyFactory.getInstance(ALGORITHM).generatePublic(new X509EncodedKeySpec(publicKey));
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encryptedBytes = cipher.doFinal(inputData);
        return encryptedBytes;
    }

    public static byte[] decrypt(byte[] privateKey, byte[] inputData)throws Exception {

        PrivateKey key = KeyFactory.getInstance(ALGORITHM).generatePrivate(new PKCS8EncodedKeySpec(privateKey));
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, key);
        byte[] decryptedBytes = cipher.doFinal(inputData);
        return decryptedBytes;
    }

    public static KeyPair generateKeyPair()throws NoSuchAlgorithmException, NoSuchProviderException {

        KeyPairGenerator keyGen = KeyPairGenerator.getInstance(ALGORITHM);
        SecureRandom random = SecureRandom.getInstance("SHA1PRNG", "SUN");
        keyGen.initialize(512, random);
        KeyPair generateKeyPair = keyGen.generateKeyPair();
        return generateKeyPair;
    }

    // public static void main(String[] args) throws Exception {

    //     KeyPair generateKeyPair = generateKeyPair();
	//     byte[] publicKey = generateKeyPair.getPublic().getEncoded();
    //     byte[] privateKey = generateKeyPair.getPrivate().getEncoded();

    //     byte[] encryptedData = encrypt(publicKey,
    //             ("Hey").getBytes());
                
    //     byte[] decryptedData = decrypt(privateKey, encryptedData);

    //     System.out.println(new String(encryptedData));
    //     System.out.println(new String(decryptedData));


    // }

}

class CryptographySignatureExample {

    private static final String ALGORITHM = "RSA";

    public static byte[] encrypt(byte[] privateKey, byte[] inputData)throws Exception {

        PrivateKey key = KeyFactory.getInstance(ALGORITHM).generatePrivate(new PKCS8EncodedKeySpec(privateKey));
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encryptedBytes = cipher.doFinal(inputData);
        return encryptedBytes;
    }

    public static byte[] decrypt(byte[] publicKey, byte[] inputData)throws Exception {

        PublicKey key = KeyFactory.getInstance(ALGORITHM).generatePublic(new X509EncodedKeySpec(publicKey));
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, key);
        byte[] decryptedBytes = cipher.doFinal(inputData);
        return decryptedBytes;
    }

    public static KeyPair generateKeyPair()throws NoSuchAlgorithmException, NoSuchProviderException {

        KeyPairGenerator keyGen = KeyPairGenerator.getInstance(ALGORITHM);
        SecureRandom random = SecureRandom.getInstance("SHA1PRNG", "SUN");
        keyGen.initialize(512, random);
        KeyPair generateKeyPair = keyGen.generateKeyPair();
        return generateKeyPair;
    }

    // public static void main(String[] args) throws Exception {

    //     KeyPair generateKeyPair = generateKeyPair();
	//     byte[] publicKey = generateKeyPair.getPublic().getEncoded();
    //     byte[] privateKey = generateKeyPair.getPrivate().getEncoded();

    //     byte[] encryptedData = encrypt(publicKey,
    //             ("Hey").getBytes());
                
    //     byte[] decryptedData = decrypt(privateKey, encryptedData);

    //     System.out.println(new String(encryptedData));
    //     System.out.println(new String(decryptedData));


    // }

}