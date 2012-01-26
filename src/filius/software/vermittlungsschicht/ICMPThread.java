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
package filius.software.vermittlungsschicht;

import java.util.LinkedList;
import java.util.ListIterator;

import filius.Main;
import filius.exception.VerbindungsException;
import filius.hardware.NetzwerkInterface;
import filius.hardware.knoten.InternetKnoten;
import filius.software.ProtokollThread;
import filius.software.netzzugangsschicht.EthernetFrame;
import filius.software.system.InternetKnotenBetriebssystem;
import filius.hardware.Verbindung;

/**
 * Klasse zur Ueberwachung des Puffers fuer eingehende ARP-Pakete
 *
 */
public class ICMPThread extends ProtokollThread {

	private ICMP vermittlung;

	/** der von dem Thread zu ueberwachende Puffer */
	private LinkedList<IcmpPaket> rcvdPackets;

	public ICMPThread(ICMP vermittlung) {
		super(((InternetKnotenBetriebssystem) vermittlung.holeSystemSoftware())
				.holeEthernet().holeICMPPuffer());  // zu überwachender Puffer als Parameter nötig für Thread-Steuerung
		Main.debug.println("INVOKED-2 ("+this.hashCode()+", T"+this.getId()+") "+getClass()+" (ICMPThread), constr: ICMPThread("+vermittlung+")");
		this.rcvdPackets = new LinkedList<IcmpPaket>();
		this.vermittlung = vermittlung;
	}

	/**
	 * Hilfsmethode zur Bestimmung der Netzwerkkarte, die im Rechnernetz ist,
	 * aus der das ICMP-Paket kam.
	 *
	 * @param paket
	 *            ein eingegangenes ICMP-Paket
	 * @return die Netzwerkkarte aus dem Rechnernetz, aus dem das ICMP-Paket
	 *         verschickt wurde
	 */
	private NetzwerkInterface bestimmeNetzwerkInterface(IcmpPaket paket) {
		Main.debug.println("INVOKED ("+this.hashCode()+", T"+this.getId()+") "+getClass()+" (ICMPThread), bestimmeNetzwerkInterface("+paket.toString()+")");
		NetzwerkInterface nic = null;
		ListIterator it;

		it = ((InternetKnoten) vermittlung.holeSystemSoftware().getKnoten())
				.getNetzwerkInterfaces().listIterator();
		while (it.hasNext()) {
			nic = (NetzwerkInterface) it.next();
			if (VermittlungsProtokoll.gleichesRechnernetz(paket.getQuellIp(),
					nic.getIp(), nic.getSubnetzMaske())) {
				return nic;
			}
		}

		return nic;
	}

	/**
	 * Methode zur Verarbeitung eingehender ICMP-Pakete <br />
	 */
	protected void verarbeiteDatenEinheit(Object datenEinheit) {
		Main.debug.println("INVOKED ("+this.hashCode()+", T"+this.getId()+") "+getClass()+" (ICMPThread), verarbeiteDateneinheit("+datenEinheit.toString()+")");
		// String zielIPAdresse;
		IcmpPaket icmpPaket = (IcmpPaket) datenEinheit;

		icmpPaket.setTtl(icmpPaket.getTtl()-1);
		if(icmpPaket.getIcmpType()==0 &&    // it's an ICMP echo reply packet
			(icmpPaket.getZielIp().equals(IP.LOCALHOST) || 
			 icmpPaket.getZielIp().equals(((InternetKnotenBetriebssystem) vermittlung.holeSystemSoftware()).holeIPAdresse()))) {
			synchronized(rcvdPackets) {
				rcvdPackets.add(icmpPaket);
				rcvdPackets.notify();
			}
			return;
		}
		// not a reply packet or at least not for this host:
		try {
			vermittlung.weiterleitenPaket(icmpPaket);
		}
		catch (VerbindungsException e) {
			e.printStackTrace(Main.debug);
		}
	}
	
	// method to actually send a ping and compute the pong event
	// return true if successful
	public int startSinglePing(String destIp, int seqNr) throws java.util.concurrent.TimeoutException {
		Main.debug.println("INVOKED ("+this.hashCode()+", T"+this.getId()+") "+getClass()+" (ICMPThread), startSinglePing("+destIp+","+seqNr+")");
		int resultTTL=-20;   // return ttl field of received echo reply packet
		
		IcmpPaket sentIcmpPaket = vermittlung.sendEchoRequest(destIp, seqNr);
		synchronized(rcvdPackets) {
			try {
				rcvdPackets.wait(Verbindung.holeRTT());
			}
			catch (InterruptedException e) {
			}
			if(rcvdPackets.size()==0) {
				Main.debug.println("DEBUG ("+this.hashCode()+", T"+this.getId()+") "+getClass()+" (ICMPThread), startSinglePing, NO reply in queue");
				throw new java.util.concurrent.TimeoutException("Destination Host Unreachable");  // not absolutely correct, but who cares...
			}
			else {
				Main.debug.println("DEBUG ("+this.hashCode()+", T"+this.getId()+") "+getClass()+" (ICMPThread), startSinglePing, reply in queue");
				IcmpPaket rcvdIcmpPaket = rcvdPackets.removeFirst();
				if(rcvdIcmpPaket != null && !(sentIcmpPaket.getZielIp().equals(rcvdIcmpPaket.getQuellIp())
				  && sentIcmpPaket.getSeqNr() == rcvdIcmpPaket.getSeqNr())) {
					throw new java.util.concurrent.TimeoutException("Destination Host Unreachable");  // not absolutely correct, but who cares...
				}
				resultTTL = rcvdIcmpPaket.getTtl(); 
			}
		}
		Main.debug.println("INVOKED ("+this.hashCode()+", T"+this.getId()+") "+getClass()+" (ICMPThread), startSinglePing, return "+resultTTL);
		return resultTTL;
	}
	

}
