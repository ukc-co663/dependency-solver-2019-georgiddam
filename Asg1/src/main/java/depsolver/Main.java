package depsolver;
import java.io.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class Main {

	private static final Comparator<Package> PackageVersionComparator = new Comparator<>() {
		
		@Override
		public int compare(Package arg0, Package arg1) {
			
			return arg1.version.compareTo(arg0.version);
		}
	};
	private static final Pattern dependancyConflictPattern = Pattern.compile("([A-Z])(?:([<>]?=?)(\\d+(?:\\.\\d+)*))?");
	private static final Pattern toInstallPattern = Pattern.compile("\\[\\s*\"([+-])([\\w-]+)([<>]?=?)?(\\d+(?:\\.\\d+)*)?\"\\s*\\]");
	public static void main(String[] args) throws IOException {
		String repo = readFile(args[0]);
		String initial = readFile(args[1]);
		String toInstall = readFile(args[2]);
		JsonElement jsonParsed = parseJson(repo, initial, toInstall);
		List<Package> allTasks = createPackages(jsonParsed);
		Map<String, SortedSet<Package>> tasksMap = new HashMap<>();
	
		for (Package p: allTasks) {
			String pName = p.name;
			
			SortedSet<Package> temp = tasksMap.get(pName);
			if(temp == null) {
				temp = new TreeSet<>(PackageVersionComparator);
				tasksMap.put(pName, temp);
			}
			temp.add(p);
			System.out.println(p);
//			Shorter version of the above 6 lines
//			tasksMap.computeIfAbsent(pName, _n -> new ArrayList<>()).add(p);			
			
		}
		System.out.println();
		
		startRun(tasksMap, toInstall);
	}
	
	  static String readFile(String filename) throws IOException {
		    BufferedReader br = new BufferedReader(new FileReader(filename));
		    StringBuilder sb = new StringBuilder();
		    br.lines().forEach(line -> sb.append(line));
		    return sb.toString();	
	  }
	
	private static JsonElement parseJson(String repo, String initial, String constraint) throws FileNotFoundException {
		JsonParser parser = new JsonParser();
		JsonElement rootElement = parser.parse(repo);
		
		return rootElement;
	}
//	Add dependencies and constraints
	private static void addDependAndConflict(String[] dependants, Package currentJson, List<Package> allTasks, boolean isDep, int pos) { 
		
		List<Package> combinedDependants = new ArrayList<>();
		String name;
		String version = "0.0";
		String symbol = "=";
//		create semantic version
//		SemanticVersion semVersion = new SemanticVersion(version);
		for (int i = 0; i < dependants.length; i++) {
			String depends = dependants[i];
			System.out.println();
			Matcher m = dependancyConflictPattern.matcher(depends);
			if(m.matches()) {
				name = m.group(1);
//				If there is a symbol, add it
				if (!(m.group(2) == null)) {
					symbol = m.group(2);
				}
//				If there is a version, make it semantic
				if (!(m.group(3) == null)) {
					version = (m.group(3));
				}
				
				
/*				
				Start looping through all tasks, check if the name of constraint/dependency 
				matches to that of inside the list.
				Once it does, look at its symbol and based on that, use the semVersion class
				to compare and based on results, add to the dependency list
*/
				for (int j = 0; j < allTasks.size(); j++) {
					Package tempTask = allTasks.get(j);
					if(tempTask.name.equals(name)) {
						int result = tempTask.version.compareTo(version);
						if(symbol.equals(">=")) {
							
							if(result == 0 || result == 1) {
								combinedDependants.add(tempTask);
							}
						}
						else if(symbol.equals("=")) {
							combinedDependants.add(tempTask);
						}
						else if(symbol.equals(">")) {
//							int result = tempTask.version.compareTo(version);
							if(result == 1) {
								combinedDependants.add(tempTask);
							}
						}
						else if(symbol.equals("<")) {
//							int result = tempTask.version.compareTo(version);
							if(result == -1) {
								combinedDependants.add(tempTask);
							}
						}
						else if(symbol.equals("<=")) {
//							int result = tempTask.version.compareTo(version);
							if(result == -1 || result == 0) {
								combinedDependants.add(tempTask);
							}
						} else {
							System.out.println("Should never get here, what is my symbol " + symbol);
						}
					}
				}
			}
			
//			If its a dependency
			if(isDep) {
				currentJson.addDependants(pos, combinedDependants);
//			Else add as a conflict
			} else {
				for (int j = 0; j < combinedDependants.size(); j++) {
					currentJson.addConflict(combinedDependants.get(j));
				}
			}
		
		}

	}
	
	private static List<Package> createPackages(JsonElement json) {
		List<Package> allTasks = new ArrayList<>();
		Gson gson = new Gson();
		Package[] finalPackage = gson.fromJson(json, Package[].class);

//		Add all packages to a list, including dependents
		for (Package e: finalPackage) {
			allTasks.add(e);
			e.init();
        }

//		Using finalPackage here because dependencies are still in there
		for (Package e: finalPackage) {
		
//			Dependents
			if (Arrays.deepToString(e.depends) != "null") {
	        	for (int i = 0; i < e.depends.length; i++) {
	        		String[] dependants = new String[e.depends[i].length];
					for (int j = 0; j < e.depends[i].length; j++) {	
						dependants[j] = e.depends[i][j];
					}	
					addDependAndConflict(dependants, e, allTasks, true, i);
	        	}
			}
			
//			Constraints
			if (Arrays.deepToString(e.conflicts) != "null") {
				for (int i = 0; i < e.conflicts.length; i++) {
					String[] conflicts = {e.conflicts[i]};
					addDependAndConflict(conflicts, e, allTasks, false, i);
				}
			}
			
        }    
				
		for (int i = 0; i < allTasks.size(); i++) {
//			Shows me the Linked Dependencies in the end
//			System.out.println(allTasks.get(i));
			
		}
//		
		return allTasks;
		
	}
//	Checks which packages to run and starts running them
	public static void startRun(Map<String, SortedSet<Package>> tasksMap, String toInstall) {
		
		Matcher m = toInstallPattern.matcher(toInstall);
		StringBuilder strBuilder = new StringBuilder();
		while(m.find()) {
//			System.out.println(m.group(1));
//			System.out.println(m.group(2));
			
			String tempInstall = m.group(1);
			String tempPackageName = m.group(2);
			String tempSymbol = m.group(3);
			String tempVersion = m.group(4);
			
			SortedSet<Package> tempPackage = tasksMap.get(tempPackageName);
			
			
//			We check if a version exists, if not, then just run the value with highest version
//			TODO I need to somehow check for all these available versions it might have, which ones to run
			if(tempVersion == null) {
				tempPackage.first().run(strBuilder);
			} else {
//				If it has a version, look at the comparator, run the first version which satisfies it
//				TODO again, create a list of all the possible values, check which one has more/less dependencies to run that one
				for (Package p : tempPackage) {
					
					int result = p.version.compareTo(tempVersion);
				
					if(tempSymbol == "=") {
					
						if(result == 0) {
							p.run(strBuilder);
							break;
						}
						
					} else if(tempSymbol == "<") {
						
						if(result == -1) {
							p.run(strBuilder);
							break;
						}
						
					} else if(tempSymbol == "<=") {
						if(result == -1 || result == 0) {
							p.run(strBuilder);
							break;
						}
						
					} else if(tempSymbol == ">") {
						if(result == 1) {
							p.run(strBuilder);
							break;
						}
						
					} else if(tempSymbol == ">=") {
						if(result == 1 || result == 0) {
							p.run(strBuilder);
							break;
						}
						
					} else {
						System.out.println("Should never reach here");
						break;
					}
				}
			}
			System.out.println(strBuilder);	
		};
//		Prints out my sets
//		for(Entry<String, SortedSet<Package>> e : tasksMap.entrySet()) {
//			System.out.println(e.getKey());
//			System.out.println(e.getValue().stream().map(Package::toString).collect(Collectors.joining(", ","[","]")));
//		}
		
	}
}