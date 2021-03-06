package rmi;

import java.io.File;
import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RMIInterface extends Remote{
	void backup(File file, int repDegree) throws RemoteException;
	void restore(File filename) throws RemoteException;
	void delete(File filename) throws RemoteException;
	void reclaim(int space) throws RemoteException;
	void state() throws RemoteException;
}
