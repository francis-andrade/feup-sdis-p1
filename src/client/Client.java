package client;

import java.io.File;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;


import rmi.RMIInterface;
import utils.Utils;

public class Client {
	private static File file;
	private static int repDegree = -1;
	private static RMIInterface rmiStub; 
	private static int space;

	public static void main(String[] args) throws RemoteException {
		showMainMenu();
		if(!validArgs(args))
			return;
		
		if(initRMI(args[0]) == false) {
			ClientCommands.printUsage();
			return;
		}
		
		System.out.println("The client application was called with success");
		if(args[1].equals(ClientCommands.BACKUP)) {
			rmiStub.backup(file, repDegree);
		}
		else if(args[1].equals(ClientCommands.RESTORE))
			rmiStub.restore(file);
		else if(args[1].equals(ClientCommands.DELETE))
			rmiStub.delete(file);
		else if(args[1].equals(ClientCommands.RECLAIM))
			rmiStub.reclaim(space);
		else if(args[1].equals(ClientCommands.STATE))
			rmiStub.state();
		
		
	}

	private static void showMainMenu() {
		System.out.println("SDIS1718-T2G02 TESTING CLIENT");
		System.out.println("  - ");
	}
	
	private static boolean validArgs(String[] args) {
		if(args.length < 2) 
			ClientCommands.printUsage();
		
		
		boolean retValue = true;
		
		
			
		
		if(args[1].equals(ClientCommands.BACKUP)) {
			
			
			if(args.length != ClientCommands.BACKUP_NoArgs) { 
				retValue = false;		
			}
			else if((file=Utils.validFilePath(args[2])) == null) {
				System.out.println("The filePath is not valid");
				retValue = false;
			}						
			else if((repDegree=Utils.validInt(args[3])) <= 0) {
				System.out.println("The replication degree must be an integer");
				retValue = false;			
			}
			else if(repDegree < 1 || repDegree > 9) {
				System.out.println("The replication degree parameter must be a value between 1 and 9");
				retValue = false;
			}
			
			if(retValue == false)
				System.out.println(ClientCommands.BACKUP_Usage);
			
			return retValue;
		}
		
		else if(args[1].equals(ClientCommands.RESTORE)) {
			
			if(args.length != ClientCommands.RESTORE_NoArgs)
				retValue = false;
			else if((file=Utils.validFilePath(args[2])) == null) {
				System.out.println("The filePath is not valid");
				retValue = false;
			}	
			
			
			if(retValue == false)
				System.out.println(ClientCommands.RESTORE_Usage);
			
			return retValue;
		}
		
		else if(args[1].equals(ClientCommands.DELETE)) {
			
			if(args.length != ClientCommands.DELETE_NoArgs)
				retValue = false;
			else if((file=Utils.validFilePath(args[2])) == null) {
				System.out.println("The filePath is not valid");
				retValue = false;
			}	
			
			if(retValue == false)
				System.out.println(ClientCommands.DELETE_Usage);
			
			return retValue;			
		}
		
		else if(args[1].equals(ClientCommands.RECLAIM)) {
			if(args.length != ClientCommands.RECLAIM_NoArgs)
				retValue = false;
			else if((space=Utils.validInt(args[2])) < 0)
				retValue = false;
			
			if(retValue == false)
				System.out.println(ClientCommands.RECLAIM_Usage);
			
			return retValue;
		}
		
		else if(args[1].equals(ClientCommands.STATE)) {
			if(args.length != ClientCommands.STATE_NoArgs) {
				System.out.println(ClientCommands.STATE_Usage);
				return false;
			}
			
			return true;
			
		}
		
		else {
			ClientCommands.printUsage();
			return false;
		}
		
		
	}
	
	private static boolean initRMI(String accessPoint) {
		try {
			String host = null;
			Registry registry = LocateRegistry.getRegistry(host);
			rmiStub = (RMIInterface) registry.lookup(accessPoint);
			
		}catch(Exception e) {
			System.err.println("RMI exception: "+e.toString());			
			e.printStackTrace();
			
			return false;
		}
		
		return true;
	}
}
