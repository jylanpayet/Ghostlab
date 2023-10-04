import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.Random;

public class Fantome implements Runnable {
    Partie partie;
    int x, y;
    boolean manger;
    Joueur joueur;

    public Fantome(Partie partie, int x, int y) {
        this.partie = partie;
        this.x = x;
        this.y = y;
        this.manger = false;
        this.joueur = null;
    }

    @Override
    public void run() {
        //Quand le fantome bouge, on envoie un message multicast
        try {
            while (!manger) {
                Thread.sleep(25000);
                if (manger)
                    break;
                //indique ou il se situe puis change de place
                DatagramSocket dso = new DatagramSocket();
                InetSocketAddress ia = new InetSocketAddress(partie.adresse, partie.port);
                byte[] data;
                String posx = x < 10 ? "00" : (x < 100 ? "O" : "");
                String posy = y < 10 ? "00" : (y < 100 ? "O" : "");
                String s = "GHOST " + posx + x + " " + posy + y + "+++";
                data = s.getBytes();
                DatagramPacket paquet = new DatagramPacket(data, data.length, ia);
                dso.send(paquet);
                Random rand = new Random();
                int nb1 = rand.nextInt(4);
                synchronized (partie.labyfantome) {
                    if (nb1 == 0 && x < partie.longueur - 2 && partie.labyrinthe.tableau[x + 1][y] && partie.labyfantome[x + 1][y] == null) {
                        x++;
                        partie.labyfantome[x][y] = null;
                        partie.labyfantome[x + 1][y] = this;
                    } else if (nb1 == 1 && y < partie.largeur - 2 && partie.labyrinthe.tableau[x][y + 1] && partie.labyfantome[x][y + 1] == null) {
                        y++;
                        partie.labyfantome[x][y] = null;
                        partie.labyfantome[x][y + 1] = this;
                    } else if (nb1 == 2 && x > 0 && partie.labyrinthe.tableau[x - 1][y] && partie.labyfantome[x - 1][y] == null) {
                        x--;
                        partie.labyfantome[x][y] = null;
                        partie.labyfantome[x - 1][y] = this;
                    } else if (nb1 == 3 && y > 0 && partie.labyrinthe.tableau[x][y - 1] && partie.labyfantome[x][y - 1] == null) {
                        y--;
                        partie.labyfantome[x][y] = null;
                        partie.labyfantome[x - 1][y] = this;
                    }
                }
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}
