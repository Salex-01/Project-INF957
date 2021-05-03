import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class Client extends Thread {
    public static void main(String[] args) throws IOException {
        new Client(args).start();
    }

    public Client(String[] args) throws IOException {
        String address = null;
        int port = 8666;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "host":
                    address = args[i + 1];
                    i++;
                    break;
                case "port":
                    port = Integer.parseInt(args[i + 1]);
                    i++;
                    break;
                default:
                    System.out.println("Argument inconnu : " + args[i]);
                    System.exit(-1);
            }
        }
        Socket s = new Socket(address, port);
        DataInputStream is = new DataInputStream(new DataInputStream(s.getInputStream()));
        DataOutputStream os = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
        String[] testMessages = {
                "new account" + Common.Constants.separator + "test",
                "new account" + Common.Constants.separator + "test",
                "new account" + Common.Constants.separator + "test2",
                "follow" + Common.Constants.separator + "test2" + Common.Constants.separator + "test",
                "post" + Common.Constants.separator + "test" + Common.Constants.separator + "ceci est un message",
                "get" + Common.Constants.separator + "test2" + Common.Constants.separator + "0",
                "unfollow" + Common.Constants.separator + "test2" + Common.Constants.separator + "test",
                "delete account" + Common.Constants.separator + "test"
        };
        for (String message : testMessages) {
            Network.send(message, os, false);
            System.out.println(Network.getMessage(is, false));
        }
    }
}