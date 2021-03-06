package channels;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

public abstract class MulticastThread implements Runnable {
	
	protected int port;
	protected InetAddress address;
	protected MulticastSocket socket;
	
	MulticastThread(InetAddress address, int port) throws IOException{
		this.address = address;
		this.port = port;
		this.socket = new MulticastSocket(this.port);
		this.socket.setTimeToLive(1);
		this.socket.joinGroup(this.address);
	}
	
	public void close() throws IOException {
		this.socket.leaveGroup(this.address);
		this.socket.close();
	}
	
	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public InetAddress getAddress() {
		return address;
	}

	public void setAddress(InetAddress address) {
		this.address = address;
	}

	public MulticastSocket getSocket() {
		return socket;
	}

	public void setSocket(MulticastSocket socket) {
		this.socket = socket;
	}

	@Override
	public void run() {
		System.out.println("MulticastThread run");
	}
	
	
	
	protected DatagramPacket receivePacket(int bufferSize) throws IOException {
		byte[] rbuf = new byte[bufferSize];
		DatagramPacket packet = new DatagramPacket(rbuf, rbuf.length);
		//System.out.println("packet received before");
		socket.receive(packet);
		//System.out.println("packet received after");
		return packet;
	}

}
