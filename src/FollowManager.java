import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.LinkedList;

public class FollowManager extends Thread{

    ServerSocket server;
    boolean log = false;
    String moduleManagerHost;
    int moduleManagerPort;
    String configFile = "./modulesConfig.txt";
    final LinkedList<Follow> follows = new LinkedList<>();

    class Follow {
        String pseudo;
        LinkedList<String> followList;

        Follow(String pseudo) {
            this.pseudo = pseudo;
            followList = new LinkedList<String>();
        }
    }

    public static void main(String[] args) throws IOException, MissingConfigException {
        new FollowManager(args).start();
    }

    public FollowManager(String[] args) throws IOException, MissingConfigException {
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
        return Common.getPort(configFile, "followManager");
    }

    @Override
    @SuppressWarnings("InfiniteLoopStatement")
    public void run() {
        while (true) {
            try {
                new FollowManager.WorkerThread(server.accept()).start();
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
                if (split.length < 2) {
                    Network.send(Common.Constants.badMessage, dos, log);
                    continue;
                }
                switch (split[0]) {
                    case "add":
                        boolean done = false;
                        synchronized (follows) {
                            for (Follow f: follows
                            ) {
                                if (f.pseudo.equals(split[1])) {
                                    if (f.followList.contains(split[2])) {
                                        f.followList.add(split[2]);
                                        Network.send(Common.Constants.followed, dos, log);
                                        done = true;
                                        break;
                                    }
                                    else {
                                        Network.send(Common.Constants.cantFollow, dos, log);
                                        done = true;
                                    }
                                }
                            }
                            if (!done) {
                                Follow newFollow = new Follow(split[1]);
                                newFollow.followList.add(split[2]);
                                follows.add(newFollow);
                                Network.send(Common.Constants.followed, dos, log);
                            }
                        }
                        break;
                    case "remove":
                        synchronized (follows) {
                            for (Follow f: follows
                            ) {
                                if (f.pseudo.equals(split[1])) {
                                    if (!f.followList.remove(split[2])) {
                                        Network.send(Common.Constants.cantUnfollow, dos, log);
                                    } else {
                                        Network.send(Common.Constants.unfollowed, dos, log);
                                    }
                                }
                            }
                        }
                        break;
                    case "get":
                        boolean get = false;
                        synchronized (follows) {
                            for (Follow f: follows
                            ) {
                                if (f.pseudo.equals(split[1])) {
                                    String followslist = "";
                                    for (String follow: f.followList
                                         ) {
                                        followslist += follow + " ";
                                    }
                                    Network.send("ok " + followslist, dos, log);
                                    get = true;
                                    break;
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
