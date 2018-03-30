package channels;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import java.net.InetAddress;

import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;

import peer.Message;
import peer.Peer;
import utils.Utils;


public class ThreadMDR extends MulticastThread {

	public ThreadMDR(InetAddress address, int port) throws IOException {
		super(address, port);
	}
	
	@Override
	public void run() {
		while(true) {
			try {
				DatagramPacket packet = receivePacket(64512);
				String firstWord = getFirstWord(new String(packet.getData(), "UTF-8"));
				if (firstWord.equals("CHUNK")) {
					receive(packet);
				} else {
					throw new IOException("Invalid packet header!");
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}	
	
	public boolean receive(DatagramPacket packet) throws UnsupportedEncodingException, IOException, InterruptedException {
		String[] packetData = new String(packet.getData(), "UTF-8").split(Message.endHeader);
		byte[] chunk = packetData[1].getBytes();
		String[] header = packetData[0].split(" ");
		packetData = null;
		if (header[2].equals(Integer.toString(Peer.getInstance().getPeerID()))) // avoids storing chunks
			return false;
		
		int currentID = Peer.getInstance().getPeerID();
		int chunkNo = Integer.parseInt(header[4]);
		int replicationDeg = Integer.parseInt(header[5]);
		
//		if (Peer.getInstance().getFileStores().contains(header[3]) &&
//			Peer.getInstance().getFileStores().get(header[3]).peers.containsKey(chunkNo) &&
//			Peer.getInstance().getFileStores().get(header[3]).peers.get(chunkNo).contains(currentID)) {
//			return true;
//		}
				
		String filename = header[2] + "-" + header[3] + "." + header[4] + ".chunk";
		FileOutputStream out = new FileOutputStream(filename);
		out.write(chunk);
		out.close();
		
		// TODO resolver statics do Protocol.createStoredHeader
		return true;
	}

}