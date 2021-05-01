import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.LinkedList;

public class MessageManager extends Thread{

    ServerSocket server;
    boolean log = false;
    String moduleManagerHost;
    int moduleManagerPort;
    String configFile = "./modulesConfig.txt";
    final LinkedList<Messages> messages = new LinkedList<Messages>();

    class Messages {

        String pseudo;
        LinkedList<Message> messages;

        Messages(String pseudo) {
            this.pseudo = pseudo;
            messages = new LinkedList<Message>();
        }
    }

    static class Message {
        static double counter = 0;
        String message;
        double id;
        Message(String message) {
            id = counter ++;
            this.message = message;
        }
    }

    public static void main(String[] args) throws IOException, MissingConfigException {
        new MessageManager(args).start();
    }

    public MessageManager(String[] args) throws IOException, MissingConfigException {
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "config":
                    configFile = args[i + 1];
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
        server = new ServerSocket(getPort());
        getMMparams();
    }

    private void getMMparams() throws IOException, MissingConfigException {
        Pair<String, Integer> p = Common.getMMparams(configFile, server);
        moduleManagerHost = p.key;
        moduleManagerPort = p.value;
    }

    private int getPort() throws IOException, MissingConfigException {
        return Common.getPort(configFile, "messageManager");
    }

    @Override
    @SuppressWarnings("InfiniteLoopStatement")
    public void run() {
        while (true) {
            try {
                new MessageManager.WorkerThread(server.accept()).start();
            } catch (IOException | MissingConfigException ignored) {
                System.out.println("crash du serveur");
            }
        }
    }


    private class WorkerThread extends Thread {
        Socket socket;
        DataInputStream dis;
        DataOutputStream dos;
        DataInputStream fromMM;
        DataOutputStream toMM;

        public WorkerThread(Socket s) throws IOException, MissingConfigException {
            socket = s;
            dis = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            dos = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            connectToMM();
        }

        @Override
        @SuppressWarnings("InfiniteLoopStatement")
        public void run() {
            while (true) {
                String message = Network.getMessage(dis, null, log);
                String[] split = (String[]) Arrays.stream(message.split(Common.Constants.separator)).filter(s -> !s.contentEquals("")).toArray();
                if (split.length != 3) {
                    Network.send(Common.Constants.badMessage, dos, log);
                    continue;
                }
                switch (split[0]) {
                    case "post":
                        boolean done = false;
                        synchronized (messages) {
                            for (Messages m : messages
                            ) {
                                if (m.pseudo.equals(split[1])) {
                                    m.messages.add(new Message(split[2]));
                                    Network.send(Common.Constants.posted, dos, log);
                                    done = true;
                                }
                            }
                            if (!done) {
                                Messages mes = new Messages(split[1]);
                                mes.messages.add(new Message(split[2]));
                                messages.add(mes);
                                Network.send(Common.Constants.posted, dos, log);
                            }
                        }
                        break;
                    case "get":
                        boolean get = false;
                        synchronized (messages) {
                            for (Messages m : messages
                            ) {
                                if (m.pseudo.equals(split[1])) {
                                    boolean startGet = false;
                                    String allMessages = "ok ";
                                    for (Message mes : m.messages
                                         ) {
                                        if (mes.id == Integer.parseInt(split[2]) || get) {
                                            allMessages += mes.message;
                                            get = true;
                                        }
                                    }
                                    Network.send("ok " + allMessages, dos, log);
                                }
                            }
                            if (!get) {
                                Network.send(Common.Constants.cantGetMessages, dos, log);
                            }
                        }
                        break;
                    default:
                        //TODO
                        break;
                }
            }
        }

        private void connectToMM() throws IOException, MissingConfigException {
            if (fromMM != null || toMM != null) {
                getMMparams();
            }
            Pair<DataInputStream, DataOutputStream> p = Common.connectToMM(fromMM, toMM, moduleManagerHost, moduleManagerPort);
            fromMM = p.key;
            toMM = p.value;
        }
    }

}
