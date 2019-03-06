package depsolver;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

class Package {
	
	String symbol;
	String name;
	String version;
	String size;
	
	String dotlessVersion;
	
	String[][] depends;
	
	String[] conflicts;
	boolean visited = false;
	boolean done = false;
	
	boolean hasVersion;
	boolean isRequired = false;

	HashMap<Integer, List<Package>> dependantSet;
	
	HashSet<Package> conflictsSet;
	
	public Package(String name, String version, String symbol, String size) {
		this.name = name;
		this.version = version;
		this.symbol = symbol;
		this.size = size;
		init();
	} 
	
	public void init() {
		dotlessVersion = "version999"+version.replaceAll("\\.","");
		dependantSet = new HashMap<>();
		conflictsSet = new HashSet<>();
		
		if(this.symbol == null) { 
			this.symbol = "=";
		}
	}
	
	public int getSize() {
		return Integer.valueOf(size);
	}
	
	public String getVersion() {
		return this.version;
	}
	
	public void addDependants(Integer i, List<Package> dependant) {
		dependantSet.put(i, dependant);
	}
	
	public void addConflict(Package conflict) {
		conflictsSet.add(conflict); 
	}
	
	public HashMap<String, List<Package>> addToBooleanString(String trackString) {
		
		boolean addedSingle = false;
		String convertToBool = "";
		List<Package>collectAllNext = new ArrayList<>();
		HashMap<String, List<Package>> toReturn = new HashMap<>(); 
		String thisPack = this.name+this.dotlessVersion;
		if (this.visited) {
			convertToBool += " & " +  thisPack + " TEST999*";
			toReturn.put(convertToBool,  collectAllNext);
			return toReturn;
		}
		this.visited = true;
		
		if(this.dependantSet.size() == 0) {
			convertToBool += this.name+this.dotlessVersion;
		}
	
//		Creating my dependency list
		for (int i = 0; i < this.dependantSet.size(); i++) { 

			int size = this.dependantSet.get(i).size();
			String[] storeAll = new String[size];
//			Check all deps, if there are OR's to manage them later
			
			for (int j = 0; j < this.dependantSet.get(i).size(); j++) {
				Package getPackage = dependantSet.get(i).get(j);
				collectAllNext.add(getPackage);
				storeAll[j] = getPackage.name+getPackage.dotlessVersion; 
			}
			
 
//			Single dep
			if(storeAll.length < 2) {	
				if(!addedSingle) {
					convertToBool += thisPack + " & ";
					addedSingle = true;
				}
				
				for (int k = 0; k < storeAll.length; k++) {
					convertToBool += (dependantSet.get(i).get(k).name+dependantSet.get(i).get(k).dotlessVersion);
				}
				
				if(i+1 < this.dependantSet.size()) {
					convertToBool += " & ";
				}
//			Multiple Deps
			} else {
				for (int k = 0; k < storeAll.length; k++) {
					int j = 0;
					convertToBool += trackString;
					int followNot = 0;
					for (; j < storeAll.length; j++) {		
						if (followNot != k) {
							convertToBool += " & ~";
							convertToBool += (dependantSet.get(i).get(j).name+dependantSet.get(i).get(j).dotlessVersion);
							followNot ++;
						} else {						
							convertToBool += " & ";
							convertToBool += (dependantSet.get(i).get(j).name+dependantSet.get(i).get(j).dotlessVersion);
							followNot ++;
						}
					}
					if(k<storeAll.length-1)
						convertToBool += " | ";
				}
				if(i+1 < this.dependantSet.size()) {
					convertToBool += " & ";
				}
			}
		} 
//		Conflicts 
		Iterator<Package> itr = conflictsSet.iterator();
		while(itr.hasNext()) {
			Package nextVal = itr.next();
			 convertToBool += (" & ~");
			 convertToBool += (nextVal.name+nextVal.dotlessVersion);  
		}
		toReturn.put(convertToBool,  collectAllNext);
		return toReturn; 
	}
	
	public int checkInstall(int addSize) {
		if(this.done == true) return addSize;
		return addSize += this.getSize();
	}
	
	public int checkUninstall(int addSize) {
		if(this.done == false) return addSize;
		return addSize += 1000000;
	}
	
	@Override
	public String toString() {
		return "Link Dependancies [name: " + name + ", version: " + this.version + ", symbol: '" + symbol 
			+ " Size: " + getSize() + "' , dependants: [" + dependantSet.values().stream().map(l-> {
			return l.stream().map(t -> (t.name + ": " + t.symbol + t.version )).collect(Collectors.joining(", ","[","]"));
		}).collect(Collectors.joining(", ")) + "], "
			+ "conflicts: [" + conflictsSet.stream().map(t->(t.name + ": " + t.symbol + t.version)).collect(Collectors.joining(", ")) 
			+ "]  done: " + done + "]";
	}
	
}