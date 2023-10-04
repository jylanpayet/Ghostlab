import java.util.Random;

public class Labyrinthe {
    public boolean[][] tableau;
    // Initialise un objet labyrinthe aléatoirement :

    public Labyrinthe(int longueur, int largeur) {
        this.tableau = new boolean[longueur][largeur];
        Random rand = new Random();
        int nb1 = rand.nextInt(longueur);
        int nb2 = rand.nextInt(largeur);
        this.tableau[nb1][nb2] = true;
        int i = 0;
        int j = 0;
        int nb3 = 0;

        while (i < ((longueur * largeur) - (0.6 * (longueur * largeur)))) {
            if (j != 4) {
                nb3 = rand.nextInt(4);
                j++;
            } else {
                j = 0;
            }
            if (nb3 == 0 && nb1 > 1) {
                nb1--;
            } else if (nb3 == 1 && nb1 < longueur - 1) {
                nb1++;
            } else if (nb3 == 2 && nb2 > 1) {
                nb2--;
            } else if (nb3 == 3 && nb2 < largeur - 1) {
                nb2++;
            }
            if (!this.tableau[nb1][nb2]) {
                this.tableau[nb1][nb2] = true;
                i++;
            }
        }
        this.affiche();
    }

    // Affiche le labyrinthe :
    public void affiche() {
        for (int i = 0; i < this.tableau.length; i++) {
            for (int j = 0; j < this.tableau[i].length; j++) {
                if (this.tableau[i][j]) {
                    System.out.print(".  ");
                } else {
                    System.out.print("0  ");
                }
            }
            System.out.println();
        }
    }
}
