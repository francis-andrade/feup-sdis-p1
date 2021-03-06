package peer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import channels.ThreadMC;
import channels.ThreadMDB;
import channels.ThreadMDR;
import initiators.Backup;
import initiators.Delete;
import initiators.DeleteVersion2;
import initiators.Reclaim;
import initiators.Restore;

import rmi.RMIInterface;
import utils.Pair;
import utils.Utils;


public class Peer implements RMIInterface{

	protected static Peer instance;
	private static String version = null;
	private static int peerID;
	private static String accessPoint = null;
	private static ThreadMC MCThread;
	private static ThreadMDR MDRThread;
	private static ThreadMDB MDBThread;
	private static int mcPort;
	private static int mdrPort;
	private static int mdbPort;
	private static InetAddress mcAddress;
	private static InetAddress mdrAddress;
	private static InetAddress mdbAddress;
	private static FileHandler fileHandler;
	private static ConcurrentHashMap<String, ArrayList<Integer>> chunksInPeer = new ConcurrentHashMap<String, ArrayList<Integer> >();
	private static String chunksInPeerFilename = null;
	private static String fileStoresFilename = null;
	private static String peersToBeDeletedFilename = null;
	private static RestoreStatus currentRestore = null;
	private static int mdrPacketsReceived = 0;	
	private static ConcurrentHashMap<String, ChunkStoreRecord> fileStores = new ConcurrentHashMap<String, ChunkStoreRecord>();
	private static ConcurrentHashMap<String, ArrayList<Integer> > peersToBeDeleted = new ConcurrentHashMap<String, ArrayList<Integer>>();
	private static Vector<Pair<String, Integer> > putchunksReceived = new Vector<Pair<String, Integer> >();
	private static Vector<Pair<String, Integer> > reclaimedChunks = new Vector<Pair<String, Integer> >();
	private static DeleteVersion2 deleteVersion2;
	
	private static final int chunkSize = 64000;	
	private static final int maximumCapacity = 10000000;

	public static Peer getInstance() {
		if (instance == null) {
			instance = new Peer();
		}

		return instance;
	}
	
	private Peer() {
	};
	
	public static void main(String[] args) throws IOException {
		getInstance();		
		
		
		
		if(!validArgs(args))
			return;
		
		chunksInPeerFilename = ((Integer) peerID).toString()+"-"+PeerCommands.ChunksInPeerPathName;
		fileStoresFilename = ((Integer) peerID).toString()+"-"+PeerCommands.FileStoresPathName;
		peersToBeDeletedFilename = ((Integer) peerID).toString()+"-"+PeerCommands.PeersToBeDeletedPathName;
		
		if(initRMI(accessPoint) == false) 
			return;
		
		MCThread = new ThreadMC(mcAddress, mcPort);
		MDBThread = new ThreadMDB(mdbAddress, mdbPort);
		MDRThread = new ThreadMDR(mdrAddress, mdrPort);
		fileHandler = new FileHandler();
		deleteVersion2 = new DeleteVersion2();
		
		readChunksInPeer();
		readFileStores();
		readPeersToBeDeleted();
		launchThreads();
		
		
	}
	
	public static boolean validArgs(String[] args) throws UnknownHostException {
		boolean retValue = true;
		if(args.length != PeerCommands.PEER_NoArgs) 
			retValue = false;
		
		
		else if( (peerID=Utils.validInt(args[1])) <= 0) {
			System.out.println("<Peer_ID> must be an integer greater than 0");
			retValue = false;
		}
		else if((mcPort=Utils.validInt(args[4])) <= 0) {
			System.out.println("<MC_Port> must be an integer");
			retValue = false;
		}
		else if((mdrPort=Utils.validInt(args[6])) <= 0) {
			System.out.println("<MDR_Port> must be an integer");
			retValue = false;
		}
		else if((mdbPort=Utils.validInt(args[8])) <= 0) {
			System.out.println("<MDB_Port> must be an integer");
			retValue = false;
		}				
		else {
			version = args[0];
			accessPoint = args[2];
			mcAddress = InetAddress.getByName(args[3]);
			mdrAddress = InetAddress.getByName(args[5]);
			mdbAddress = InetAddress.getByName(args[7]);
		}
		
		
		if(retValue == false)
			PeerCommands.printUsage();
		
		
		return retValue;
		
	}
	
	public static boolean initRMI(String accessPoint) {
		try {
			Peer obj = new Peer();
			RMIInterface stub = (RMIInterface) UnicastRemoteObject.exportObject(obj, 0);
		
			Registry registry = LocateRegistry.getRegistry();
			registry.bind(accessPoint, stub);
			
			System.err.println("Peer Ready");
		} catch(Exception e) {
			System.err.println("Peer exception: "+e.toString());
			e.printStackTrace();
			return false;
		}
		
		return true;
	}
	
	/*These are the functions called by the RMI*/
	public void backup(File file, int repDegree) {
		
		try {
			new Thread(new Backup(file, repDegree)).start();
		}
		catch(Exception e) {
			System.err.println("Backup exception: "+e.toString());
			e.printStackTrace();
		}
		
	}
	
	public void restore(File file) {

		try {
			new Thread(new Restore(file)).start();
		}
		catch(Exception e) {
			System.err.println("Restore exception: "+e.toString());
			e.printStackTrace();
		}
	}
	
	public void delete(File file) {
		String fileID;
		try {
			fileID = Message.getFileData(file);
			Peer.deleteFile(fileID);
			
			if(version.equals("2")) {
				Peer.addPeersToBeDeleted(fileID);
				
			}
			
		} catch (NoSuchAlgorithmException  | IOException e) {
			System.err.println("Delete exception: "+e.toString());
			e.printStackTrace();
		} 
		
		new Thread(new Delete(file)).start();
	}
	
	public void reclaim(int space) {
		Vector<Pair<String, Integer> >filesDeleted = Peer.reclaimSpace(space);
		reclaimedChunks.addAll(filesDeleted);
		new Thread(new Reclaim(filesDeleted)).start();
		
	}
	
	public void state() {
		printState();
	}
	
	private static void launchThreads() {
		
		
		(new Thread(MCThread)).start();
		(new Thread(MDRThread)).start();
		(new Thread(MDBThread)).start();
		(new Thread(fileHandler)).start();
		if(Peer.getVersion().equals("2"))
			(new Thread(deleteVersion2)).start();
	}

	private static void closeThreads() throws IOException {

		MCThread.close();
		MDRThread.close();
		MDBThread.close();
		Peer.writeChunksInPeer();
		Peer.writeFileStores();
	}
	
	public static int getChunkSize() {
		return chunkSize;
	}
	
	public static Vector<Pair<String, Integer>> getReclaimedChunks() {
		return reclaimedChunks;
	}
	
	public static Vector<Pair<String, Integer> > getPutchunksReceived(){
		return putchunksReceived;
	}
	
	public static ConcurrentHashMap<String, ChunkStoreRecord> getFileStores() {
		return fileStores;
	}

	public static void setFileStores(ConcurrentHashMap<String, ChunkStoreRecord> hashmap) {
		fileStores = hashmap;
	}
	
	public static ConcurrentHashMap<String, ArrayList<Integer>> getChunksInPeer() {
		return chunksInPeer;
	}

		
	public static void createHashMapEntry(String fileID, int replicationDeg, int peerInit, String fileName) {
		if (!fileStores.containsKey(fileID)) {
			ChunkStoreRecord record = new ChunkStoreRecord(replicationDeg, peerInit, fileName);
			fileStores.put(fileID, record);
		}
	}
	
	public static int getMaximumCapacity() {
		return maximumCapacity;
	}
	
	/**
	 * @return the peerID
	 */
	public static int getPeerID() {
		return peerID;
	}

	/**
	 * @param peerID the peerID to set
	 */
	public static void setPeerID(int peerID) {
		Peer.peerID = peerID;
	}

	public static String getVersion() {
		return version;
	}

	public static void setVersion(String version) {
		Peer.version = version;
	}
	
	
	/**
	 * @return the mCThread
	 */
	/*public static ThreadMC getMCThread() {
		return MCThread;
	}*/

	/**
	 * @param mCThread the mCThread to set
	 */
	/*public static void setMCThread(ThreadMC mCThread) {
		MCThread = mCThread;
	}*/
	
	public static int getMCPort() {
		return mcPort;
	}
	
	public static int getMDBPort() {
		return mdbPort;
	}
	
	public static int getMDRPort() {
		return mdrPort;
	}
	
	public static InetAddress getMCAddress() {
		return mcAddress;
	}
	
	public static InetAddress getMDBAddress() {
		return mdbAddress;
	}
	
	public static InetAddress getMDRAddress() {
		return mdrAddress;
	}
	
	public static RestoreStatus getCurrentRestore() {
		return currentRestore;
	}

	public static void setCurrentRestore(RestoreStatus currentRestore) {
		Peer.currentRestore = currentRestore;
	}

	public static int getMdrPacketsReceived() {
		return mdrPacketsReceived;
	}

	public static void setMdrPacketsReceived(int mdrPacketsReceived) {
		Peer.mdrPacketsReceived = mdrPacketsReceived;
	}
	
	public static void incrementMdrPacketsReceived() {
		Peer.mdrPacketsReceived++;
	}
	
	public static void removeFileStoresPeer(String fileID, Integer chunkNo, Integer peerID) {
		if(fileStores.containsKey(fileID) && fileStores.get(fileID).peers.containsKey(chunkNo) && fileStores.get(fileID).peers.get(chunkNo).contains(peerID)) {
			fileStores.get(fileID).peers.get(chunkNo).remove((Object) peerID);	
		}
	}
	
	public static void removeFileStoresFile(String fileID, Integer senderID) {		
		if(fileStores.containsKey(fileID)) {
			ChunkStoreRecord record = fileStores.get(fileID);
			ConcurrentHashMap<Integer, ArrayList<Integer> > chunks = record.peers;
			Iterator<Entry<Integer, ArrayList<Integer>>> chunksIt = chunks.entrySet().iterator();
			while(chunksIt.hasNext()) {
				Map.Entry<Integer, ArrayList<Integer>> pair = (Entry<Integer, ArrayList<Integer>>) chunksIt.next();
				if(pair.getValue().contains(senderID))
					pair.getValue().remove((Object) senderID);
				
			}	
		}				
	}
	
	
	
	public static boolean peerStoredChunk(String fileID, Integer chunkNo, Integer peerID) {
		if (checkChunkPeers(fileID, chunkNo) <= 0) {
			return false;
		} else {
			ConcurrentHashMap<String, ChunkStoreRecord> hashmap = getFileStores();
			return hashmap.get(fileID).peers.get(chunkNo).contains(peerID);
		}
	}
	
	public static int checkChunkPeers(String fileID, Integer chunkNo) {
		ConcurrentHashMap<String, ChunkStoreRecord> hashmap = getFileStores();
		if (hashmap.containsKey(fileID)) {
			if (hashmap.get(fileID).peers.containsKey(chunkNo)) {
				return hashmap.get(fileID).peers.get(chunkNo).size();
			} else {
				return -2;	//file exists in hashmap, but not the chunk
			}
		} else {	//file does not exist in hashmap
			return -1;
		}
	}
	
	public static boolean addPeerToHashmap(String fileID, Integer chunkNo, Integer peerID) {
		int chunkStatus = checkChunkPeers(fileID, chunkNo);
		ConcurrentHashMap<String, ChunkStoreRecord> hashmap = getFileStores();
		ChunkStoreRecord record = new ChunkStoreRecord(-1, -1, "");
		ArrayList<Integer> peers = new ArrayList<Integer>();
		
		switch(chunkStatus) {
		case -1:	//new fileID
			break;
		case -2:	//new chunkNo
			record = hashmap.get(fileID);
			break;
		default:	//chunkNo exists
			record = hashmap.get(fileID);
			peers = record.peers.get(chunkNo);
			if(peers.contains(peerID))
				return false;
		}
		
		peers.add(peerID);	
		record.peers.put(chunkNo, peers);
		hashmap.put(fileID, record);
		Peer.setFileStores(hashmap);
		
		return true;
	}
	
	
	
	@SuppressWarnings("unchecked")
	public static void readChunksInPeer() {
		try {
		if((Utils.validFilePath(chunksInPeerFilename)) == null) {
			FileOutputStream out = new FileOutputStream(chunksInPeerFilename);
			ObjectOutputStream oos = new ObjectOutputStream(out);
			oos.writeObject(chunksInPeer);
			oos.close();
			
		}
		else {
			FileInputStream in = new FileInputStream(chunksInPeerFilename);
			ObjectInputStream ob = new ObjectInputStream(in);
			chunksInPeer = (ConcurrentHashMap<String, ArrayList<Integer> >) ob.readObject();
			ob.close();
		}
		//Utils.printChunksInPeer(chunksInPeer);
		}
		catch(Exception e) {
			System.err.println("Error reading chunksInPeer file: "+e.toString());
			e.printStackTrace();
		}
				
			
	}
	
	@SuppressWarnings("unchecked")
	public static void readFileStores() {
		try {
		if((Utils.validFilePath(fileStoresFilename)) == null) {
			FileOutputStream out = new FileOutputStream(fileStoresFilename);
			ObjectOutputStream oos = new ObjectOutputStream(out);
			oos.writeObject(fileStores);
			oos.close();
			
		}
		else {
			FileInputStream in = new FileInputStream(fileStoresFilename);
			ObjectInputStream ob = new ObjectInputStream(in);
			fileStores = (ConcurrentHashMap<String, ChunkStoreRecord>) ob.readObject();
			ob.close();
		}
		//Utils.printHashMap(fileStores);
		}
		catch(Exception e) {
			System.err.println("Error reading chunksInPeer file: "+e.toString());
			e.printStackTrace();
		}
				
			
	}
	
	public static void readPeersToBeDeleted() {
		try {
			if((Utils.validFilePath(peersToBeDeletedFilename)) == null) {
				FileOutputStream out = new FileOutputStream(peersToBeDeletedFilename);
				ObjectOutputStream oos = new ObjectOutputStream(out);
				oos.writeObject(peersToBeDeleted);
				oos.close();
				
			}
			else {
				FileInputStream in = new FileInputStream(peersToBeDeletedFilename);
				ObjectInputStream ob = new ObjectInputStream(in);
				peersToBeDeleted = (ConcurrentHashMap<String, ArrayList<Integer>>) ob.readObject();
				ob.close();
			}
			//Utils.printHashMap(fileStores);
			}
			catch(Exception e) {
				System.err.println("Error reading peersToBeDeleted file: "+e.toString());
				e.printStackTrace();
			}
	}
	
	public static void writeChunksInPeer() {
		FileOutputStream fos;
		try {
			fos = new FileOutputStream(chunksInPeerFilename);
			ObjectOutputStream oos = new ObjectOutputStream(fos);
			oos.writeObject(chunksInPeer);
			oos.close();
		} catch (FileNotFoundException e) {
			System.err.println("Error writing chunksInPeer file: "+e.toString());
			e.printStackTrace();
		}
		catch (IOException e) {
			System.err.println("Error writing chunksInPeer file: "+e.toString());
			e.printStackTrace();
		}
	
	}
	
	
	public static void writeFileStores() {
		FileOutputStream fos;
		try {
			fos = new FileOutputStream(fileStoresFilename);
			ObjectOutputStream oos = new ObjectOutputStream(fos);
			oos.writeObject(fileStores);
			oos.close();
		} catch (FileNotFoundException e) {
			System.err.println("Error writing chunksInPeer file: "+e.toString());
			e.printStackTrace();
		}
		catch (IOException e) {
			System.err.println("Error writing chunksInPeer file: "+e.toString());
			e.printStackTrace();
		}
	
	}
	
	public static void writePeersToBeDeleted() {
		FileOutputStream fos;
		try {
			fos = new FileOutputStream(peersToBeDeletedFilename);
			ObjectOutputStream oos = new ObjectOutputStream(fos);
			oos.writeObject(peersToBeDeleted);
			oos.close();
		} catch (FileNotFoundException e) {
			System.err.println("Error writing peersToBeDeleted file: "+e.toString());
			e.printStackTrace();
		}
		catch (IOException e) {
			System.err.println("Error writing peersToBeDeleted file: "+e.toString());
			e.printStackTrace();
		}
	
	}
	
	public static void addToChunksInPeer(String fileID,  int chunk) {
		if(chunksInPeer.containsKey(fileID)) {
			ArrayList<Integer> chunks = chunksInPeer.get(fileID);
			if(!chunks.contains(chunk))
				chunks.add(chunk);
			
			chunksInPeer.put(fileID, chunks);
		}
		else {
			ArrayList<Integer> chunks = new ArrayList<Integer>();
			chunks.add(chunk);
			
			chunksInPeer.put(fileID, chunks);
		}
		
	}
	
	public static boolean deleteFile(String fileID) {
		Peer.removeFileStoresFile(fileID, Peer.getPeerID());
		if(chunksInPeer.containsKey(fileID)) {
			ArrayList<Integer> chunks = chunksInPeer.get(fileID);
			Iterator<Integer> itr =chunks.iterator();
			while(itr.hasNext()) {
				Integer i = itr.next();
				Peer.deleteChunk(fileID, i);
				itr.remove();
			}
			if(chunks.isEmpty())
				chunksInPeer.remove(fileID);
			
			Peer.writeChunksInPeer();
			return true;
		}
		else
			return false;
		
	
	}
	
	public static boolean deleteChunk(String fileID, int chunkNo) {
		File file = new File(((Integer) peerID).toString()+"-"+fileID+"."+((Integer) chunkNo).toString()+".chunk");
		
		if(file.delete()) {
			System.out.println("Deleted file "+fileID);
			return true;
		}
		else {
			System.out.println("Failed to delete file "+fileID);
			return false;
		}
	}
	
	public static int calculateUsedSpace() {
		int spaceCount = 0;
		Iterator<Entry<String, ArrayList<Integer>>> chunksInPeerIt = chunksInPeer.entrySet().iterator();
		while(chunksInPeerIt.hasNext()) {
			Map.Entry<String, ArrayList<Integer>> pair = (Entry<String, ArrayList<Integer>>) chunksInPeerIt.next();
			String fileID = pair.getKey();
			for(Integer chunkNo : pair.getValue()) {
				spaceCount = spaceCount + getChunkSpace(fileID, chunkNo);
			} 
		}
		
		
		return spaceCount;
	}
	
	public static Vector<Pair<String, Integer>> reclaimSpace(int space) {
		
		Vector<Pair<String, Integer>> eliminatedFiles = new Vector<Pair<String, Integer>>();
		
		int spaceToBeDeleted = calculateUsedSpace() - space;
		int spaceAlreadyDeleted = 0;
		
		
		Iterator<Entry<String, ArrayList<Integer>>> chunksInPeerIt = chunksInPeer.entrySet().iterator();
		
		while(chunksInPeerIt.hasNext() && spaceToBeDeleted > spaceAlreadyDeleted) {
			Map.Entry<String, ArrayList<Integer>> pair = (Entry<String, ArrayList<Integer>>) chunksInPeerIt.next();
			String fileID = (String) pair.getKey();
			ArrayList<Integer> chunksSaved = (ArrayList<Integer>) pair.getValue();
			ChunkStoreRecord record = fileStores.get(fileID);
			Iterator<Integer> chunksSavedIt = chunksSaved.iterator(); 
			
		
			
			while(chunksSavedIt.hasNext() && spaceToBeDeleted > spaceAlreadyDeleted) {
				Integer chunkNo = (Integer) chunksSavedIt.next();
				ArrayList <Integer> peersSavedChunk = record.getPeers().get(chunkNo);
				if(peersSavedChunk.size() > record.getReplicationDeg()) {
					eliminatedFiles.add(new Pair<String, Integer>(fileID, chunkNo));
					spaceAlreadyDeleted += getChunkSpace(fileID, chunkNo);
					deleteChunk(fileID, chunkNo);
					chunksSavedIt.remove();										
					peersSavedChunk.remove((Object) Peer.getPeerID());	
				}
				
				
			}
					
			if(chunksSaved.size() == 0)
				chunksInPeer.remove(fileID);
			
		}
		
		chunksInPeerIt = chunksInPeer.entrySet().iterator();
		
		while(chunksInPeerIt.hasNext() && spaceToBeDeleted > spaceAlreadyDeleted) {
			Map.Entry<String, ArrayList<Integer>> pair = (Entry<String, ArrayList<Integer>>) chunksInPeerIt.next();
			String fileID = (String) pair.getKey();
			ArrayList<Integer> chunksSaved = (ArrayList<Integer>) pair.getValue();
			ChunkStoreRecord record = fileStores.get(fileID);
			Iterator<Integer> chunksSavedIt = chunksSaved.iterator(); 
			
		
			
			while(chunksSavedIt.hasNext() && spaceToBeDeleted > spaceAlreadyDeleted) {
				Integer chunkNo = (Integer) chunksSavedIt.next();
				ArrayList <Integer> peersSavedChunk = record.getPeers().get(chunkNo);
				eliminatedFiles.add(new Pair<String, Integer>(fileID, chunkNo));
				spaceAlreadyDeleted += getChunkSpace(fileID, chunkNo);
				deleteChunk(fileID, chunkNo);
				chunksSavedIt.remove();				
				peersSavedChunk.remove((Object) Peer.getPeerID());
				
				
			}
			
			if(chunksSaved.size() == 0)
				chunksInPeer.remove(fileID);
			
		}
		
		return eliminatedFiles;
	}
	
	public static Integer getChunkPeerInit(String fileID) {
		if(fileStores.containsKey(fileID))
			return fileStores.get(fileID).getPeerInit();
		else
			return -1;
	}
	
	public static Integer getPerceivedReplicationDegree(String fileID, Integer chunkNo) {
		ChunkStoreRecord record = fileStores.get(fileID);
		ArrayList <Integer> peersSavedChunk = record.getPeers().get(chunkNo);
		return peersSavedChunk.size();
	}
	
	public static Integer getChunkSpace(String fileID, Integer chunkNo) {
		File file = new File(((Integer )peerID).toString()+"-"+fileID+"."+chunkNo.toString()+".chunk");
		return (int) file.length();
	}
	
	public static void addPeersToBeDeleted(String fileID){
		ArrayList<Integer> arr = new ArrayList<Integer>();
		if(fileStores.containsKey(fileID)) {
		ConcurrentHashMap<Integer, ArrayList<Integer> > chunks = fileStores.get(fileID).peers;
		Iterator<Entry<Integer, ArrayList<Integer>>> chunksIt = chunks.entrySet().iterator();
		
		while(chunksIt.hasNext()) {
			Map.Entry<Integer, ArrayList<Integer>> pair= (Entry<Integer, ArrayList<Integer>>) chunksIt.next();
			arr.addAll(pair.getValue());
			arr = (ArrayList<Integer>) arr.stream().distinct().collect(Collectors.toList());
		}
		
		}
		
		if(peersToBeDeleted.containsKey(fileID)) {
			ArrayList<Integer> finalPeers =  peersToBeDeleted.get(fileID);
			finalPeers.addAll(arr);
			finalPeers = (ArrayList<Integer>) finalPeers.stream().distinct().collect(Collectors.toList());
			peersToBeDeleted.put(fileID, finalPeers);
		}
		
		peersToBeDeleted.put(fileID, arr);
		
		
		
	}
	
	public static void removePeersToBeDeleted(String fileID, Integer peerID) {
		if(peersToBeDeleted.containsKey(fileID)) {
			ArrayList<Integer> arr = peersToBeDeleted.get(fileID);
			if(arr.contains(peerID)) 
				arr.remove((Object) peerID);
			peersToBeDeleted.put(fileID, arr);
			
		}
	}
	
	public static void printStateInit() {
		System.out.println("Files Initiated by this Peer:");
		System.out.println("");
		Iterator<Entry<String, ChunkStoreRecord>> fileStoresIt = fileStores.entrySet().iterator();
		while(fileStoresIt.hasNext()) {
			Map.Entry<String, ChunkStoreRecord> pair = (Entry<String, ChunkStoreRecord>) fileStoresIt.next();
			ChunkStoreRecord record = pair.getValue();
			Integer peerInit = record.getPeerInit();
			if(peerInit == peerID) {
				String fileID = (String) pair.getKey();
				String fileName = record.getFileName();
				System.out.println("FilePathname: "+fileName);
				System.out.println("fileID: "+fileID);
				System.out.println("Desired Replication Degree: "+((Integer)record.getReplicationDeg()).toString());
				ConcurrentHashMap<Integer, ArrayList<Integer> > chunks = record.peers;
				Iterator<Entry<Integer, ArrayList<Integer>>> chunksIt = chunks.entrySet().iterator();
				System.out.println("chunks:");
				while(chunksIt.hasNext()) {
					Map.Entry<Integer, ArrayList<Integer>> pair1 = (Entry<Integer, ArrayList<Integer>>) chunksIt.next();
					System.out.print("	chunkNo: "+pair1.getKey().toString());
					System.out.println("	Perceived Replication Degree: "+((Integer) pair1.getValue().size()).toString()); 
				}
			}
			
			
		}
	}
	
	public static void printStateStored() {
		System.out.println("");
		System.out.println("Files Stored by this Peer:");
		Iterator<Entry<String, ArrayList<Integer>>> chunksInPeerIt = chunksInPeer.entrySet().iterator();
		while(chunksInPeerIt.hasNext()) {
			Map.Entry<String, ArrayList<Integer>> pair = (Entry<String, ArrayList<Integer>>) chunksInPeerIt.next();
			String fileID = (String) pair.getKey();
			ArrayList<Integer> chunks = pair.getValue();
			
			System.out.println("File ID: "+fileID);
			System.out.println("Chunks Stored by this Peer");
			
			for(Integer chunkNo : chunks) {
				System.out.print("	Chunk Number :"+chunkNo.toString());
				System.out.print("	Chunk Size :"+getChunkSpace(fileID, chunkNo));
				System.out.println("	Perceived Replication Degree :"+Peer.getPerceivedReplicationDegree(fileID, chunkNo));
			}
			
			
			
		}
	}
	
	public static void printPeerStorage() {
		System.out.println("");
		System.out.println("Peer Capacity");
		System.out.println("Storage Capacity: "+maximumCapacity);
		System.out.println("Occupied Memory (bytes): "+calculateUsedSpace());
		
	}
	
	public static void printState() {
		printStateInit();
		printStateStored();
		printPeerStorage();
	}

	public static ConcurrentHashMap<String, ArrayList<Integer> > getPeersToBeDeleted() {
		return peersToBeDeleted;
	}

	

	
	
	
	
}