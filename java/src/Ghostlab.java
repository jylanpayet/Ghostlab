import java.net.*;
import java.io.*;
import java.util.ArrayList;

public class Ghostlab {

    public static void main(String[] args) {
        try {
            int port;
            if (args.length == 1) {
                port = Integer.parseInt(args[0]);
            } else {
                port = 4455;
            }
            ServerSocket server = new ServerSocket(port);
            ArrayList<Partie> liste_partie = new ArrayList<>();
            ArrayList<Joueur> liste_joueur = new ArrayList<>();
            while (true) {
                Socket socket = server.accept();
                ServiceGhostlab serv = new ServiceGhostlab(socket, liste_partie, liste_joueur);
                Thread t = new Thread(serv);
                t.start();
            }
        } catch (Exception e) {
            System.out.println(e);
            e.printStackTrace();
        }
    }
}
