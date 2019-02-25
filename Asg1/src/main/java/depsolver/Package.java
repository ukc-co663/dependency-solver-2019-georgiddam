package depsolver;
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
	
	boolean converted = false;
	
//	StringBuilder solver = new StringBuilder();
	
	String[][] depends;
	
	String[] conflicts;
	
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
//		solver.append("~").append(conflict.name+conflict.version);
	}
	
	public String addToBooleanString(String convertToBool) {		
		for (int i = 0; i < this.dependantSet.size(); i++) { 
			convertToBool += (" & ");
				convertToBool += ("(");
//			}
			int size = this.dependantSet.get(i).size();
			for (int j = 0; j < this.dependantSet.get(i).size(); j++) {
				convertToBool += ("(");
				
				convertToBool += (dependantSet.get(i).get(j).name+dependantSet.get(i).get(j).version);
//				dependantSet.get(i).get(j).addToBooleanString(convertToBool);
				
				if(j<size-1) {
					convertToBool += " | ";
				}
						
			}
			if(this.dependantSet.get(i).size() > 1) {
				convertToBool += (")");
			}
		}
		
		Iterator<Package> itr = conflictsSet.iterator();
		while(itr.hasNext()) {
			Package nextVal = itr.next();
			 convertToBool += (" & ~");
			 convertToBool += (nextVal.name+nextVal.version);
//			 convertToBool = nextVal.addToBooleanString(convertToBool);
			 
		}
		convertToBool += (")");
		System.out.println("Converted bool " + convertToBool);
		return convertToBool;
	}
	
	
//	public boolean checkDependencies(StringBuilder result) {
//		for (int i = 0; i < dependantSet.size(); i++) {
//			int biggerSize = Integer.MAX_VALUE;
//			Package toRun = this;
//			boolean hasPriority = false;
//			for (int j = 0; j < dependantSet.get(i).size(); j++) {
////				If some package is already chosen
//				if (!hasPriority && dependantSet.get(i).get(j).dependantSet.size() == 0) {
//					if(toRun.conflictsSet.size() > dependantSet.get(i).get(j).conflictsSet.size()) {
//						biggerSize = dependantSet.get(i).get(j).getSize();
//						toRun = dependantSet.get(i).get(j);
//					}
//					if (biggerSize > dependantSet.get(i).get(j).getSize()) {
//						biggerSize = dependantSet.get(i).get(j).getSize();
//						toRun = dependantSet.get(i).get(j);
//					}
//				}
////				Base cases
////				If it has no dependencies, use that
//				else if (dependantSet.get(i).get(j).dependantSet.size() == 0) {
//					hasPriority = true;
//					biggerSize = dependantSet.get(i).get(j).getSize();
//					toRun = dependantSet.get(i).get(j);
//					
////					If it has no conflicts
//				} else if (dependantSet.get(i).get(j).conflictsSet.size() == 0){
//					hasPriority = true;
//					biggerSize = dependantSet.get(i).get(j).getSize();
//					toRun = dependantSet.get(i).get(j);
////					If it has lowest size
//				} else if (biggerSize > dependantSet.get(i).get(j).getSize())  {
//					biggerSize = dependantSet.get(i).get(j).getSize();
//					toRun = dependantSet.get(i).get(j);
//					hasPriority = false;
//				}
//			}
//			toRun.run(result);
//		}
//		return false;
//	}
	
//	public boolean checkConflicts(StringBuilder result) {
//		Iterator<Package> itr = conflictsSet.iterator();
//		boolean toBreak = false;
//		while(itr.hasNext()) {
//			Package nextVal = itr.next();
//			if(nextVal.done != false) {
//				if(!nextVal.isRequired) {
//					nextVal.uninstall(result);
//					toBreak = false;
//				} else {
//					toBreak = true;
//				}
//			} else {
//				toBreak = false;;
//			}
//			if (toBreak) {
////				Exit the loop, return false
//				break;
//			}
//		}
//		return toBreak;
//	}
	
//	public boolean run(StringBuilder result) {
//		if(this.done == true) return true;
////		If no dependencies and no conflicts, install module
//		boolean hasConflict = true;
//		boolean hasDepend = true;
////		if no conflicts and no dependencies
//		if(this.conflictsSet.size() < 1 && this.dependantSet.size() < 1) {
//			this.isRequired = true;
//			this.done = true;
//			result.append("+").append(this.name).append(this.symbol).append(this.version).append("\n");
//			return true;
//		}
////		If has conflicts, check if conflicts are installed, if they are, uninstall
//		if (this.conflictsSet.size() > 0) {
//			hasConflict = checkConflicts(result);
//		} else {
//			hasConflict = false;
//		}
////		We already checked for conflicts, now we check if there are dependents to install those
//		if(this.dependantSet.size() > 0) {
//			hasDepend = checkDependencies(result);
//		} else {
//			hasDepend = false;
//		}
//		
//		if (!hasDepend && !hasConflict) {
//			this.isRequired = true;
//			this.done = true;
//			result.append("+").append(this.name).append(this.symbol).append(this.version).append("\n");
//			return true;
//		} else {
//			System.out.println("Broken");
//			return false;
//		}
//	}
	
	
//	public boolean uninstall(StringBuilder result) {
//		if(isRequired) {
//			System.out.println(this.name + " Is Required somewhere else");
//			return false;
//		}
//		this.done = false;
//		result.append("-").append(this.name).append(this.symbol).append(this.version).append("\n");
//		return true;
//	}
	
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