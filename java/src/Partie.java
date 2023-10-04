import java.io.IOException;
import java.util.ArrayList;
import java.net.*;

public class Partie implements Runnable {
    String adresse;
    int port;
    Labyrinthe labyrinthe;
    int longueur, largeur;
    ArrayList<Joueur> participant;
    ArrayList<Fantome> list_fantome;
    int fantome;
    final Fantome[][] labyfantome;
    Etat etat;

    static int numero = 5000;

    public enum Etat {DEBUT, FIN, ENCOURS}

    public Partie(int fantome, int longueur, int largeur) {
        this.adresse = "224.0.0.255";
        numero++;
        this.port = numero;
        this.fantome = fantome;
        this.longueur = longueur;
        this.largeur = largeur;
        this.labyrinthe = new Labyrinthe(longueur,largeur);
        this.participant = new ArrayList<>();
        this.list_fantome = new ArrayList<>();
        this.labyfantome = new Fantome[longueur][largeur];
        this.etat = Etat.DEBUT;
    }

    @Override
    public void run() {
        init();
        try {
            while (list_fantome.size() != 0 && participant.size() !=0) {
                Thread.sleep(3000);
            }
            if(participant.size()>0) {
                DatagramSocket dso = new DatagramSocket();
                InetSocketAddress ia = new InetSocketAddress(adresse, port);
                byte[] data;
                Joueur vainqueur = participant.get(0);
                for (Joueur j : participant) {
                    if (vainqueur.score < j.score) {
                        vainqueur = j;
                    }
                }
                String point = vainqueur.score < 10 ? "000" : (vainqueur.score < 100 ? "O0" : (vainqueur.score < 1000 ? "0" : ""));
                String s = "ENDGA " + vainqueur.identifiant + " " + point + vainqueur.score + "+++";
                data = s.getBytes();
                DatagramPacket paquet = new DatagramPacket(data, data.length, ia);
                dso.send(paquet);
            }
            this.etat = Etat.FIN;
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void init() {
        int nb1;
        int nb2;
        for (Joueur j : participant) {
            nb1 = (int) (Math.random() * longueur);
            nb2 = (int) (Math.random() * largeur);
            while (!labyrinthe.tableau[nb1][nb2] || labyfantome[nb1][nb2] != null) {
                nb1 = (int) (Math.random() * longueur);
                nb2 = (int) (Math.random() * largeur);
            }
            j.x = nb1;
            j.y = nb2;
        }
        for (int i = 0; i < fantome; i++) {
            nb1 = (int) (Math.random() * longueur);
            nb2 = (int) (Math.random() * largeur);
            while (!labyrinthe.tableau[nb1][nb2] || labyfantome[nb1][nb2] != null) {
                nb1 = (int) (Math.random() * longueur);
                nb2 = (int) (Math.random() * largeur);
            }
            labyfantome[nb1][nb2] = new Fantome(this, nb1, nb2);
            list_fantome.add(labyfantome[nb1][nb2]);
            Thread t = new Thread(labyfantome[nb1][nb2]);
            t.start();
        }
    }

    public void mange(Joueur j,Fantome f) {
        try {
            f.manger = true;
            f.joueur = j;
            this.labyfantome[j.x][j.y] = null;
            j.score += 10;
            DatagramSocket ds = new DatagramSocket();
            InetSocketAddress ia = new InetSocketAddress(this.adresse, this.port);
            byte[] data;
            String posx = j.x < 10 ? "00" : (j.x < 100 ? "0" : "");
            String posy = j.y < 10 ? "00" : (j.y < 100 ? "0" : "");
            String point = j.score < 10 ? "000" : (j.score < 100 ? "00" : (j.score < 1000 ? "0" : ""));
            String s = "SCORE " + j.identifiant + " " + point+j.score+ " " + posx + j.x + " " + posy + j.y + "+++";
            data = s.getBytes();
            DatagramPacket paquet = new DatagramPacket(data, data.length, ia);
            ds.send(paquet);
            this.list_fantome.remove(f);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}