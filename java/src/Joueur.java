public class Joueur {
    String identifiant;
    int port_udp;
    boolean pret;
    int x, y = 0;
    int score;

    public Joueur(String identifiant, int port_udp) {
        this.identifiant = identifiant;
        this.port_udp = port_udp;
        this.pret = false;
        this.score = 0;
    }
}
