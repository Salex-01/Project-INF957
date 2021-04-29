import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class Client extends Thread {
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
        String[] testMessages = {"post", "get", "fsdjfjsh"};
        for (String message : testMessages) {
            os.writeInt(message.getBytes(StandardCharsets.UTF_8).length);
            os.writeBytes(message);
            os.flush();
        }
    }
}