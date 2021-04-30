import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;

public class Common {
    public static class Constants {
        static String accountCreated = "Account created";
        static String accountDeleted = "Account deleted";
        static String badMessage = "Message format is incorrect";
        static String cantFollow = "Failed to follow ";
        static String cantUnfollow = "Failed to unfollow ";
        static String cantGetMessages = "Failed to get new messages";
        static String cantPostMessage = "Failed to post this message";
        static String couldNotCreateAccount = "Account creation failed";
        static String couldNotDeleteAccount = "Account deletion failed";
        static String couldNotReadSize = "Could not read message size";
        static String couldNotSendMessage = "Could not send message";
        static String posted = "Message successfully posted";
        static String followed = "Now following ";
        static String unfollowed = "Unfollowed ";
        static String messageWrongSize = "Could not read the specified number of bytes";
        static String separator = ";\n";
    }

    @SuppressWarnings("deprecation")
    static Pair<String, Integer> getMMparams(String configFile, ServerSocket server) throws IOException, MissingConfigException {
        DataInputStream cf;
        cf = new DataInputStream(new FileInputStream(configFile));
        String line;
        line = cf.readLine();
        while (line != null && !line.startsWith("moduleManager")) {
            line = cf.readLine();
        }
        cf.close();
        if (line == null) {
            throw new MissingConfigException();
        }
        String[] split;
        split = (String[]) Arrays.stream(line.trim().split(" ")).filter(s -> !s.contentEquals("")).toArray();
        String moduleManagerHost = (split.length == 3 ? split[1] : server.getInetAddress().getHostAddress());
        Integer moduleManagerPort = Integer.parseInt(split[split.length - 1]);
        return new Pair<>(moduleManagerHost, moduleManagerPort);
    }

    @SuppressWarnings("deprecation")
    static int getPort(String configFile, String module) throws IOException, MissingConfigException {
        int port;
        DataInputStream cf = new DataInputStream(new FileInputStream(configFile));
        String line = cf.readLine();
        while (line != null && !line.startsWith(module)) {
            line = cf.readLine();
        }
        cf.close();
        if (line == null) {
            throw new MissingConfigException();
        }
        String[] split = (String[]) Arrays.stream(line.trim().split(" ")).filter(s -> !s.contentEquals("")).toArray();
        port = Integer.parseInt(split[split.length - 1]);
        return port;
    }

    static Pair<DataInputStream, DataOutputStream> connectToMM(DataInputStream fromMM, DataOutputStream toMM, String moduleManagerHost, int moduleManagerPort) throws IOException {
        if (fromMM != null) {
            fromMM.close();
        }
        if (toMM != null) {
            toMM.close();
        }
        Socket moduleManagerSocket = null;
        boolean tem;
        do {
            tem = false;
            try {
                moduleManagerSocket = new Socket(moduleManagerHost, moduleManagerPort);
            } catch (Exception e) {
                System.out.println("Module manager unreachable at " + moduleManagerHost + ":" + moduleManagerPort);
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ignored) {
                }
                tem = true;
            }
        } while (tem);
        DataInputStream fromMM2 = new DataInputStream(new BufferedInputStream(moduleManagerSocket.getInputStream()));
        DataOutputStream toMM2 = new DataOutputStream(new BufferedOutputStream(moduleManagerSocket.getOutputStream()));
        return new Pair<>(fromMM2, toMM2);
    }
}