package depsolver;
import java.util.Collection;
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
	
//	int getSize = Integer.parseInt(size);
	
	String[][] depends;
	
	String[] conflicts;
	
	boolean done = false;
	
	boolean hasVersion;
	
//	Float versionInt;
	
	HashMap<Integer, List<Package>> dependantSet;
	
	HashSet<Package> conflictsSet;
	
//	SemanticVersion semVersion;
	
	public Package(String name, String version, String symbol, String size) {
		this.name = name;
		this.version = version;
		this.symbol = symbol;
		this.size = size;
		init();
		
	}
	
	public void init() {
//		semVersion = new SemanticVersion(this.version);
//		semVersion(this.version);
		dependantSet = new HashMap<>();
		conflictsSet = new HashSet<>();
		
		if(this.symbol == null) {
			this.symbol = "=";
		}
	}
	
	public int getSize() {
//		System.out.println(size);
		return Integer.valueOf(size);
	}
	
	public String getVersion() {
		return this.version;
	}
	
//	public SemanticVersion getVersion() {
//		
//		return this.semVersion;
//	}
	
	public void addDependants(Integer i, List<Package> dependant) {
		dependantSet.put(i, dependant);
	}
	
	public void addConflict(Package conflict) {
		conflictsSet.add(conflict);
	}
	
	public void run(StringBuilder result) {
		if(this.done == true) return;
//		System.out.println("Trying to install " + this.name + " With version " + this.semVersion);
//		If no dependencies and no conflicts, install module
		boolean hasConflict = true;
		boolean hasDepend = true;
		if(this.conflictsSet.size() < 1 && this.dependantSet.size() < 1) {
			System.out.println("installed: " + this.name);
			this.done = true;
			result.append("+").append(this.name).append(this.symbol).append(this.version).append("\n");
			return;
//		If has conflicts, check if conflicts are installed, if they are, uninstall
		} else if (!(this.conflictsSet.size() < 1) ) {
			Iterator<Package> itr = conflictsSet.iterator();
			while(itr.hasNext()) {
				Package nextVal = itr.next();
				if(nextVal.done != false) {
//					System.out.println("We found conflict with " + itr.next().name);
					nextVal.uninstall(result);
				}
			}
			hasConflict = false;
		}
//		We already checked for conflicts, now we check if there are dependents to install those
		if(!(this.dependantSet.size() < 1)) {
			for (int i = 0; i < dependantSet.size(); i++) {
				int biggerSize = Integer.MAX_VALUE;
				Package toRun = this;
				boolean hasDeps = true;
				for (int j = 0; j < dependantSet.get(i).size(); j++) {
//					If we already have a package that has no dependencies and we get another with no dependencies
//					I am checking to get the one with the smaller size to be installed
					if (!hasDeps && dependantSet.get(i).get(j).dependantSet.size() == 0) {
						if (biggerSize > dependantSet.get(i).get(j).getSize()) {
							biggerSize = dependantSet.get(i).get(j).getSize();
							toRun = dependantSet.get(i).get(j);
						}
					}
					else if (dependantSet.get(i).get(j).dependantSet.size() == 0) {
						hasDeps = false;
						biggerSize = dependantSet.get(i).get(j).getSize();
						toRun = dependantSet.get(i).get(j);
					} else if (biggerSize > dependantSet.get(i).get(j).getSize())  {
						biggerSize = dependantSet.get(i).get(j).getSize();
						toRun = dependantSet.get(i).get(j);
						hasDeps = true;
					}
				}
				toRun.run(result);
			}
//			System.out.println("Finished installing dependancies");
			hasDepend = false;
		} else {
//			System.out.println("No dependancies");
			hasDepend = false;
		}
		
		if (!hasDepend && !hasConflict) {
//			System.out.println("All conflicts and dependencies are installed");
		}
		this.done = true;
		result.append("+").append(this.name).append(this.symbol).append(this.version).append("\n");
		
	}
	
	
	public void uninstall(StringBuilder result) {
		this.done = false;
//		System.out.println("Unintsalling");
		result.append("-").append(this.name).append(this.symbol).append(this.version).append("\n");
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