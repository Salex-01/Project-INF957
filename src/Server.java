import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class Server extends Thread {
    ServerSocket server;
    boolean log = false;

    public Server(String[] args) throws IOException {
        int port = 8666;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "port":
                    port = Integer.parseInt(args[i + 1]);
                    i++;
                    break;
                case "log":
                    log = true;
                    break;
                default:
                    System.out.println("Argument inconnu : " + args[i]);
                    System.exit(-1);
            }
        }
        server = new ServerSocket(port);
    }

    @Override
    @SuppressWarnings("InfiniteLoopStatement")
    public void run() {
        while (true) {
            try {
                new WorkerThread(server.accept(), log).start();
            } catch (IOException ignored) {
                System.out.println("crash du serveur");
            }
        }
    }

    private static class WorkerThread extends Thread {
        Socket socket;
        DataInputStream dis;
        DataOutputStream dos;
        boolean log;

        public WorkerThread(Socket s, boolean l) throws IOException {
            socket = s;
            log = l;
            dis = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            dos = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        }

        @Override
        public void run() {
            while (true) {
                Integer size = Network.getSize(dis, dos, log);
                if (size == null) {
                    return;
                }
                String inMessage = Network.getMessage(size, dis, dos, log);
                if (inMessage == null) {
                    continue;
                }
                String[] message = inMessage.split(";\n");
                String outMessage = null;
                switch (message[0]) {
                    case "post":
                        //TODO
                        System.out.println("post");
                        break;
                    case "get":
                        //TODO
                        System.out.println("get");
                        break;
                    case "close":
                        //TODO
                        return;
                    default:
                        System.out.println("Unknown command : " + message[0]);
                        continue;
                }
                if (outMessage != null) {
                    if (outMessage.startsWith("ERROR : ")) {
                        Network.notifyError(outMessage, dos, log);
                    } else {
                        Network.send(outMessage, dos, log);
                    }
                }
            }
        }
    }
}