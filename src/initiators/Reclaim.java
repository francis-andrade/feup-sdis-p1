package initiators;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.MulticastSocket;
import java.util.Vector;

import peer.Message;
import peer.Peer;
import utils.Pair;

public class Reclaim implements Runnable{
	
	
	private Vector<Pair <String, Integer>> filesDeleted;
	private MulticastSocket mcSocket;
	
	public Reclaim(Vector<Pair <String, Integer>> filesDeleted) {
		this.filesDeleted = filesDeleted;
		try {

			mcSocket = new MulticastSocket();
		} catch (IOException e) {
			System.err.println("Error in Reclaims Constructor: "+e.toString());
			e.printStackTrace();
		}
	}
	
	@Override
	public void run() {
		for(int i=0; i < filesDeleted.size(); i++) {
			String fileID = filesDeleted.elementAt(i).getKey();
			Integer chunkNo = filesDeleted.elementAt(i).getValue();
				System.out.println("Will send Removed Message");
				byte[] message = Message.createRemovedHeader(Peer.getVersion(), ((Integer) Peer.getPeerID()).toString(), fileID, chunkNo);
				DatagramPacket packet = new DatagramPacket(message, message.length, Peer.getMCAddress(), Peer.getMCPort());
				try {
					mcSocket.send(packet);
				} catch (IOException e) {
					System.err.println("Error in Delete Constructor: "+e.toString());
					e.printStackTrace();
				}	
		
		}
	}
	
	

}
