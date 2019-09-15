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

public class TCPClient {
    private String username;
    private String mode;
    private Socket sendSocket = null;
    private Socket receiveSocket = null;
    private int serverPort ;
    BufferedReader inFromUser = null;
    BufferedReader inFromServerSEND = null;
    BufferedReader inFromServerRECV = null;
    private DataOutputStream outToSEND = null;
    private DataOutputStream outToRECV = null;
    private String serverIP = "localhost";

    private KeyPair generateKeyPair = null; // = generateKeyPair();
    private byte[] publicKey = null; // = generateKeyPair.getPublic().getEncoded();
    private byte[] privateKey = null; // = generateKeyPair.getPrivate().getEncoded();
    private static String s=
    "'   .----------------. .----------------. .----------------. .----------------. .----------------. .----------------. .----------------. .----------------. \n"+
    "'  | .--------------. | .--------------. | .--------------. | .--------------. | .--------------. | .--------------. | .--------------. | .--------------. |\n"+
    "'  | |     ______   | | |  ____  ____  | | |      __      | | |  _________   | | |              | | |      __      | | |   ______     | | |   ______     | |\n"+
    "'  | |   .' ___  |  | | | |_   ||   _| | | |     / \\     | | | |  _   _  |  | | |              | | |     /  \\     | | |  |_   __ \\   | | |  |_   __ \\   | |\n"+
    "'  | |  / .'   \\_|  | | |   | |__| |   | | |    / /\\\\    | | | |_/ | |  \\_|  | | |    ______    | | |   / /\\\\    | | |    | |__) |  | | |    | |__) |  | |\n"+
    "'  | |  | |         | | |   |  __  |   | | |   / ____\\\\   | | |     | |      | | |   |______|   | | |   / ____ \\   | | |    |  ___/   | | |    |  ___/   | |\n"+
    "'  | |  \\ `.___.'\\  | | |  _| |  | |_  | | | _/ /   \\\\_ | | |    _| |_     | | |              | | | _/ /    \\ \\_ | | |   _| |_      | | |   _| |_      | |\n"+
    "'  | |   `._____.'  | | | |____||____| | | ||____|  |____|| | |   |_____|    | | |              | | ||____|  |____|| | |  |_____|     | | |  |_____|     | |\n"+
    "'  | |              | | |              | | |              | | |              | | |              | | |              | | |              | | |              | |\n"+
    "'  | '--------------' | '--------------' | '--------------' | '--------------' | '--------------' | '--------------' | '--------------' | '--------------' |\n"+
    "'   '----------------' '----------------' '----------------' '----------------' '----------------' '----------------' '----------------' '----------------' \n";


    public TCPClient(String username, String serverIP, BufferedReader inFromUser, String mode, int serverPort) {
        this.serverPort=serverPort;
        this.serverIP = serverIP;
        this.username = username;
        this.inFromUser = inFromUser;
        this.mode = mode;
        try {
            sendSocket = new Socket(serverIP, serverPort);
            inFromServerSEND = new BufferedReader(new InputStreamReader(sendSocket.getInputStream()));
            outToSEND = new DataOutputStream(sendSocket.getOutputStream());

            receiveSocket = new Socket(serverIP, serverPort);
            inFromServerRECV = new BufferedReader(new InputStreamReader(receiveSocket.getInputStream()));
            outToRECV = new DataOutputStream(receiveSocket.getOutputStream());
        } catch (UnknownHostException u) {
            System.out.println("Server Not Found");
            System.exit(0);
        } catch (ConnectException i) {
            System.out.println("Server Is Down");
            System.exit(0);
        } catch (NoRouteToHostException i) {
            System.out.println("Server Is Unreachable");
            System.exit(0);
        }catch (IOException i){
            System.exit(0);
        }
    }

    void register() {
        if (mode.equals("2") || mode.equals("3")) {
            try {
                generateKeyPair = CryptographyExample.generateKeyPair();
                publicKey = generateKeyPair.getPublic().getEncoded();
                privateKey = generateKeyPair.getPrivate().getEncoded();
            } catch (Exception e) {
                System.out.println("Encryption Algorithm Not Found. Switching to Unencrypted Mode");
                mode = "1";
            }
        }
        boolean reg = false;
        try {
            reg = registerToSend(username);
            while (!reg) {
                System.out.print("Username: ");
                username = inFromUser.readLine().trim();
                if (username.equals(""))
                    continue;
                reg = registerToSend(username);
            }
            if (reg) {
                reg = registerToReceive(username);
            }
            if (reg) {
                System.out.println("Registered sucessfully as " + username + '\n'
                        + "Now you can send message to any user like this: \"@username message\" and then click enter to send");
                ReceiveMessages rec = new ReceiveMessages(receiveSocket, inFromServerRECV, outToRECV, privateKey, mode, username);
                rec.start();
                try {
                    send();
                } catch (Exception ex) {
                    System.out.println("Server Down");
                    System.exit(0);
                }

            }
        } catch (Exception ex) {
            System.out.println("Server Down");
            System.exit(0);

        }

    }

    boolean registerToSend(String username) throws IOException {
        boolean ret = false;
        String send;
        if (mode.equals("2") || mode.equals("3")) {
            String pubKey = java.util.Base64.getEncoder().encodeToString(publicKey);
            int pubKeyLength = pubKey.length();
            send = "REGISTER TOSEND " +mode+" "+ username +"\n" + 
                    pubKeyLength + "\n\n" + 
                    pubKey;
        } else {
            send = "REGISTER TOSEND "+mode+" " + username + "\n\n";
        }
        outToSEND.writeBytes(send);
        String[] servermessage = inFromServerSEND.readLine().split(" ");

        switch (servermessage[0] + " " + servermessage[1]) {
        case "REGISTERED TOSEND":
            inFromServerSEND.readLine();
            ret = true;
            break;
        case "ERROR 100":
            inFromServerSEND.readLine();
            System.out.println("Failed to register : Username " + servermessage[2]
                    + " not well formatted. Usernames can only contain alphabets and numbers without spaces");
            ret = false;
            break;
        case "ERROR 111":
            inFromServerSEND.readLine();
            System.out.println("Failed to register : Username " + servermessage[2]
                    + " already registered. Register again with different username");
            ret = false;
            break;

        case "ERROR 120":
            inFromServerSEND.readLine();
            String serverMode=servermessage[4];
            System.out.println("Server is Running in diffrent mode : "+serverMode+" ");
            System.out.println("Exiting Application");
            ret=false;
            System.exit(0);
            break;
        }
        return ret;
    }

    boolean registerToReceive(String username) throws IOException {
        boolean ret = false;
        String received = "";
        String send = "REGISTER TORECV " + username;
        String[] servermessage;
        outToRECV.writeBytes(send + "\n\n");
        received = inFromServerRECV.readLine();
        servermessage = received.split(" ");
        switch (servermessage[0] + " " + servermessage[1]) {
        case "REGISTERED TORECV":
            inFromServerRECV.readLine();
            ret = true;
            break;
        }
        return ret;
    }

    byte[] fetchKey(String recipient) throws IOException {
        byte[] pubKey = null;
        String reqToFetchKey = "FETCHKEY " + recipient + "\n\n";
        outToSEND.writeBytes(reqToFetchKey);

        String[] fetchKeyResponse = inFromServerSEND.readLine().split(" ");
        
        if (fetchKeyResponse.length == 1) {
            int keyLength = Integer.parseInt(fetchKeyResponse[0]);
            //char[] encodedPubKey = new char[keyLength];
            int temp=0;
            String enPubKey="";
            while(temp!=keyLength){
                int t = inFromServerSEND.read();
                if(t!=-1){
                    char c=(char)t;
                    enPubKey=enPubKey+c;
                    temp++;
                }
            }
            //System.out.println(inFromServerSEND.read(encodedPubKey, 0, keyLength));
            // pubKey = java.util.Base64.getDecoder().decode(new String(encodedPubKey));
            pubKey = java.util.Base64.getDecoder().decode(enPubKey);
        } else {
            return pubKey;
        }

        return pubKey;
    }

    String sendWithEncryption(byte[] pubKey, String recipient, String message) throws Exception {
        byte[] encryptedMessage = CryptographyExample.encrypt(pubKey, message.getBytes());
        String encodedContent = java.util.Base64.getEncoder().encodeToString(encryptedMessage);
        int contentLength = encodedContent.length();

        String send = "SEND " + recipient + "\n" + "Content-length: " + contentLength + "\n\n" + encodedContent;
        return send;
    }

    String sendWithEncryptionAndSignature(byte[] pubKey, String recipient, String message) throws Exception {
        byte[] encryptedMessage = CryptographyExample.encrypt(pubKey, message.getBytes());
        String encodedContent = java.util.Base64.getEncoder().encodeToString(encryptedMessage);
        int contentLength = encodedContent.length();

        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] shaBytes = md.digest(encryptedMessage);
        byte[] encryptedSignature = CryptographySignatureExample.encrypt(privateKey, shaBytes);
        String encodedSignature = java.util.Base64.getEncoder().encodeToString(encryptedSignature);
        int sigContentLenght = encodedSignature.length();

        String send = "SEND " + recipient + "\n" + "Content-length: " + contentLength + "\n" + "signature-lenght: "
                + sigContentLenght + "\n\n" + encodedContent + encodedSignature;

        return send;
    }

    String sendWithoutEncryption(String recipient, String message) {
        int contentLength = message.length();
        String send = "SEND " + recipient + "\n" + "Content-length: " + contentLength + "\n\n" + message;
        return send;
    }

    void send() throws IOException {
        boolean sending = true;
        while (sending) {
            String inputfromuser = inFromUser.readLine();
            if (inputfromuser.equals("")){
                continue;
            }
            if(inputfromuser.equals("unregister")){
                outToSEND.writeBytes("UNREGISTER "+username+"\n\n");
                System.exit(0);
            }
            String[] userResponse_ARR = inputfromuser.split(" ");

            if (inputfromuser.charAt(0) == '@' && userResponse_ARR.length >= 2 && inputfromuser.charAt(1) != '@' && inputfromuser.charAt(1) != ' ') {
                userResponse_ARR = inputfromuser.split(" ",2);
                if(userResponse_ARR[1].length() > 53 && !mode.equals("1")){
                    System.out.println("Can Not Send Encrypted Message Greater than 53 Character In One Message");
                    continue;
                }
                String recipient = userResponse_ARR[0].replace("@", "");
                String message = userResponse_ARR[1].trim();
                String send = null;
                try {
                    switch (mode) {
                    case "1":
                        send = sendWithoutEncryption(recipient, message);
                        break;

                    case "2":
                        byte[] pubKey_2 = fetchKey(recipient);
                        if (pubKey_2 == null) {
                            System.out.println("Unable to fetch key of " + recipient);
                            continue;
                        }
                        send = sendWithEncryption(pubKey_2, recipient, message);
                        break;

                    case "3":
                        byte[] pubKey_3 = fetchKey(recipient);
                        if (pubKey_3 == null) {
                            System.out.println("Unable to fetch key of " + recipient);
                            continue;
                        }
                        send = sendWithEncryptionAndSignature(pubKey_3, recipient, message);
                        break;

                    default:
                        break;
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("Can Not Encrypt Message. Try Again or Restart Application with Unencrypted Mode");
                    continue;
                }
                outToSEND.writeBytes(send);
                String[] serverResponse_ARR = inFromServerSEND.readLine().split(" ");
                inFromServerSEND.readLine();
                switch (serverResponse_ARR[0]) {
                    case "SENT":
                        System.out.println("Message sent successfully to " + serverResponse_ARR[1]);
                        break;
                    case "ERROR":
                        if (serverResponse_ARR[1].equals("102")) {
                            System.out.println("Unable to send message to " + recipient);
                        } else if (serverResponse_ARR[1].equals("103")) {
                            System.out.println("Header Incomplete");
                        } else if (serverResponse_ARR[1].equals("106")) {
                            System.out.println("Message Tempered");
                        }
                        break;
                    default:
                        break;
                }

            } else {
                System.out.println("Badly formatted send statement");
            }
        }
    }

    public static void main(String argv[]) throws Exception {
        System.out.println("Enter the number corrosponding to the mode in which you want to run the application\n"+
                            "1. Unencrypted\n2. Encrypted\n3. Encrypted with Signature");
        BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));
        String mode = "";
        String username = "";
        String serverIP = "";
        
        while (true) {
            System.out.print("Mode: ");
            mode = inFromUser.readLine().trim();
            if (mode.equals("1") || mode.equals("2") || mode.equals("3"))
                break;
            else
                continue;
        }
        while (true) {
            System.out.print("Username: ");
            username = inFromUser.readLine().trim();
            if (username.equals(""))
                continue;
            else
                break;
        }
        while (true) {
            System.out.print("Server IP address: ");
            serverIP = inFromUser.readLine().trim();
            if (serverIP.equals(""))
                continue;
            else
                break;
        }
        System.out.println("You Can Terminate this application anytime by typing \"unregister\" without quotes and pressing enter.\n");
        TCPClient client = new TCPClient(username, serverIP, inFromUser, mode, 6789);
        client.register();

    }
}

class ReceiveMessages extends Thread {
    private String username =null;
    private String mode = null;
    private byte[] privateKey = null;
    private Socket receiveSocket = null;
    BufferedReader inFromServerRECV = null;
    private DataOutputStream outToRECV = null;

    public ReceiveMessages(Socket receiveSocket, BufferedReader inFromServerRECV, DataOutputStream outToRECV,
            byte[] privateKey, String mode, String username) {
        this.username = username;
        this.mode = mode;
        this.privateKey = privateKey;
        this.receiveSocket = receiveSocket;
        this.inFromServerRECV = inFromServerRECV;
        this.outToRECV = outToRECV;
    }

    byte[] fetchKey(String recipient) throws IOException {
        byte[] pubKey = null;
        String reqToFetchKey = "FETCHKEY " + recipient + "\n\n";
        outToRECV.writeBytes(reqToFetchKey);

        String[] fetchKeyResponse = inFromServerRECV.readLine().split(" ");

        if (fetchKeyResponse.length == 1) {
            int keyLength = Integer.parseInt(fetchKeyResponse[0]);
            // char[] encodedPubKey = new char[keyLength];
            // inFromServerRECV.read(encodedPubKey, 0, keyLength);

            pubKey = java.util.Base64.getDecoder().decode(readSomeByte(keyLength, inFromServerRECV));
        } else {
            return pubKey;
        }

        return pubKey;
    }
    
    boolean isMessageIntegrated(byte[] decryptedSignature, byte[] decodedContent) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] shaBytes = md.digest(decodedContent);
        return(Arrays.equals(decryptedSignature, shaBytes));        

    }
    
    void getUnencryptedMessage(String length, String sender) throws IOException {
        String message=null;
        int contentLength = Integer.parseInt(length);
        inFromServerRECV.readLine();
        //char[] temp = new char[contentLength];
        //inFromServerRECV.read(temp, 0, contentLength);
        message = readSomeByte(contentLength, inFromServerRECV);
        System.out.println("Message from "+sender+" : "+message);
        outToRECV.writeBytes("RECEIVED " + sender + "\n\n");
    }

    void getEncryptedMessage(String length, String sender) throws IOException {
        inFromServerRECV.readLine();
        String message=null;
        int contentLength = Integer.parseInt(length);
        //char[] temp = new char[contentLength];
        //inFromServerRECV.read(temp, 0, contentLength);

        byte[] decodedContent = java.util.Base64.getDecoder().decode(readSomeByte(contentLength,inFromServerRECV));
        try {
            message = new String(CryptographyExample.decrypt(privateKey, decodedContent));
        } catch (Exception e) {
            message=null;
        }
        if(message!=null){
            System.out.println("Message from "+sender+" : "+message);
            outToRECV.writeBytes("RECEIVED " + sender + "\n\n");
            return;
        }
        else{
            
        }
    }

    void getEncryptedMessageWithSignature(String length, String sender) throws IOException {
        String[] headerLine3_ARR = inFromServerRECV.readLine().split(" ");
        inFromServerRECV.readLine();     
        if(headerLine3_ARR.length!=2){
            outToRECV.writeBytes("ERROR 103 Header incomplete\n\n");
            return;
        }
        String message=null;
        int contentLength = Integer.parseInt(length);
        int sigContentLength = Integer.parseInt(headerLine3_ARR[1]);
        
        byte[] decodedContent = java.util.Base64.getDecoder().decode(readSomeByte(contentLength, inFromServerRECV));
        byte[] decodedSignature = java.util.Base64.getDecoder().decode(readSomeByte(sigContentLength, inFromServerRECV));
        byte[] pubKey = fetchKey(sender);
        byte[] decryptedSignature = null;
        try {
            decryptedSignature = CryptographySignatureExample.decrypt(pubKey, decodedSignature);
            if(isMessageIntegrated(decryptedSignature, decodedContent)){
                message = new String(CryptographyExample.decrypt(privateKey, decodedContent));
            }
            else{
                outToRECV.writeBytes("ERROR 105 Message Tempered\n\n");
                return;
            }
        } catch (Exception e) {
            message=null;
        }
        if(message!=null){
            System.out.println("Message from "+sender+" : "+message);
            outToRECV.writeBytes("RECEIVED " + sender + "\n\n");
            return;
        }
        else{
            
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
                String[] headerLine1_ARR = inFromServerRECV.readLine().split(" ");
                if (headerLine1_ARR[0].equals("FORWARD")) {
                    
                    String[] headerLine2_ARR = inFromServerRECV.readLine().split(" ");
                    
                    if(headerLine2_ARR.length!=2){
                        try {
                            outToRECV.writeBytes("ERROR 103 Header incomplete\n\n");
                        } catch (IOException e) {
                            System.out.println("Server Down");
                            System.exit(0);
                        }
                        continue;                       
                    }
                    try {
                        switch (mode) {
                        case "1":
                            getUnencryptedMessage(headerLine2_ARR[1], headerLine1_ARR[1]);
                            break;
    
                        case "2":
                            getEncryptedMessage(headerLine2_ARR[1], headerLine1_ARR[1]);
                            break;
    
                        case "3":
                            getEncryptedMessageWithSignature(headerLine2_ARR[1], headerLine1_ARR[1]);
                            break;
    
                        default:
                            break;
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                        continue;
                    }

                } else {

                }

            } catch (IOException | NullPointerException ex) {
                System.out.println("Server Down");
                System.exit(0);
            }
        }
    }
}

class CryptographyExample {

    private static final String ALGORITHM = "RSA";

    public static byte[] encrypt(byte[] publicKey, byte[] inputData) throws Exception {

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
}