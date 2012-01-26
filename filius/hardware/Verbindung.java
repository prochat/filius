/*
** This file is part of Filius, a network construction and simulation software.
** 
** Originally created at the University of Siegen, Institute "Didactics of
** Informatics and E-Learning" by a students' project group:
**     members (2006-2007): 
**         André Asschoff, Johannes Bade, Carsten Dittich, Thomas Gerding,
**         Nadja Haßler, Ernst Johannes Klebert, Michell Weyer
**     supervisors:
**         Stefan Freischlad (maintainer until 2009), Peer Stechert
** Project is maintained since 2010 by Christian Eibl <filius@c.fameibl.de>
 **         and Stefan Freischlad
** Filius is free software: you can redistribute it and/or modify
** it under the terms of the GNU General Public License as published by
** the Free Software Foundation, either version 2 of the License, or
** (at your option) version 3.
** 
** Filius is distributed in the hope that it will be useful,
** but WITHOUT ANY WARRANTY; without even the implied
** warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
** PURPOSE. See the GNU General Public License for more details.
** 
** You should have received a copy of the GNU General Public License
** along with Filius.  If not, see <http://www.gnu.org/licenses/>.
*/
package filius.hardware;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.Observable;

import filius.Main;
import filius.software.netzzugangsschicht.EthernetFrame;
import filius.exception.VerbindungsException;
import filius.rahmenprogramm.I18n;
import filius.rahmenprogramm.Information;

/**
 * @author carsten
 *
 */
public abstract class Verbindung extends Hardware implements Serializable, I18n {

	/** Verzoegerung der Uebertragung in Millisekunden, wenn der
	 * Verzoegerungsfaktor 1 ist.
	 */
	private static final int MIN_VERZOEGERUNG = 50;

	/** Faktor der Verzoegerungszeit, der zwischen 1 und 100 */
	private static int verzoegerungsFaktor = 1;

	/** maximale Anzahl von Hops zum Datenaustausch. Diese
	 * Zahl wird verwendet, um eine Round-Trip-Time (RTT) zu
	 * berechnen. Da es auch möglich ist, Datenaustausch mit einem
	 * einem virtuellen Rechnernetz ueber eine 'Modemverbindung'
	 * zu erstellen, wird diese Zahl hoch angesetzt. <br />
	 * Mit dieser Zahl sind die HOPS fuer einen Round-Trip
	 * als fuer die Hin- und Zurueck-Uebertragung beim Datenaustausch
	 * mit einem anderen Knoten gemeint.
	 */
	private static final int MAX_HOPS = 50;
	
	// extend RTT in case of slow machines by this factor; 1: no change
	private static int extendRTTfactor = 1;


	private Port[] anschluesse = null;

	private SimplexVerbindung simplexEins, simplexZwei;


	public void setAnschluesse(Port[] anschluesse) {
		Main.debug.println("INVOKED ("+this.hashCode()+") "+getClass()+" (Verbindung), setAnschluesse("+anschluesse+")");
		this.anschluesse = anschluesse;

		try {
			verbinde();
		} catch (VerbindungsException e) {
			// TODO Auto-generated catch block
			e.printStackTrace(Main.debug);
		}
	}

	private void verbinde() throws VerbindungsException {
		Main.debug.println("INVOKED ("+this.hashCode()+") "+getClass()+" (Verbindung), verbinde()"+
						   "\t"+anschluesse[0].hashCode()+" <-> "+anschluesse[1].hashCode());
		try {
			simplexEins = new SimplexVerbindung(anschluesse[0], anschluesse[1], this);
			simplexZwei = new SimplexVerbindung(anschluesse[1], anschluesse[0], this);

			anschluesse[0].setVerbindung(this);
			anschluesse[1].setVerbindung(this);

			Thread t1 = new Thread(simplexEins);
			Thread t2 = new Thread(simplexZwei);

			t1.start();
			t2.start();

		} catch (NullPointerException e) {
			simplexEins = null;
			simplexZwei = null;
			anschluesse[0].setVerbindung(null);
			anschluesse[1].setVerbindung(null);
			throw new VerbindungsException("EXCEPTION: "+messages.getString("verbindung_msg1"));
		}
	}

	public Port[] getAnschluesse() {
		return anschluesse;
	}

	public void anschluesseTrennen () throws VerbindungsException {
		Main.debug.println("INVOKED ("+this.hashCode()+") "+getClass()+" (Verbindung), anschluesseTrennen()");
		simplexEins.anschluesseTrennen();
		simplexZwei.anschluesseTrennen();
	}

	public static int holeVerzoegerungsFaktor() {
		return verzoegerungsFaktor;
	}

	/** Zum setzen den Verzoegerungsfaktors. Das Produkt daraus und der
	 * minimalen Verzoegerung ergibt die tatsaechliche Verzoegerung
	 * bei der Uebertragung zwischen zwei Knoten im Rechnernetz. Der
	 * Wert des Faktors muss zwischen 1 und 100 liegen. Wenn der
	 * uebergebene Parameter ausserhalb dieses Bereichs liegt, wird
	 * er auf den Minimal- bzw. Maximalwert gesetzt.
	 *
	 * @param verzoegerungsFaktor
	 */
	public static void setzeVerzoegerungsFaktor(int verzoegerungsFaktor) {
		Main.debug.println("INVOKED (static) filius.hardware.Verbindung, setzeVerzoegerungsFaktor("+verzoegerungsFaktor+")");
		if (verzoegerungsFaktor < 1) {
			Verbindung.verzoegerungsFaktor = 1;
		}
		else if (verzoegerungsFaktor > 100) {
			Verbindung.verzoegerungsFaktor = 100;
		}
		else {
		Verbindung.verzoegerungsFaktor = verzoegerungsFaktor;
		}
	}


	/** Gibt die Verzoegerung einer Verbindung zwischen zwei Knoten im
	 * Rechnernetz in Millisekunden zurueck. Dazu wird die minimale
	 * Verzoegerungszeit von 50 Millisekunden mit dem Verzoegerungsfaktor
	 * multipliziert.
	 *
	 * @return Verzoegerung der Uebertragung zwischen zwei Knoten im
	 *   Rechnernetz in Millisekunden
	 */
	public static int holeVerzoegerung() {
		return verzoegerungsFaktor * MIN_VERZOEGERUNG;
	}

	public static void setRTTfactor(int factor) {
		extendRTTfactor = factor;
	}
	public static int getRTTfactor() {
		return extendRTTfactor;
	}
	
	/** maximale Round-Trip-Time (RTT) in Millisekunden <br />
	 * solange wird auf eine Antwort auf ein Segment gewartet */
	public static int holeRTT() {
		//new Exception().printStackTrace(Main.debug);  // DEBUG to disclose calling functions
		return MAX_HOPS * holeVerzoegerung() * extendRTTfactor;
	}
}
