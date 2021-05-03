import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;

public class Common {
    public static class Constants {
        static String accountCreated = "Account created";
        static String accountDeleted = "Account deleted";
        static String badMessage = "Message format is incorrect";
        static String cantFollow = "Failure : failed to follow ";
        static String cantUnfollow = "Failure : failed to unfollow ";
        static String cantGetMessages = "Failure : failed to get new messages";
        static String cantPostMessage = "Failure : failed to post this message";
        static String couldNotCreateAccount = "Failure : account creation failed";
        static String couldNotDeleteAccount = "Failure : account deletion failed";
        static String couldNotReadSize = "Failure : could not read message size";
        static String couldNotSendMessage = "Failure : could not send message";
        static String posted = "Message successfully posted";
        static String followed = "Now following ";
        static String unfollowed = "Unfollowed ";
        static String messageWrongSize = "Failure : could not read the specified number of bytes";
        static String separator = ";\n";
    }

    // Récupération des paramètres du module manager
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
        String[] split = splitOnSeparator(line, " ");
        String moduleManagerHost = (split.length == 3 ? split[1] : server.getInetAddress().getHostAddress());
        Integer moduleManagerPort = Integer.parseInt(split[split.length - 1]);
        return new Pair<>(moduleManagerHost, moduleManagerPort);
    }

    // Récupération du port associé au module demandé
    @SuppressWarnings("deprecation")
    static int getPort(String configFile, String module) throws IOException, MissingConfigException {
        DataInputStream cf = new DataInputStream(new FileInputStream(configFile));
        String line = cf.readLine();
        while (line != null && !line.startsWith(module)) {
            line = cf.readLine();
        }
        cf.close();
        if (line == null) {
            throw new MissingConfigException();
        }
        String[] split = splitOnSeparator(line, " ");
        return Integer.parseInt(split[split.length - 1]);
    }

    // Renvoie les String non vides rendues par le split de base sur sep
    static String[] splitOnSeparator(String base, String sep) {
        return toStringArray(Arrays.stream(base.trim().split(sep)).filter(s -> !s.contentEquals("")).toArray());
    }

    static String[] toStringArray(Object[] o) {
        String[] res = new String[o.length];
        for (int i = 0; i < res.length; i++) {
            res[i] = (String) o[i];
        }
        return res;
    }

    // Ouvre une connexion vers le module manager
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