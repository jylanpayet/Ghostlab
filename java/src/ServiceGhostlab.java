import java.net.*;
import java.io.*;
import java.lang.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;

public class ServiceGhostlab implements Runnable {
    public Socket socket;
    public final ArrayList<Partie> liste_partie;
    public final ArrayList<Joueur> liste_joueur;
    public Joueur courant = null;
    public Partie selection = null;


    public ServiceGhostlab(Socket s, ArrayList<Partie> liste_partie, ArrayList<Joueur> liste_joueur) {
        this.socket = s;
        this.liste_partie = liste_partie;
        this.liste_joueur = liste_joueur;
    }

    @Override
    public void run() {
        try {
            boolean lecture = true;
            InputStream is = socket.getInputStream();
            OutputStream os = socket.getOutputStream();

            //Message de début (Il faudra possiblement passer le byte en unsigned lors de la reception)
            messageGAMES(os);
            messageOGAME(os);

            // On lit en continue tant que rien ne bloque l'utilisateur.
            while (lecture) {
                String[] message = readMessage(is);
                System.out.println(Arrays.toString(message));
                if (message.length > 0) {
                    if (selection != null && selection.etat == Partie.Etat.FIN)
                        message[0] = "IQUIT";
                    switch (message[0]) {
                        case "NEWPL":
                            messageNEWPL(message, os);
                            break;
                        case "REGIS":
                            messageREGIS(message, os);
                            break;
                        case "START":
                            messageSTART(message, os);
                            break;
                        case "UNREG":
                            messageUNREG(message, os);
                            break;
                        case "SIZE?":
                            messageSIZE(message, os);
                            break;
                        case "LIST?":
                            messageLIST(message, os);
                            break;
                        case "GAME?":
                            if (message.length == 1 && selection == null) {
                                messageGAMES(os);
                                messageOGAME(os);
                            } else {
                                os.write("DUNNO***".getBytes());
                                os.flush();
                            }
                            break;
                        case "UPMOV", "DOMOV", "LEMOV", "RIMOV":
                            deplacement(message, os);
                            break;
                        case "GLIS?":
                            messageGLIS(message, os);
                            break;
                        case "MALL?":
                            messageMALL(message, os);
                            break;
                        case "SEND?":
                            messageUDP(message, os);
                            break;
                        case "IQUIT":
                            lecture=false;
                            messageIQUIT(socket,is,os);
                            break;
                        default:
                            os.write("DUNNO***".getBytes());
                            os.flush();
                            break;
                    }
                }
            }
        } catch (Exception e) {
            System.out.println(e);
            e.printStackTrace();
        }
    }

    // Lecture de message :
    public String[] readMessage(InputStream is) throws IOException {
        StringBuilder message = new StringBuilder();
        int p;
        while (true) {
            p = is.read();
            if (p == -1) {
                return ("IQUIT").split(" ");
            } else if (p == 42) {
                p = is.read();
                if (p == 42) {
                    p = is.read();
                    if (p == 42) {
                        break;
                    } else {
                        message.append((char) p);
                    }
                } else {
                    message.append((char) p);
                }
            } else {
                message.append((char) p);
            }
        }
        return message.toString().split(" ");
    }

    // Envoie du message [GAMES␣n***] au joueur
    public void messageGAMES(OutputStream os) throws IOException {
        // nombre de partie non commencée :
        int partie = 0;
        //On récupere toutes les parties non commencée :
        synchronized (liste_partie) {
            for (Partie value : liste_partie) {
                if (value.etat == Partie.Etat.DEBUT)
                    partie++;
            }
        }
        os.write("GAMES ".getBytes());
        os.write((byte) partie);
        os.write("***".getBytes());
        os.flush();
    }

    //Envoie des messages [OGAME␣m␣s***] au joueur
    public void messageOGAME(OutputStream os) throws IOException {
        //On récupere toutes les parties non commencée :
        Partie p;
        synchronized (liste_partie) {
            for (int i = 0; i < liste_partie.size(); i++) {
                p = liste_partie.get(i);
                if (p.etat == Partie.Etat.DEBUT) {
                    os.write("OGAMES ".getBytes());
                    os.write((byte) i);
                    os.write(" ".getBytes());
                    os.write((byte) p.participant.size());
                    os.write("***".getBytes());
                    os.flush();
                }
            }
        }
    }

    //Creation d'un nouveau Joueur,
    //la creation ne se fait que quand le joueur demande à créer ou rejoindre une partie
    public boolean nouveauJoueur(String id, String port) {
        int port_parse;
        if (id.length() == 8 && id.matches("\\w.*") && port.length() == 4 && port.matches("\\d*(\\.\\d+)?")) {
            port_parse = Integer.parseInt(port);
            if (port_parse < 1024)
                return false;
            synchronized (liste_joueur) {
                if (courant == null) {
                    for (Joueur j : liste_joueur) {
                        if (id.equals(j.identifiant) || port_parse == j.port_udp) {
                            return false;
                        }
                    }
                    courant = new Joueur(id, port_parse);
                    liste_joueur.add(courant);
                    return true;
                }
            }
        }
        return false;
    }

    //Traitement de [START***]
    public void messageSTART(String[] message, OutputStream os) throws IOException, InterruptedException {
        if (message.length == 1 && this.courant != null && this.selection != null) {
            this.courant.pret = true;
            boolean partie = true;
            synchronized (selection) {
                for (Joueur j : selection.participant) {
                    if (!j.pret) {
                        partie = false;
                        break;
                    }
                }
                if (partie) {
                    selection.etat = Partie.Etat.ENCOURS;
                    selection.notifyAll();
                    Thread t = new Thread(selection);
                    t.start();
                }
                while (selection.etat == Partie.Etat.DEBUT) {
                    selection.wait();
                }
            }

            //Partie est lancée
            os.write("WELCO ".getBytes());
            os.write((byte) liste_partie.indexOf(selection));
            os.write((byte) ' ');
            os.write(ByteBuffer.allocate(2).putShort((short) selection.longueur).order(ByteOrder.LITTLE_ENDIAN).array());
            os.write((byte) ' ');
            os.write(ByteBuffer.allocate(2).putShort((short) selection.largeur).order(ByteOrder.LITTLE_ENDIAN).array());
            os.write((byte) ' ');
            os.write((byte) selection.fantome);
            os.write((" " + selection.adresse + "#### " + selection.port + "***").getBytes());
            os.flush();

            // Envoie de la position du joueur
            String posx = courant.x < 10 ? "00" : (courant.x < 100 ? "0" : "");
            String posy = courant.y < 10 ? "00" : (courant.y < 100 ? "0" : "");
            os.write(("POSIT " + courant.identifiant + " " + posx + courant.x + " " + posy + courant.y + "***").getBytes());
            os.flush();
        } else {
            os.write("DUNNO***".getBytes());
            os.flush();
        }
    }

    //Traitement de [NEWPL␣id␣port***] -> [REGOK␣m***] ou [REGNO***]
    public void messageNEWPL(String[] message, OutputStream os) throws IOException {
        if (this.courant == null && message.length == 3 && nouveauJoueur(message[1], message[2])) {
            synchronized (liste_partie) {
                Partie nouvelle = new Partie(6, 12, 12);
                liste_partie.add(nouvelle);
                selection = nouvelle;
                selection.participant.add(courant);
                os.write("REGOK ".getBytes());
                os.write((byte) liste_partie.indexOf(selection));
                os.write("***".getBytes());
            }
        } else {
            os.write("REGNO***".getBytes());
        }
        os.flush();
    }

    //Traitement de [REGIS␣id␣port␣m***] -> [REGOK␣m***] ou [REGNO***]
    public void messageREGIS(String[] message, OutputStream os) throws IOException {
        if (this.courant == null && message.length == 4 && nouveauJoueur(message[1], message[2])) {
            synchronized (liste_partie) {
                int number = (byte) message[3].charAt(0) & 0xff;
                if (number < liste_partie.size()) {
                    Partie p = liste_partie.get(number);
                    if (p != null && p.etat.equals(Partie.Etat.DEBUT)) {
                        selection = p;
                        selection.participant.add(courant);
                        os.write("REGOK ".getBytes());
                        os.write((byte) liste_partie.indexOf(selection));
                        os.write("***".getBytes());
                    } else {
                        os.write("REGNO***".getBytes());
                    }
                } else {
                    os.write("REGNO***".getBytes());
                }
            }
        } else {
            os.write("REGNO***".getBytes());
        }
        os.flush();
    }

    //Traitement [UNREG***] -> [UNROK␣m***] ou [DUNNO***]
    public void messageUNREG(String[] message, OutputStream os) throws IOException {
        if (message.length == 1 && courant != null && selection != null && !this.courant.pret) {
            synchronized (liste_partie) {
                selection.participant.remove(courant);
                byte m = (byte) liste_partie.indexOf(selection);
                selection = null;
                os.write("UNROK ".getBytes());
                os.write(m);
                os.write("***".getBytes());
                os.flush();
            }
            synchronized (liste_joueur) {
                liste_joueur.remove(courant);
                courant = null;
            }
        } else {
            os.write("DUNNO***".getBytes());
            os.flush();
        }

    }

    //Traitement [SIZE?␣m***] -> [SIZE!␣m␣h␣w***] ou [DUNNO***]
    public void messageSIZE(String[] message, OutputStream os) throws IOException {
        if (this.selection == null && message.length == 2 && message[1].length() == 1) {
            int m = (byte) message[1].charAt(0) & 0xff;
            synchronized (liste_partie) {
                if (m < liste_partie.size()) {
                    Partie p = liste_partie.get(m);
                    os.write("SIZE! ".getBytes());
                    os.write((byte) m);
                    os.write((byte) ' ');
                    os.write(ByteBuffer.allocate(2).putShort((short) p.longueur).order(ByteOrder.LITTLE_ENDIAN).array());
                    os.write((byte) ' ');
                    os.write(ByteBuffer.allocate(2).putShort((short) p.largeur).order(ByteOrder.LITTLE_ENDIAN).array());
                    os.write("***".getBytes());
                } else {
                    os.write("DUNNO***".getBytes());
                }
            }
        } else {
            os.write("DUNNO***".getBytes());
        }
        os.flush();
    }

    //Traitement [LIST?␣m***] -> [LIST!␣m␣s***] -> s * [PLAYR␣id***] ou [DUNNO***]
    public void messageLIST(String[] message, OutputStream os) throws IOException {
        if (this.selection == null && message.length == 2 && message[1].length() == 1) {
            int m = (byte) message[1].charAt(0) & 0xff;
            synchronized (liste_partie) {
                if (m < liste_partie.size()) {
                    Partie p = liste_partie.get(m);
                    os.write("LIST! ".getBytes());
                    os.write((byte) m);
                    os.write((byte) ' ');
                    os.write((byte) p.participant.size());
                    os.write("***".getBytes());
                    for (int i = 0; i < p.participant.size(); i++) {
                        os.write(("PLAYR " + p.participant.get(i).identifiant + "***").getBytes());
                    }
                } else {
                    os.write("DUNNO***".getBytes());
                }
            }
        } else {
            os.write("DUNNO***".getBytes());
        }
        os.flush();
    }


    public void deplacement(String[] message, OutputStream os) throws IOException {
        if (this.courant != null && this.selection != null && this.selection.etat == Partie.Etat.ENCOURS && message.length == 2 && message[1].length() == 3
                && message[1].matches("\\d*(\\.\\d+)?")) {
            int d = Integer.parseInt(message[1]);
            boolean ft = false;
            for (int i = 1; i < d + 1; i++) {
                if (message[0].equals("DOMOV") && courant.x - 1 > 0 && selection.labyrinthe.tableau[courant.x - 1][courant.y]) {
                    courant.x--;
                    if (selection.labyfantome[courant.x][courant.y] != null) {
                        selection.mange(courant, selection.labyfantome[courant.x][courant.y]);
                        ft = true;
                    }

                } else if (message[0].equals("UPMOV") && courant.x + 1 < selection.longueur - 1 && selection.labyrinthe.tableau[courant.x + 1][courant.y]) {
                    courant.x++;
                    if (selection.labyfantome[courant.x][courant.y] != null) {
                        selection.mange(courant, selection.labyfantome[courant.x][courant.y]);
                        ft = true;
                    }

                } else if (message[0].equals("LEMOV") && courant.y - 1 > 0 && selection.labyrinthe.tableau[courant.x][courant.y - 1]) {
                    courant.y--;
                    if (selection.labyfantome[courant.x][courant.y] != null) {
                        selection.mange(courant, selection.labyfantome[courant.x][courant.y]);
                        ft = true;
                    }

                } else if (message[0].equals("RIMOV") && courant.y + 1 < selection.largeur - 1 && selection.labyrinthe.tableau[courant.x][courant.y + 1]) {
                    courant.y++;
                    if (selection.labyfantome[courant.x][courant.y] != null) {
                        selection.mange(courant, selection.labyfantome[courant.x][courant.y]);
                        ft = true;
                    }

                } else {
                    break;
                }
            }
            String posx = courant.x < 10 ? "00" : (courant.x < 100 ? "0" : "");
            String posy = courant.y < 10 ? "00" : (courant.y < 100 ? "0" : "");
            String point = courant.score < 10 ? "000" : (courant.score < 100 ? "00" : (courant.score < 1000 ? "0" : ""));
            if (ft) {
                os.write(("MOVEF " + posx + courant.x + " " + posy + courant.y + " " + point + courant.score + "***").getBytes());
            } else {
                os.write(("MOVE! " + posx + courant.x + " " + posy + courant.y + "***").getBytes());
            }
            os.flush();
        } else {
            os.write("DUNNO***".getBytes());
            os.flush();
        }
    }

    public void messageGLIS(String[] message, OutputStream os) throws IOException {
        if (courant != null && selection != null && this.selection.etat == Partie.Etat.ENCOURS && message.length == 1) {
            os.write("GLIS! ".getBytes());
            os.write((byte) selection.participant.size());
            os.write("***".getBytes());
            os.flush();

            String posx;
            String posy;
            String point;

            for (Joueur j : selection.participant) {
                posx = j.x < 10 ? "00" : (j.x < 100 ? "O" : "");
                posy = j.y < 10 ? "00" : (j.y < 100 ? "O" : "");
                point = j.score < 10 ? "000" : (j.score < 100 ? "O0" : (j.score < 1000 ? "0" : ""));
                os.write(("GPLYR " + j.identifiant + " " + posx + j.x + " " + posy + j.y + " " + point + j.score + "***").getBytes());
                os.flush();
            }
        } else {
            os.write("DUNNO***".getBytes());
            os.flush();
        }
    }

    public void messageMALL(String[] message, OutputStream os) throws IOException {
        String mess = "";
        for (int i = 1; i < message.length; i++) {
            mess += " " + message[i];
        }
        if (courant != null && selection != null && this.selection.etat == Partie.Etat.ENCOURS && mess.length() < 201) {
            DatagramSocket dso = new DatagramSocket();
            InetSocketAddress ia = new InetSocketAddress(selection.adresse, selection.port);
            byte[] data;
            String s = "MESSA " + courant.identifiant + mess + "+++";
            data = s.getBytes();
            DatagramPacket paquet = new DatagramPacket(data, data.length, ia);
            dso.send(paquet);
            os.write("MALL!***".getBytes());
            os.flush();
        } else {
            os.write("DUNNO***".getBytes());
            os.flush();
        }
    }

    public void messageUDP(String[] message, OutputStream os) throws IOException {
        boolean envoie = false;
        String mess = "";
        for (int i = 2; i < message.length; i++) {
            mess += " " + message[i];
        }
        if (courant != null && selection != null && this.selection.etat == Partie.Etat.ENCOURS && mess.length() < 201) {
            for (Joueur j : selection.participant) {
                if (message[1].equals(j.identifiant)) {
                    try {
                        DatagramSocket dso = new DatagramSocket();
                        byte[] data;
                        String s = "MESSP " + j.identifiant + mess + "+++";
                        data = s.getBytes();
                        DatagramPacket paquet = new
                                DatagramPacket(data, data.length,
                                InetAddress.getByName("localhost"), j.port_udp);
                        dso.send(paquet);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    envoie = true;
                    break;
                }
            }
        }
        if (envoie) {
            os.write("SEND!***".getBytes());
        } else {
            os.write("NSEND***".getBytes());
        }
        os.flush();
    }

    public void messageIQUIT(Socket socket, InputStream is, OutputStream os) throws IOException {
        if (courant != null && selection != null) {
            boolean b=false;
            synchronized (selection) {
                selection.participant.remove(courant);
                for (Joueur j : selection.participant) {
                    if (!j.pret) {
                        b = true;
                        break;
                    }
                }
                if (!b && selection.etat== Partie.Etat.DEBUT) {
                    selection.etat = Partie.Etat.ENCOURS;
                    selection.notifyAll();
                    Thread t = new Thread(selection);
                    t.start();
                }
            }
        }
        if(courant != null)
            liste_joueur.remove(courant);
        os.write("GOBYE***".getBytes());
        os.flush();
        is.close();
        os.close();
        socket.close();
    }
}