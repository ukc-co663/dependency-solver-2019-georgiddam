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
	int size;
	
	
	
	String[][] depends;
	
	String[] conflicts;
	
	boolean done = false;
	
	boolean hasVersion;
	
//	Float versionInt;
	
	HashMap<Integer, List<Package>> dependantSet;
	
	HashSet<Package> conflictsSet;
	
	SemanticVersion semVersion;
	
	public Package(String name, String version, String symbol) {
		this.name = name;
		this.version = version;
		this.symbol = symbol;
		init();
		
	}
	
	public void init() {
		semVersion = new SemanticVersion(this.version);
//		semVersion(this.version);
		dependantSet = new HashMap<>();
		conflictsSet = new HashSet<>();
		if(this.symbol == null) {
			this.symbol = "=";
		}
	}
	
	public SemanticVersion getVersion() {
		
		return this.semVersion;
	}
	
	public void addDependants(Integer i, List<Package> dependant) {
		dependantSet.put(i, dependant);
	}
	
	public void addConflict(Package conflict) {
		conflictsSet.add(conflict);
	}
	
	public void run(StringBuilder result) {
//		TODO This doesn't check at all at symbols, so I have no clue how it will actually work

		System.out.println("Trying to install " + this.name + " With version " + this.semVersion);
//		If no dependencies and no conflicts, install module
		boolean hasConflict = true;
		boolean hasDepend = true;
		if(this.conflictsSet.size() < 1 && this.dependantSet.size() < 1) {
			this.done = true;
			result.append(this.name).append(" ");
			return;
//		If has conflicts, check if conflicts are installed, if they are, uninstall
		} else if (!(this.conflictsSet.size() < 1) ) {
			Iterator<Package> itr = conflictsSet.iterator();
			while(itr.hasNext()) {
				if(itr.next().done != false) {
					System.out.println("We found conflict with " + itr.next().name);
					itr.next().uninstall();
				}
			}
			hasConflict = false;
		}
//		We already checked for conflicts, now we check if there are dependents to install those
		if(!(this.dependantSet.size() < 1)) {
			for (int i = 0; i < dependantSet.size(); i++) {
				for (int j = 0; j < dependantSet.get(i).size(); j++) {
					System.out.println("Running package on dependancy " + dependantSet.get(i).get(j).name);
					dependantSet.get(i).get(j).run(result);
				}
				
			}
			System.out.println("Finished installing dependancies");
			hasDepend = false;
		} else {
			System.out.println("No dependancies");
			hasDepend = false;
		}
		
		if (!hasDepend && !hasConflict) {
			System.out.println("All conflicts and dependencies are installed");
			this.done = true;
			
		}
		
		result.append(this.name).append(" ");
		
	}
	
	
	public void uninstall() {
		this.done = false;
	}
	
//	public boolean canRun() {
//		for (Package Package : depends) {
//			if (!(Package.done)) {
//				return false;
//			}
//		}
//		return true;
//	}
	
	
	@Override
	public String toString() {
		return "Link Dependancies [name = " + name + ", version= " + this.semVersion + ", symbol = '" + symbol 
			+ "' , dependants = [" + dependantSet.values().stream().map(l-> {
			return l.stream().map(t -> (t.name + " = " + t.version)).collect(Collectors.joining(", ","[","]"));
		}).collect(Collectors.joining(", ")) + "], "
			+ "conflicts = [" + conflictsSet.stream().map(t->(t.name + " = " + t.semVersion)).collect(Collectors.joining(", ")) 
			+ "]  done = " + done + "]";
	}
	
}