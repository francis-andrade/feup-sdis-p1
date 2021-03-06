package utils;

import java.io.File;
import java.util.ArrayList;
import java.util.Random;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;

import peer.ChunkStoreRecord;

public final class Utils {
	
	private Utils() {}
	
	public static File validFilePath(String filePath) {
		File file = new File(filePath);
		
		if(file.exists())
			return file;		
		else
			return null;
			
	}
	
	public static int validInt(String s_integer) {
			int integer = -1;
		try {
			integer = Integer.parseInt(s_integer);
		}catch(NumberFormatException e) {
			return -1;
		}

		return integer;
	}
	
	/**
	 * @brief Encodes a byte array to a String representation of their hexadecimal
	 *        representations.
	 * @param data
	 * @return
	 */
	public static String encodeByteArray(byte[] data) {
		StringBuilder sb = new StringBuilder();
		for (byte b : data) {
			sb.append(String.format("%02X", b));
		}
		return sb.toString();
	}
	
	public static int generateRandomInteger(int min, int max) {
		Random rand = new Random();
		int randomNum = rand.nextInt((max - min) + 1) + min;
		return randomNum;
	}
	
	public static String getFirstWord(String data) {
		String[] stringArray = data.split(" ");
		return stringArray[0];
	}
	
	public static String getSecondWord(String data) {
		String[] stringArray = data.split(" ");
		if (stringArray.length >=2)
			return stringArray[1];
		else
			return "";
	}
	
		
	/*public static void printHashMap(ConcurrentHashMap<String, ChunkStoreRecord> hash) {
		System.out.println("Print HashMap FileStores: ");
		for (String name: hash.keySet()){

           
            System.out.print(name);  
            System.out.println(": ");
            
            ConcurrentHashMap<Integer,ArrayList<Integer>> hash2 = hash.get(name).peers;
            
            
            
            for(int name2: hash2.keySet()) {
            	ArrayList<Integer> arr = hash2.get(name2);
            	System.out.print(name2);System.out.print(" { ");
            	for(int i : arr) {
            		System.out.print(i); System.out.print(" ");
            	}
            	System.out.println("}");
            }
            
            System.out.print(hash.get(name).getReplicationDeg());System.out.print(" ");
            System.out.print(hash.get(name).getPeerInit());
           
         }
		
		System.out.println("Finished printing");
		
	}
	
	public static void printChunksInPeer(ConcurrentHashMap<String, ArrayList<Integer> > hash) {
		System.out.println("Print HashMap: ");
		for (String name: hash.keySet()){                  
           
            	ArrayList<Integer> arr = hash.get(name);
            	System.out.print(name);System.out.print(" { ");
            	for(int i : arr) {
            		System.out.print(i); System.out.print(" ");
            	}
            	System.out.println("}");
            
           
         }
	}
	
	public static void printVectorOfPairs(Vector<Pair<String, Integer>> vec) {
		System.out.println("Print Vector of Pairs:");
		for(int i =0; i < vec.size(); i++) {
			System.out.print(vec.elementAt(i).getKey());System.out.println(" ");System.out.println(vec.elementAt(i).getValue());
		}
		
	}*/
	
	


}
